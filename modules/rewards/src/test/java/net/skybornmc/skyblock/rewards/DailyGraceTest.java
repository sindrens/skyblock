package net.skybornmc.skyblock.rewards;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Owner 4 Nov 2026 (plan A, daily reward): one missed day keeps the streak, two break it; claim after some play. */
class DailyGraceTest {

    @Test
    void oneMissedDayKeepsTheStreak() {
        DailyRules.State s = DailyRules.claim(DailyRules.State.NONE, 100, 28, 1).orElseThrow().after();
        DailyRules.Claim gap = DailyRules.claim(s, 102, 28, 1).orElseThrow();
        assertEquals(2, gap.streakAfter(), "day 101 missed: still a streak");
        assertFalse(gap.streakBroken());
        DailyRules.Claim broken = DailyRules.claim(s, 103, 28, 1).orElseThrow();
        assertEquals(1, broken.streakAfter(), "two days missed: a new streak");
        assertTrue(broken.streakBroken());
        assertEquals(1, DailyRules.liveStreak(s, 102, 1));
        assertEquals(0, DailyRules.liveStreak(s, 103, 1));
    }

    @Test
    void claimAfterSomeMinutesOnline() {
        assertEquals(10, DailyRules.minutesToWait(1_000, 1_000, 10));
        assertEquals(1, DailyRules.minutesToWait(0 + 1, 9 * 60_000L + 1, 10));
        assertEquals(0, DailyRules.minutesToWait(1, 10 * 60_000L + 1, 10));
        assertEquals(0, DailyRules.minutesToWait(1_000, 1_000, 0), "off");
    }
}
