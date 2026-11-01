package net.skybornmc.skyblock.rewards;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Weekly / Monthly Reward periods (spec 6.1, golden rule 7): Oslo time, Monday and the 1st, DST-safe. */
class PeriodicRulesTest {

    private static final ZoneId OSLO = ZoneId.of("Europe/Oslo");

    private static long at(int y, int mo, int d, int h, int mi) {
        return ZonedDateTime.of(y, mo, d, h, mi, 0, 0, OSLO).toInstant().toEpochMilli();
    }

    @Test
    void weekKeysFlipAtMondayMidnightOslo() {
        assertEquals("2026-W45", PeriodicRules.key(PeriodicRules.Period.WEEK, at(2026, 11, 8, 23, 59), OSLO), "Sunday night");
        assertEquals("2026-W46", PeriodicRules.key(PeriodicRules.Period.WEEK, at(2026, 11, 9, 0, 0), OSLO), "Monday 00:00");
        assertEquals("2026-W53", PeriodicRules.key(PeriodicRules.Period.WEEK, at(2027, 1, 1, 12, 0), OSLO), "ISO week-based year");
    }

    @Test
    void monthKeysFlipOnTheFirst() {
        assertEquals("2026-10", PeriodicRules.key(PeriodicRules.Period.MONTH, at(2026, 10, 31, 23, 59), OSLO));
        assertEquals("2026-11", PeriodicRules.key(PeriodicRules.Period.MONTH, at(2026, 11, 1, 0, 0), OSLO));
    }

    @Test
    void resetCountdownsAreDstSafe() {
        // 2026-10-25 the clocks go back: Sunday 25 Oct 12:00 to Monday 26 Oct 00:00 is 13 real hours.
        assertEquals(Duration.ofHours(13), PeriodicRules.untilReset(PeriodicRules.Period.WEEK, at(2026, 10, 25, 12, 0), OSLO));
        // 2027-03-28 the clocks go forward: Sunday 28 Mar 12:00 to Monday 00:00 is 12 real hours.
        assertEquals(Duration.ofHours(12), PeriodicRules.untilReset(PeriodicRules.Period.WEEK, at(2027, 3, 28, 12, 0), OSLO));
        assertEquals(Duration.ofHours(1), PeriodicRules.untilReset(PeriodicRules.Period.MONTH, at(2026, 11, 30, 23, 0), OSLO));
        assertEquals(Duration.ofDays(7), PeriodicRules.untilReset(PeriodicRules.Period.WEEK, at(2026, 11, 9, 0, 0), OSLO));
    }

    @Test
    void weeklyNeedsThreeHoursOnThisProfile() {
        assertFalse(PeriodicRules.weeklyUnlocked(3 * 3600 - 1, 180));
        assertTrue(PeriodicRules.weeklyUnlocked(3 * 3600, 180), "claimable the moment the threshold is reached");
        assertTrue(PeriodicRules.weeklyUnlocked(0, 0));
    }

    @Test
    void claimKeysAreOnePerSourceProfileAndPeriod() {
        java.util.UUID p = java.util.UUID.randomUUID();
        assertEquals("weekly:" + p + ":2026-W45", PeriodicRules.claimKey(PeriodicRules.Period.WEEK, p, "2026-W45"));
        assertTrue(PeriodicRules.claimKey(PeriodicRules.Period.MONTH, p, "2026-11").length() <= 128);
    }

    @Test
    void defaultContentsParseAndHoldTheOwnerKeys() {
        RewardsSettings s = new RewardsSettings();
        List<String> problems = new ArrayList<>();
        List<RewardSpec> weekly = RewardsModule.rewardLines(s.weeklyRewards, "weekly", problems::add);
        List<RewardSpec> monthly = RewardsModule.rewardLines(s.monthlyRewards, "monthly", problems::add);
        assertEquals(List.of(), problems);
        assertTrue(weekly.stream().anyMatch(r -> r.kind() == RewardSpec.Kind.KEY && r.ref().equals("blaze") && r.amount() == 1),
                "owner F5a: one Blaze key a week");
        assertTrue(monthly.stream().anyMatch(r -> r.kind() == RewardSpec.Kind.KEY && r.amount() == 2), "owner F5b: two keys");
        assertTrue(weekly.stream().anyMatch(r -> r.kind() == RewardSpec.Kind.SHARDS && r.amount() == 25));
        assertTrue(weekly.stream().anyMatch(r -> r.kind() == RewardSpec.Kind.PASS_XP && r.amount() == 400));
        assertFalse(s.weeklyEnabled || s.monthlyEnabled || s.menuEnabled, "golden rule 10: nothing ships on");
        assertEquals(180, s.weeklyRequiredMinutes);
    }
}
