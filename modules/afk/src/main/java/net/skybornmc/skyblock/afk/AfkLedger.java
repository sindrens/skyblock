package net.skybornmc.skyblock.afk;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Crediting logic of the AFK pool (pure, tested): day keys in the reset time zone, the daily cap, rewards every
 * {@code reward-minutes} of credited time and key milestones of the day.
 */
final class AfkLedger {

    /** A reward is due: its number (idempotency) and the session length when it was earned (reward step). */
    record Reward(long seq, long sessionMillis, int day) {
    }

    /** What one credit step produced. */
    record Result(long credited, List<Reward> rewards, List<AfkRules.KeyMilestone> keys, boolean reachedCap) {

        static final Result NONE = new Result(0, List.of(), List.of(), false);
    }

    private AfkLedger() {
    }

    /** The day of an instant in the reset time zone as {@code yyyymmdd} (e.g. 20261030). */
    static int dayKey(long epochMillis, ZoneId zone) {
        LocalDate d = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate();
        return d.getYear() * 10_000 + d.getMonthValue() * 100 + d.getDayOfMonth();
    }

    /** Milliseconds until the next midnight in the zone. */
    static long millisToMidnight(long epochMillis, ZoneId zone) {
        var now = Instant.ofEpochMilli(epochMillis).atZone(zone);
        var next = now.toLocalDate().plusDays(1).atStartOfDay(zone);
        return next.toInstant().toEpochMilli() - epochMillis;
    }

    /** Starts a new day when {@code today} differs: today's total goes back to 0, the reward progress stays. */
    static boolean rollDay(AfkAccount a, int today) {
        if (a.day == today) {
            return false;
        }
        a.day = today;
        a.creditedMillis = 0;
        a.dirty = true;
        return true;
    }

    /** Time credited today (0 when the stored day is not today). */
    static long creditedToday(AfkAccount a, int today) {
        return a.day == today ? a.creditedMillis : 0;
    }

    static boolean capped(AfkAccount a, int today, AfkRules rules) {
        return creditedToday(a, today) >= rules.dailyCapMillis();
    }

    /**
     * Credits up to {@code millis} of earning time on {@code today} (never past the daily cap or {@code extraLimit}, the
     * IP's remaining time) and returns the rewards and key milestones it produced. The account changes in place.
     */
    static Result credit(AfkAccount a, int today, long millis, long extraLimit, AfkRules rules) {
        rollDay(a, today);
        long room = Math.min(rules.dailyCapMillis() - a.creditedMillis, extraLimit);
        long credit = Math.min(Math.max(0, millis), Math.max(0, room));
        if (credit <= 0) {
            return Result.NONE;
        }
        long before = a.creditedMillis;
        a.creditedMillis += credit;
        a.sessionMillis += credit;
        a.dirty = true;
        List<Reward> rewards = new ArrayList<>(1);
        long progress = a.progressMillis + credit;
        long period = Math.max(1, rules.rewardMillis());
        while (progress >= period) {
            progress -= period;
            a.rewardSeq++;
            rewards.add(new Reward(a.rewardSeq, a.sessionMillis - progress, today));
        }
        a.progressMillis = progress;
        return new Result(credit, rewards, rules.crossed(before, a.creditedMillis), a.creditedMillis >= rules.dailyCapMillis());
    }

    /** Milliseconds of earning until the next reward. */
    static long untilNextReward(AfkAccount a, AfkRules rules) {
        return Math.max(0, rules.rewardMillis() - a.progressMillis);
    }
}
