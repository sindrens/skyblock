package net.skybornmc.skyblock.rewards;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.time.DayOfWeek;

/**
 * Weekly Reward and Monthly Reward periods (spec 6.1, golden rule 7): a week starts Monday 00:00, a month the 1st 00:00,
 * both in the reward zone (Europe/Oslo), DST-safe. Period keys are text: {@code 2026-W45} (ISO week-based year) and
 * {@code 2026-11}. Pure, unit-tested.
 */
final class PeriodicRules {

    /** The two periodic sources of the Reward Core. */
    enum Period {
        WEEK("weekly"),
        MONTH("monthly");

        private final String source;

        Period(String source) {
            this.source = source;
        }

        /** Source id ({@code reward_claims.reward_source}, RewardsService source, grant source, key reason). */
        String source() {
            return source;
        }
    }

    private PeriodicRules() {
    }

    static String key(Period period, long millis, ZoneId zone) {
        LocalDate d = LocalDate.ofInstant(Instant.ofEpochMilli(millis), zone);
        return period == Period.WEEK
                ? String.format("%d-W%02d", d.get(IsoFields.WEEK_BASED_YEAR), d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR))
                : String.format("%d-%02d", d.getYear(), d.getMonthValue());
    }

    /** Time until the next period starts, never negative. */
    static Duration untilReset(Period period, long millis, ZoneId zone) {
        Instant now = Instant.ofEpochMilli(millis);
        LocalDate d = LocalDate.ofInstant(now, zone);
        LocalDate next = period == Period.WEEK ? d.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                : d.with(TemporalAdjusters.firstDayOfNextMonth());
        ZonedDateTime start = next.atStartOfDay(zone);
        Duration left = Duration.between(now, start.toInstant());
        return left.isNegative() ? Duration.ZERO : left;
    }

    /** The Weekly Reward is unlocked: at least {@code requiredMinutes} active minutes on this profile this week. */
    static boolean weeklyUnlocked(long activeSeconds, int requiredMinutes) {
        return activeSeconds >= Math.max(0, requiredMinutes) * 60L;
    }

    /** Idempotency key of a claim: one per source, profile and period. */
    static String claimKey(Period period, java.util.UUID profile, String periodKey) {
        return period.source() + ":" + profile + ":" + periodKey;
    }
}
