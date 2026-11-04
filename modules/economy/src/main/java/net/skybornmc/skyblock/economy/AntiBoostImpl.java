package net.skybornmc.skyblock.economy;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.Sql;
import net.skybornmc.skyblock.common.economy.AntiBoost;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link AntiBoost} (owner 4 Nov 2026): money caps for new profiles. Totals live in {@code eco_anti_boost} (one row per new
 * profile); profiles known to have played the threshold are remembered in memory, so transfers between veterans never touch
 * the database. The rules ({@link #decide}) and the refusal window ({@link RefusalWindow}) are pure and unit-tested.
 */
final class AntiBoostImpl implements AntiBoost {

    /** A refused check from an error (database or playtime lookup failed): {@link Check#error()}. */
    static final Check ERROR = new Check(false, null, null, 0, 0, 0, 0, 0);

    /** The settings of one check, read from economy.yml once per call. */
    record Settings(boolean enabled, long minutes, long maxReceived, long maxSent) {

        static Settings of(EconomyConfig c) {
            return new Settings(c.antiBoostEnabled, c.antiBoostPlaytimeMinutes, c.antiBoostMaxReceived, c.antiBoostMaxSent);
        }

        long thresholdMs() {
            return minutes * 60_000L;
        }

        /** True when a profile with this playtime is capped ({@code playtimeMs < 0} = unknown: not capped). */
        boolean isNew(long playtimeMs) {
            return enabled && minutes > 0 && (maxReceived > 0 || maxSent > 0) && playtimeMs >= 0 && playtimeMs < thresholdMs();
        }
    }

    /** What a profile received and sent so far. */
    record Totals(long received, long sent) {
        static final Totals ZERO = new Totals(0, 0);
    }

    /** Audit, staff notices and player texts (Bukkit side, in the module). */
    interface Reporter {
        void audit(Check refused, String path, Actor actor, List<Transfer> transfers);

        void alert(Check refused, String path, int count, int minutes);

        void tell(org.bukkit.entity.Player viewer, Check refused, String newPlayerName);
    }

    private final Database db;
    private final Supplier<EconomyConfig> config;
    private final Function<UUID, CompletableFuture<Long>> playtime;
    private final LongSupplier clock;
    private final Logger log;
    private final Reporter reporter;
    /** Profiles seen with at least this playtime (ms): playtime only grows, so they stay veterans for that threshold. */
    private final Map<UUID, Long> veterans = new ConcurrentHashMap<>();
    private final RefusalWindow refusals = new RefusalWindow();

    AntiBoostImpl(Database db, Supplier<EconomyConfig> config, Function<UUID, CompletableFuture<Long>> playtime, LongSupplier clock,
                  Logger log, Reporter reporter) {
        this.db = db;
        this.config = config;
        this.playtime = playtime;
        this.clock = clock;
        this.log = log;
        this.reporter = reporter;
    }

    // ================================================================== pure rules

    /** Adds without overflow (amounts are positive). */
    static long plus(long a, long b) {
        long r = a + b;
        return r < 0 ? Long.MAX_VALUE : r;
    }

    /** Sums per profile: [received, sent], in the order the profiles first appear. Invalid transfers are skipped. */
    static Map<UUID, long[]> sums(List<Transfer> transfers) {
        Map<UUID, long[]> out = new LinkedHashMap<>();
        for (Transfer t : transfers) {
            if (!valid(t)) {
                continue;
            }
            long[] to = out.computeIfAbsent(t.to(), k -> new long[2]);
            to[0] = plus(to[0], t.amount());
            long[] from = out.computeIfAbsent(t.from(), k -> new long[2]);
            from[1] = plus(from[1], t.amount());
        }
        return out;
    }

    static boolean valid(Transfer t) {
        return t != null && t.from() != null && t.to() != null && !t.from().equals(t.to()) && t.amount() > 0;
    }

    /** Whole minutes (rounded up, at least 1) a profile still has to play before the caps stop. */
    static long minutesLeft(Settings s, long playtimeMs) {
        long ms = Math.max(0, s.thresholdMs() - Math.max(0, playtimeMs));
        return Math.max(1, (ms + 59_999) / 60_000);
    }

    /**
     * The decision: every new profile in the transfers must fit its receive and send cap with its totals. The first
     * refusal (in transfer order, receive before send) is returned.
     *
     * @param playtimes playtime per profile in ms (missing or {@code -1} = unknown: not capped)
     * @param totals    stored totals of the new profiles (missing = nothing yet)
     */
    static Check decide(Settings s, List<Transfer> transfers, Map<UUID, Long> playtimes, Map<UUID, Totals> totals) {
        if (!s.enabled()) {
            return Check.OPEN;
        }
        for (Map.Entry<UUID, long[]> e : sums(transfers).entrySet()) {
            long play = playtimes.getOrDefault(e.getKey(), -1L);
            if (!s.isNew(play)) {
                continue;
            }
            Totals have = totals.getOrDefault(e.getKey(), Totals.ZERO);
            long in = e.getValue()[0];
            long out = e.getValue()[1];
            if (in > 0 && s.maxReceived() > 0 && plus(have.received(), in) > s.maxReceived()) {
                return new Check(false, Side.RECEIVER, e.getKey(), in, Math.max(0, s.maxReceived() - have.received()),
                        s.maxReceived(), minutesLeft(s, play), s.minutes());
            }
            if (out > 0 && s.maxSent() > 0 && plus(have.sent(), out) > s.maxSent()) {
                return new Check(false, Side.SENDER, e.getKey(), out, Math.max(0, s.maxSent() - have.sent()),
                        s.maxSent(), minutesLeft(s, play), s.minutes());
            }
        }
        return Check.OPEN;
    }

    /** The new profiles among the transfers, sorted (the lock order of their rows). */
    static TreeSet<UUID> newProfiles(Settings s, List<Transfer> transfers, Map<UUID, Long> playtimes) {
        TreeSet<UUID> out = new TreeSet<>();
        for (UUID profile : sums(transfers).keySet()) {
            if (s.isNew(playtimes.getOrDefault(profile, -1L))) {
                out.add(profile);
            }
        }
        return out;
    }

    /**
     * Refusals per profile within a time window (for the staff notice). Thread-safe. {@link #add} answers the count in the
     * window including this one; once it reaches the threshold the window starts over, so staff get one notice per burst.
     */
    static final class RefusalWindow {

        private final Map<UUID, Deque<Long>> times = new HashMap<>();

        synchronized int add(UUID profile, long now, long windowMs, int threshold) {
            Deque<Long> q = times.computeIfAbsent(profile, k -> new ArrayDeque<>());
            while (!q.isEmpty() && q.peekFirst() <= now - windowMs) {
                q.pollFirst();
            }
            q.addLast(now);
            int count = q.size();
            if (threshold > 0 && count >= threshold) {
                times.remove(profile);
            } else if (times.size() > 10_000) {
                times.entrySet().removeIf(en -> en.getValue().isEmpty() || en.getValue().peekLast() <= now - windowMs);
            }
            return count;
        }
    }

    // ================================================================== AntiBoost

    @Override
    public CompletableFuture<Guard> prepare(List<Transfer> transfers) {
        Settings s = Settings.of(config.get());
        List<Transfer> list = transfers == null ? List.of() : transfers.stream().filter(AntiBoostImpl::valid).toList();
        if (!s.enabled() || list.isEmpty()) {
            return CompletableFuture.completedFuture(open());
        }
        CompletableFuture<Guard> future;
        try {
            future = playtimes(s, list).thenCompose(play -> {
                TreeSet<UUID> fresh = newProfiles(s, list, play);
                if (fresh.isEmpty()) {
                    return CompletableFuture.completedFuture(open());
                }
                return db.async(c -> {
                    long now = clock.getAsLong();
                    for (UUID p : fresh) {
                        ensureRow(c, p, now);
                    }
                    Check preview = decide(s, list, play, read(c, fresh, false));
                    return (Guard) new GuardImpl(s, list, play, fresh, preview);
                });
            });
        } catch (RuntimeException e) {
            future = CompletableFuture.failedFuture(e);
        }
        return future.exceptionally(e -> {
            log.log(Level.WARNING, "Anti-boost: the check failed; the transfer is refused", Database.cause(e));
            return closed();
        });
    }

    @Override
    public CompletableFuture<Check> claim(List<Transfer> transfers) {
        return prepare(transfers).thenCompose(guard -> {
            if (!guard.preview().allowed() || !(guard instanceof GuardImpl g) || g.fresh.isEmpty()) {
                return CompletableFuture.completedFuture(guard.preview());
            }
            return db.transaction(c -> g.holds(c) ? Check.OPEN : g.refused());
        }).exceptionally(e -> {
            log.log(Level.WARNING, "Anti-boost: a claim failed; the transfer is refused", Database.cause(e));
            return ERROR;
        });
    }

    @Override
    public CompletableFuture<Void> release(List<Transfer> transfers) {
        List<Transfer> list = transfers == null ? List.of() : transfers.stream().filter(AntiBoostImpl::valid).toList();
        if (list.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        Map<UUID, long[]> sums = sums(list);
        return db.transaction(c -> {
            long now = clock.getAsLong();
            for (UUID p : new TreeSet<>(sums.keySet())) {
                long[] v = sums.get(p);
                // Only rows that exist (a veteran has none); never below 0.
                Sql.update(c, "UPDATE eco_anti_boost SET received = CASE WHEN received > ? THEN received - ? ELSE 0 END, "
                        + "sent = CASE WHEN sent > ? THEN sent - ? ELSE 0 END, updated_at = ? WHERE profile_id = ?",
                        v[0], v[0], v[1], v[1], now, p);
            }
            return (Void) null;
        }).exceptionally(e -> {
            log.log(Level.WARNING, "Anti-boost: releasing a claim failed (the totals stay a little high)", Database.cause(e));
            return null;
        });
    }

    @Override
    public void report(Check refused, String path, Actor actor, List<Transfer> transfers) {
        if (refused == null || refused.allowed()) {
            return;
        }
        try {
            reporter.audit(refused, path, actor, transfers == null ? List.of() : transfers);
            EconomyConfig cfg = config.get();
            if (refused.profile() == null || cfg.antiBoostAlertRefusals <= 0) {
                return;
            }
            int count = refusals.add(refused.profile(), clock.getAsLong(), cfg.antiBoostAlertMinutes * 60_000L, cfg.antiBoostAlertRefusals);
            if (count >= cfg.antiBoostAlertRefusals) {
                reporter.alert(refused, path, count, cfg.antiBoostAlertMinutes);
            }
        } catch (RuntimeException e) {
            log.log(Level.WARNING, "Anti-boost: reporting a refusal failed", e);
        }
    }

    @Override
    public void tell(org.bukkit.entity.Player viewer, Check refused, String newPlayerName) {
        if (viewer != null && refused != null && !refused.allowed()) {
            reporter.tell(viewer, refused, newPlayerName);
        }
    }

    // ================================================================== internals

    /** Playtime per profile; veterans from memory, the rest from the profiles service (remembered once they passed). */
    private CompletableFuture<Map<UUID, Long>> playtimes(Settings s, List<Transfer> list) {
        Set<UUID> profiles = sums(list).keySet();
        Map<UUID, CompletableFuture<Long>> pending = new LinkedHashMap<>();
        Map<UUID, Long> known = new ConcurrentHashMap<>();
        for (UUID p : profiles) {
            Long seen = veterans.get(p);
            if (seen != null && seen >= s.thresholdMs()) {
                known.put(p, seen);
            } else {
                pending.put(p, Objects.requireNonNullElseGet(playtime.apply(p), () -> CompletableFuture.completedFuture(-1L)));
            }
        }
        return CompletableFuture.allOf(pending.values().toArray(new CompletableFuture<?>[0])).thenApply(v -> {
            for (Map.Entry<UUID, CompletableFuture<Long>> e : pending.entrySet()) {
                Long ms = e.getValue().join();
                long value = ms == null ? -1L : ms;
                known.put(e.getKey(), value);
                if (value >= s.thresholdMs()) {
                    veterans.merge(e.getKey(), value, Math::max);
                }
            }
            return known;
        });
    }

    /** Creates the row of a new profile outside the money transaction (a parallel insert of the same row is fine). */
    private static void ensureRow(Connection c, UUID profile, long now) throws SQLException {
        if (Sql.queryOne(c, "SELECT 1 FROM eco_anti_boost WHERE profile_id = ?", rs -> 1, profile).isPresent()) {
            return;
        }
        try {
            Sql.update(c, "INSERT INTO eco_anti_boost (profile_id, received, sent, updated_at) VALUES (?, 0, 0, ?)", profile, now);
        } catch (SQLException e) {
            if (!EconomyStore.isDuplicate(e)) {
                throw e;
            }
        }
    }

    /** Totals of the profiles, in their (sorted) order; {@code lock} = {@code FOR UPDATE} inside a transaction. */
    static Map<UUID, Totals> read(Connection c, TreeSet<UUID> profiles, boolean lock) throws SQLException {
        Map<UUID, Totals> out = new HashMap<>();
        String sql = "SELECT received, sent FROM eco_anti_boost WHERE profile_id = ?" + (lock ? " FOR UPDATE" : "");
        for (UUID p : profiles) {
            Sql.queryOne(c, sql, rs -> new Totals(rs.getLong(1), rs.getLong(2)), p).ifPresent(t -> out.put(p, t));
        }
        return out;
    }

    /** Adds what these transfers move to the totals of the new profiles (inside the transaction). */
    private static void add(Connection c, TreeSet<UUID> fresh, List<Transfer> list, long now) throws SQLException {
        Map<UUID, long[]> sums = sums(list);
        for (UUID p : fresh) {
            long[] v = sums.get(p);
            if (v == null) {
                continue;
            }
            int changed = Sql.update(c, "UPDATE eco_anti_boost SET received = received + ?, sent = sent + ?, updated_at = ? "
                    + "WHERE profile_id = ?", v[0], v[1], now, p);
            if (changed == 0) {
                Sql.update(c, "INSERT INTO eco_anti_boost (profile_id, received, sent, updated_at) VALUES (?, ?, ?, ?)", p, v[0], v[1], now);
            }
        }
    }

    private Guard open() {
        return new GuardImpl(null, List.of(), Map.of(), new TreeSet<>(), Check.OPEN);
    }

    private Guard closed() {
        return new GuardImpl(null, List.of(), Map.of(), new TreeSet<>(), ERROR);
    }

    /** The guard of one {@link #prepare}: re-reads the locked totals, refuses or counts. May run more than once (retries). */
    private final class GuardImpl implements Guard {

        private final Settings settings;
        private final List<Transfer> list;
        private final Map<UUID, Long> play;
        final TreeSet<UUID> fresh;
        private final Check preview;
        private volatile Check refused = Check.OPEN;

        GuardImpl(Settings settings, List<Transfer> list, Map<UUID, Long> play, TreeSet<UUID> fresh, Check preview) {
            this.settings = settings;
            this.list = new ArrayList<>(list);
            this.play = play;
            this.fresh = fresh;
            this.preview = preview;
        }

        @Override
        public Check preview() {
            return preview;
        }

        @Override
        public Check refused() {
            return refused;
        }

        @Override
        public boolean holds(Connection c) throws SQLException {
            refused = Check.OPEN;
            if (!preview.allowed()) {
                refused = preview; // never run a refused (or failed) check
                return false;
            }
            if (fresh.isEmpty()) {
                return true;
            }
            Check check = decide(settings, list, play, read(c, fresh, true));
            if (!check.allowed()) {
                refused = check;
                return false;
            }
            add(c, fresh, list, clock.getAsLong());
            return true;
        }
    }
}
