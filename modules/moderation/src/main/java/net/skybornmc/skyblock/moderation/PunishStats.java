package net.skybornmc.skyblock.moderation;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * {@code /pstats} (spec 12.10): how many punishments a staff member gave. Pure parts (periods, argument rules,
 * aggregation, cache) live here so they are unit-tested; the queries are in {@link PunishmentStore}.
 *
 * <p>Rules: only punishments with a staff issuer count (automatic "System (auto)" and console rows have no issuer uuid
 * and never count). An applied suggestion counts for the staff member who clicked [Apply] (that row's issuer); a
 * dismissed one creates no punishment and counts for nobody. Reversed = pardoned by anyone (including [Undo]);
 * net = given - reversed.</p>
 */
final class PunishStats {

    private PunishStats() {
    }

    /** Periods. 24h is rolling; 7d/30d are whole days in {@code stats.timezone} (today plus the days before). */
    enum Period {
        H24("24h"), D7("7d"), D30("30d"), ALL("all");

        final String id;

        Period(String id) {
            this.id = id;
        }

        static Optional<Period> parse(String raw) {
            if (raw == null) {
                return Optional.empty();
            }
            for (Period p : values()) {
                if (p.id.equalsIgnoreCase(raw.strip())) {
                    return Optional.of(p);
                }
            }
            return Optional.empty();
        }

        static List<String> ids() {
            List<String> out = new ArrayList<>();
            for (Period p : values()) {
                out.add(p.id);
            }
            return out;
        }

        /** First moment that counts ({@code 0} for all time). */
        long start(long now, ZoneId zone) {
            return switch (this) {
                case H24 -> now - Duration.ofHours(24).toMillis();
                case D7 -> dayStart(now, zone, 6);
                case D30 -> dayStart(now, zone, 29);
                case ALL -> 0L;
            };
        }

        private static long dayStart(long now, ZoneId zone, int daysBack) {
            LocalDate today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
            return today.minusDays(daysBack).atStartOfDay(zone).toInstant().toEpochMilli();
        }
    }

    /** What {@code /pstats [a] [b]} asks for. */
    enum Kind { OWN, OTHER, TOP }

    /** A parsed request, or {@code error} (a message key) when it is not allowed or not understood. */
    record Query(Kind kind, String staff, Period period, String error) {

        static Query error(String key) {
            return new Query(null, null, null, key);
        }

        boolean ok() {
            return error == null;
        }
    }

    /**
     * Argument rules. {@code /pstats} = own, default period; {@code /pstats 30d} = own; {@code /pstats top [period]} and
     * {@code /pstats <staff> [period]} need {@code others} (checked on every call), except your own name.
     */
    static Query parse(String first, String second, String selfName, boolean others, Period defaultPeriod) {
        Period period = defaultPeriod;
        if (second != null && !second.isBlank()) {
            Optional<Period> p = Period.parse(second);
            if (p.isEmpty()) {
                return Query.error("stats.bad-period");
            }
            period = p.get();
        }
        if (first == null || first.isBlank()) {
            return new Query(Kind.OWN, selfName, period, null);
        }
        Optional<Period> asPeriod = Period.parse(first);
        if (asPeriod.isPresent()) {
            if (second != null && !second.isBlank()) {
                return Query.error("stats.bad-period");
            }
            return new Query(Kind.OWN, selfName, asPeriod.get(), null);
        }
        if (first.equalsIgnoreCase("top")) {
            return others ? new Query(Kind.TOP, null, period, null) : Query.error("stats.no-permission-others");
        }
        if (selfName != null && first.equalsIgnoreCase(selfName)) {
            return new Query(Kind.OWN, selfName, period, null);
        }
        return others ? new Query(Kind.OTHER, first, period, null) : Query.error("stats.no-permission-others");
    }

    /** One grouped row from the database: punishments of one type by one staff member. */
    record Row(Punishment.Type type, long count, long reversed, long fromSuggestions) {
    }

    /** The numbers shown. */
    record Counts(long warns, long mutes, long bans, long kicks, long reversed, long fromSuggestions, long island) {

        static final Counts ZERO = new Counts(0, 0, 0, 0, 0, 0, 0);

        /** Without island punishments (before P7). */
        Counts(long warns, long mutes, long bans, long kicks, long reversed, long fromSuggestions) {
            this(warns, mutes, bans, kicks, reversed, fromSuggestions, 0);
        }

        static Counts of(List<Row> rows) {
            long w = 0;
            long m = 0;
            long b = 0;
            long k = 0;
            long r = 0;
            long s = 0;
            long i = 0;
            for (Row row : rows) {
                switch (row.type()) {
                    case WARN -> w += row.count();
                    case MUTE -> m += row.count();
                    case BAN -> b += row.count();
                    case KICK -> k += row.count();
                    case ISLAND_KICK, ISLAND_BAN, COOP_REMOVE, COOP_BLOCK -> i += row.count();
                }
                r += row.reversed();
                s += row.fromSuggestions();
            }
            return new Counts(w, m, b, k, r, s, i);
        }

        long given() {
            return warns + mutes + bans + kicks + island;
        }

        long net() {
            return given() - reversed;
        }

        /** Reversed share of given, rounded to a whole percent (0 when nothing was given). */
        long reversedPercent() {
            return given() == 0 ? 0 : Math.round(reversed * 100.0 / given());
        }
    }

    /** One line of {@code /pstats top}. */
    record Ranked(UUID staff, String name, Counts counts) {
    }

    /** Ranking: most given first, then fewest reversed, then name. */
    static List<Ranked> rank(Map<UUID, String> names, Map<UUID, List<Row>> rows, int limit) {
        List<Ranked> out = new ArrayList<>();
        rows.forEach((uuid, list) -> out.add(new Ranked(uuid, names.getOrDefault(uuid, "?"), Counts.of(list))));
        out.sort(Comparator.comparingLong((Ranked r) -> -r.counts().given())
                .thenComparingLong(r -> r.counts().reversed())
                .thenComparing(r -> r.name().toLowerCase(Locale.ROOT)));
        return out.size() > limit ? List.copyOf(out.subList(0, limit)) : List.copyOf(out);
    }

    /** A small time-to-live cache ({@code stats.cache}, 60 s) so repeated /pstats never re-run the query. */
    static final class Cache<K, V> {

        private record Entry<V>(long at, V value) {
        }

        private final Map<K, Entry<V>> entries = new ConcurrentHashMap<>();
        private final LongSupplier clock;
        private volatile Duration ttl;

        Cache(Duration ttl, LongSupplier clock) {
            this.ttl = ttl;
            this.clock = clock;
        }

        void ttl(Duration newTtl) {
            this.ttl = newTtl;
        }

        /** The cached value if younger than the ttl, else a fresh one from {@code loader} (stored). */
        V get(K key, Supplier<V> loader) {
            long now = clock.getAsLong();
            Entry<V> e = entries.get(key);
            if (e != null && ttl != null && now - e.at() < ttl.toMillis()) {
                return e.value();
            }
            V value = loader.get();
            entries.put(key, new Entry<>(now, value));
            if (entries.size() > 512) {
                entries.values().removeIf(x -> now - x.at() >= (ttl == null ? 0 : ttl.toMillis()));
            }
            return value;
        }

        void invalidate(K key) {
            entries.remove(key);
        }

        void clear() {
            entries.clear();
        }
    }
}
