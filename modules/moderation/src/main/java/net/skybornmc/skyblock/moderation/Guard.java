package net.skybornmc.skyblock.moderation;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Rogue or hacked staff protection (spec 14.1), pure. A sliding window ({@code extras.guard.window}, 5m) counts what each
 * staff member <b>applied</b> (requests, dry runs and blocked attempts never count). Each tier (the rank tiers of
 * {@code moderation.yml → limits}: helper, mod, admin) has limits for bans, mutes, sanctions and everything together.
 *
 * <ul>
 *   <li>{@link Mode#BLOCK} (helpers, mods): the punishment that would go over a limit is refused and the staff member is
 *       <b>blocked</b> until an admin runs {@code /pguard allow} (a block survives restarts).</li>
 *   <li>{@link Mode#ALERT} (admins): it is applied and only the alert is sent, so a legit mass cleanup is never blocked.</li>
 *   <li>{@code /pguard lock}: refuses every punishment of that person at once, until {@code /pguard unlock}.</li>
 *   <li>{@code /pguard allow <staff> [time]}: lifts a block and, for that time, limits only alert.</li>
 * </ul>
 * Staff without a tier (the owner, {@code skyblock.admin.punish.guard.exempt}), the console and the system rules are
 * never limited. Bulk punishments count per player (each player is its own punishment). Limits are a safety net, not a
 * punishment.
 */
final class Guard {

    private Guard() {
    }

    /** What happens when a limit is hit. */
    enum Mode {
        BLOCK, ALERT;

        String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        static Optional<Mode> parse(String raw) {
            if (raw == null) {
                return Optional.empty();
            }
            for (Mode m : values()) {
                if (m.id().equals(raw.strip().toLowerCase(Locale.ROOT))) {
                    return Optional.of(m);
                }
            }
            return Optional.empty();
        }
    }

    /** What is counted: bans (IP bans too), mutes, sanctions; everything else only counts toward the total. */
    enum Kind {
        BAN, MUTE, SANCTION, OTHER;

        static Kind of(Punishment.Type type) {
            return switch (type) {
                case BAN -> BAN;
                case MUTE -> MUTE;
                default -> OTHER;
            };
        }
    }

    /** Which limit was hit. */
    enum Limit {
        BANS, MUTES, SANCTIONS, TOTAL;

        String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * One tier: {@code mode} and the limits per window. A limit of 0 means none of that kind is allowed; -1 = no limit.
     */
    record Tier(Mode mode, int maxBans, int maxMutes, int maxSanctions, int maxTotal) {

        int max(Limit limit) {
            return switch (limit) {
                case BANS -> maxBans;
                case MUTES -> maxMutes;
                case SANCTIONS -> maxSanctions;
                case TOTAL -> maxTotal;
            };
        }
    }

    /**
     * {@code extras.guard}.
     *
     * @param enabled        off = nobody is limited (locks still work: they are an admin decision, not a limit)
     * @param window         the sliding window (5m)
     * @param tiers          tier name (moderation.yml limits tier) → limits, in config order
     * @param defaultTier    tier of staff without any {@code skyblock.staff.punish.max.<tier>} permission
     * @param allowDefault   {@code /pguard allow} without a time
     * @param alertRollback  the period of the [Rollback] button in the alert
     */
    record Settings(boolean enabled, Duration window, Map<String, Tier> tiers, String defaultTier, Duration allowDefault,
                    Duration alertRollback) {

        static final Settings DEFAULT = new Settings(true, Duration.ofMinutes(5), defaultTiers(), "helper",
                Duration.ofMinutes(30), Duration.ofHours(1));

        Settings {
            tiers = tiers == null ? Map.of() : java.util.Collections.unmodifiableMap(new LinkedHashMap<>(tiers));
        }

        private static Map<String, Tier> defaultTiers() {
            Map<String, Tier> m = new LinkedHashMap<>();
            m.put("helper", new Tier(Mode.BLOCK, 5, 15, 0, 25));
            m.put("mod", new Tier(Mode.BLOCK, 10, 25, 3, 40));
            m.put("admin", new Tier(Mode.ALERT, 30, 60, 10, 100));
            return m;
        }

        /** The tier with this name, or {@code null}. */
        Tier tier(String name) {
            return name == null ? null : tiers.get(name);
        }
    }

    /**
     * The tier of a staff member from the {@code skyblock.staff.punish.max.<tier>} tiers they hold: the one with the
     * highest total limit wins (senior staff get the higher limits); none held = {@code defaultTier}.
     */
    static String tierOf(Settings s, List<String> held) {
        String best = null;
        for (String name : held) {
            Tier t = s.tier(name);
            if (t != null && (best == null || t.maxTotal() > s.tier(best).maxTotal())) {
                best = name;
            }
        }
        return best != null ? best : s.tier(s.defaultTier()) != null ? s.defaultTier() : null;
    }

    /** Counts in the window. */
    record Counts(int bans, int mutes, int sanctions, int total) {

        static final Counts ZERO = new Counts(0, 0, 0, 0);

        Counts plus(Kind kind) {
            return new Counts(bans + (kind == Kind.BAN ? 1 : 0), mutes + (kind == Kind.MUTE ? 1 : 0),
                    sanctions + (kind == Kind.SANCTION ? 1 : 0), total + 1);
        }

        int of(Limit limit) {
            return switch (limit) {
                case BANS -> bans;
                case MUTES -> mutes;
                case SANCTIONS -> sanctions;
                case TOTAL -> total;
            };
        }
    }

    /** A limit that was gone over: {@code count} (with this one) of {@code max}. */
    record Hit(Limit limit, int count, int max) {
    }

    /** The limits relevant for {@code kind} (its own one, then the total). */
    private static List<Limit> limitsFor(Kind kind) {
        return switch (kind) {
            case BAN -> List.of(Limit.BANS, Limit.TOTAL);
            case MUTE -> List.of(Limit.MUTES, Limit.TOTAL);
            case SANCTION -> List.of(Limit.SANCTIONS, Limit.TOTAL);
            case OTHER -> List.of(Limit.TOTAL);
        };
    }

    /** The first limit {@code after} (the counts including the new one) goes over, or {@code null}. */
    static Hit exceeded(Tier tier, Counts after, Kind kind) {
        if (tier == null) {
            return null;
        }
        for (Limit l : limitsFor(kind)) {
            int max = tier.max(l);
            if (max >= 0 && after.of(l) > max) {
                return new Hit(l, after.of(l), max);
            }
        }
        return null;
    }

    /** Stored state of one staff member ({@code punish_guard}). */
    enum State { BLOCKED, LOCKED, ALLOWED }

    /**
     * One row: {@code until} = end of an {@link State#ALLOWED} period ({@code null} otherwise), {@code hit} = what caused a
     * block ("bans 6/5"), {@code by} = who set it.
     */
    record Entry(UUID staff, String staffName, State state, Long until, String hit, String by, long updated) {

        boolean allowedAt(long now) {
            return state == State.ALLOWED && until != null && until > now;
        }
    }

    /** What the guard says about one more punishment. */
    enum Verdict {
        /** Go on. */
        OK,
        /** Go on, but a limit is gone over: alert admins (alert mode or an allowed period). */
        ALERT,
        /** Refused: locked with /pguard lock. */
        LOCKED,
        /** Refused: blocked earlier, waiting for /pguard allow. */
        BLOCKED,
        /** Refused: this one goes over a limit in block mode; the staff member is blocked from now on. */
        BLOCK_NOW
    }

    record Decision(Verdict verdict, Hit hit) {

        static final Decision OK = new Decision(Verdict.OK, null);

        boolean refused() {
            return verdict == Verdict.LOCKED || verdict == Verdict.BLOCKED || verdict == Verdict.BLOCK_NOW;
        }
    }

    /**
     * The decision for one more punishment of {@code kind} by a staff member of {@code tierName} with {@code entry}
     * (may be {@code null}) and {@code before} (counts in the window without this one).
     */
    static Decision decide(Settings s, String tierName, Entry entry, Counts before, Kind kind, long now) {
        if (entry != null && entry.state() == State.LOCKED) {
            return new Decision(Verdict.LOCKED, null); // a lock is an admin decision: it holds even with the guard off
        }
        if (s == null || !s.enabled() || tierName == null) {
            return Decision.OK;
        }
        Tier tier = s.tier(tierName);
        if (tier == null) {
            return Decision.OK;
        }
        if (entry != null && entry.state() == State.BLOCKED) {
            return new Decision(Verdict.BLOCKED, null);
        }
        Hit hit = exceeded(tier, (before == null ? Counts.ZERO : before).plus(kind), kind);
        if (hit == null) {
            return Decision.OK;
        }
        boolean allowed = entry != null && entry.allowedAt(now);
        if (allowed || tier.mode() == Mode.ALERT) {
            return new Decision(Verdict.ALERT, hit);
        }
        return new Decision(Verdict.BLOCK_NOW, hit);
    }

    /**
     * The sliding window: what each staff member applied, with the time. Thread-safe (the issue path runs on the database
     * threads); old entries are dropped on every call, so memory stays at most one window of actions per staff member.
     */
    static final class Window {

        private record Item(long at, Kind kind) {
        }

        private final Map<UUID, Deque<Item>> items = new HashMap<>();

        synchronized void record(UUID staff, Kind kind, long at) {
            if (staff != null) {
                items.computeIfAbsent(staff, k -> new ArrayDeque<>()).addLast(new Item(at, kind));
            }
        }

        /** Counts of {@code staff} in {@code (now - window, now]}. */
        synchronized Counts counts(UUID staff, Duration window, long now) {
            Deque<Item> q = staff == null ? null : items.get(staff);
            if (q == null) {
                return Counts.ZERO;
            }
            long from = now - (window == null ? 0 : window.toMillis());
            while (!q.isEmpty() && q.peekFirst().at() <= from) {
                q.removeFirst();
            }
            if (q.isEmpty()) {
                items.remove(staff);
                return Counts.ZERO;
            }
            int b = 0;
            int m = 0;
            int s = 0;
            for (Item i : q) {
                switch (i.kind()) {
                    case BAN -> b++;
                    case MUTE -> m++;
                    case SANCTION -> s++;
                    default -> {
                    }
                }
            }
            return new Counts(b, m, s, q.size());
        }

        /** Forgets everything of {@code staff} ({@code /pguard allow}: a fresh start). */
        synchronized void clear(UUID staff) {
            items.remove(staff);
        }
    }

    /** "6 bans" style counter text key part ({@code guard.limits.<id>}). */
    static String hitText(Hit hit) {
        return hit == null ? "" : hit.limit().id() + " " + hit.count() + "/" + hit.max();
    }
}
