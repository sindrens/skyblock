package net.skybornmc.skyblock.moderation;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;

/**
 * The "Honorable" title (spec 14.11, clean streak), pure.
 *
 * <p>Earned when all are true: no warn, mute, ban or kick was created in the last {@code days} (60) that is still on
 * record (pardoned, undone, rolled back and accepted-appeal punishments do not break the streak); the account is older
 * than that; and at least {@code min-playtime} (10h) was played in that period (so inactive or alt accounts can't farm
 * it). Lost (the title is taken, the history is kept) when the player gets a new warn, mute, ban or kick after earning
 * it; earned again after another clean period. Staff can't give or take it. Checked on join and every {@code check} (6h)
 * for online players, never per tick.</p>
 */
final class CleanStreak {

    private CleanStreak() {
    }

    /** Punishment types that break a streak (spec: warn, mute, ban or kick). */
    static final Set<Punishment.Type> BREAKING = Set.of(Punishment.Type.WARN, Punishment.Type.MUTE, Punishment.Type.BAN,
            Punishment.Type.KICK);

    /**
     * {@code extras.clean-streak}.
     *
     * @param days        clean days needed (and the minimum account age)
     * @param minPlaytime played within those days
     * @param check       how often online players are checked
     * @param joinDelay   the check after a join waits this long (after the join messages, never in the same burst)
     * @param title       the title id in {@code tags-titles.yml}
     * @param sound       named sound (sounds.yml) when it unlocks, empty = none
     * @param tellLost    tell the player (one line) when a new punishment hides it
     */
    record Settings(boolean enabled, int days, Duration minPlaytime, Duration check, Duration joinDelay, String title,
                    String sound, boolean tellLost) {
        static final Settings DEFAULT = new Settings(true, 60, Duration.ofHours(10), Duration.ofHours(6), Duration.ofSeconds(10),
                "honorable", "unlock", true);

        Duration period() {
            return Duration.ofDays(days);
        }
    }

    /** Stored state ({@code punish_clean_streak}); {@link #NONE} = no row yet. */
    record State(boolean earned, Long earnedAt, Long lostAt, int timesEarned) {
        static final State NONE = new State(false, null, null, 0);
    }

    /**
     * What is known about the player at a check.
     *
     * @param firstSeen    first join (account-wide)
     * @param lastBreaking newest warn/mute/ban/kick that is still on record (not reversed), {@code null} = none ever
     * @param played       playtime within the period (see {@link #played})
     * @param owns         the cosmetics module says the player owns the title
     */
    record Facts(long now, long firstSeen, Long lastBreaking, long played, boolean owns, State state) {

        Facts withOwns(boolean value) {
            return new Facts(now, firstSeen, lastBreaking, played, value, state);
        }
    }

    enum Action {
        /** Nothing to do. */
        NONE,
        /** Newly earned: grant, store EARNED, one message + sound. */
        GRANT,
        /** Earned but the title row is missing (e.g. the title was redefined): grant again without a message. */
        REGRANT_QUIET,
        /** A new punishment after it was earned: take it, store LOST, one line (if enabled). */
        REVOKE
    }

    /** All three conditions hold now. */
    static boolean eligible(Settings s, Facts f) {
        long period = s.period().toMillis();
        boolean clean = f.lastBreaking() == null || f.now() - f.lastBreaking() >= period;
        boolean oldEnough = f.now() - f.firstSeen() >= period;
        boolean played = f.played() >= s.minPlaytime().toMillis();
        return clean && oldEnough && played;
    }

    static Action decide(Settings s, Facts f) {
        if (!s.enabled()) {
            return Action.NONE;
        }
        State st = f.state();
        if (st.earned()) {
            long earnedAt = st.earnedAt() == null ? 0 : st.earnedAt();
            if (f.lastBreaking() != null && f.lastBreaking() > earnedAt) {
                return Action.REVOKE;
            }
            return f.owns() ? Action.NONE : Action.REGRANT_QUIET;
        }
        // Never take a title this feature did not give (staff can't give it; a title id changed in the config may be one
        // players earned elsewhere), so "owned but not earned here" changes nothing.
        return eligible(s, f) ? Action.GRANT : Action.NONE;
    }

    /** A punishment of this type, given now, hides an earned title. */
    static boolean breaks(Punishment.Type type) {
        return BREAKING.contains(type);
    }

    // ------------------------------------------------------------------ playtime per period

    /** Epoch day of {@code at} in {@code zone}. */
    static int day(long at, ZoneId zone) {
        return (int) Instant.ofEpochMilli(at).atZone(zone).toLocalDate().toEpochDay();
    }

    /** The day the period starts on. */
    static int startDay(long now, ZoneId zone, Settings s) {
        return day(now - s.period().toMillis(), zone);
    }

    /**
     * The sample used as the start of the period: the newest one on or before {@code startDay}; when there is none (the
     * samples began later, e.g. after the update), the oldest one, which can only count less playtime, never more.
     * {@code null} without samples.
     */
    static Map.Entry<Integer, Long> baseline(NavigableMap<Integer, Long> samples, int startDay) {
        if (samples.isEmpty()) {
            return null;
        }
        Map.Entry<Integer, Long> e = samples.floorEntry(startDay);
        return e != null ? e : samples.firstEntry();
    }

    /**
     * Playtime within the period: the total now minus the total at the {@link #baseline}. Each sample is the account's
     * total playtime at the last check of that day. No samples = 0 (counting starts now).
     */
    static long played(long totalNow, NavigableMap<Integer, Long> samples, int startDay) {
        Map.Entry<Integer, Long> base = baseline(samples, startDay);
        return base == null ? 0 : Math.max(0, totalNow - base.getValue());
    }

    /** Samples with today's value set (the stored row of today is overwritten at every check). */
    static NavigableMap<Integer, Long> withToday(NavigableMap<Integer, Long> samples, int today, long totalNow) {
        NavigableMap<Integer, Long> out = new TreeMap<>(samples);
        out.put(today, totalNow);
        return out;
    }

    /** Samples older than the baseline are no longer needed (delete days before this one); {@code null} = keep all. */
    static Integer pruneBefore(NavigableMap<Integer, Long> samples, int startDay) {
        Map.Entry<Integer, Long> base = baseline(samples, startDay);
        return base == null ? null : base.getKey();
    }
}
