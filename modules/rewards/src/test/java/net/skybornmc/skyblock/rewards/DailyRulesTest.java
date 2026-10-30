package net.skybornmc.skyblock.rewards;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Day and streak math of the daily calendar (reset 00:00 Europe/Oslo, calendar moves only on a claim). */
class DailyRulesTest {

    private static final ZoneId OSLO = ZoneId.of("Europe/Oslo");
    private static final int LEN = 30;

    private static long oslo(int y, int mo, int d, int h, int mi) {
        return LocalDateTime.of(y, mo, d, h, mi).atZone(OSLO).toInstant().toEpochMilli();
    }

    private static int day(int y, int mo, int d) {
        return (int) LocalDate.of(y, mo, d).toEpochDay();
    }

    @Test
    void theDayChangesAtOsloMidnightNotUtc() {
        // 23:59 Oslo on 30 Oct (winter time, UTC+1) is 22:59 UTC: still the 30th.
        assertEquals(day(2026, 10, 30), DailyRules.today(oslo(2026, 10, 30, 23, 59), OSLO));
        // 00:00 Oslo on 31 Oct is 23:00 UTC on the 30th, but already the 31st in Oslo.
        assertEquals(day(2026, 10, 31), DailyRules.today(oslo(2026, 10, 31, 0, 0), OSLO));
        assertEquals(day(2026, 10, 30), DailyRules.today(oslo(2026, 10, 31, 0, 0), ZoneId.of("UTC")));
    }

    @Test
    void untilResetFollowsDaylightSavingDays() {
        assertEquals(Duration.ofMinutes(1), DailyRules.untilReset(oslo(2026, 10, 30, 23, 59), OSLO));
        // 29 March 2026: clocks jump 02:00 -> 03:00, so the day has 23 hours.
        assertEquals(Duration.ofHours(23), DailyRules.untilReset(oslo(2026, 3, 29, 0, 0), OSLO));
        // 25 October 2026: clocks go back 03:00 -> 02:00, so the day has 25 hours.
        assertEquals(Duration.ofHours(25), DailyRules.untilReset(oslo(2026, 10, 25, 0, 0), OSLO));
    }

    @Test
    void firstClaimIsDayOneWithStreakOne() {
        DailyRules.Claim c = DailyRules.claim(DailyRules.State.NONE, 100, LEN).orElseThrow();
        assertEquals(1, c.day());
        assertEquals(1, c.streakAfter());
        assertFalse(c.streakBroken());
        assertEquals(new DailyRules.State(1, 100, 1, 1, 0, DailyRules.NEVER, 1), c.after());
    }

    @Test
    void onlyOneClaimPerDay() {
        DailyRules.State s = DailyRules.claim(DailyRules.State.NONE, 100, LEN).orElseThrow().after();
        assertTrue(DailyRules.claimedToday(s, 100));
        assertEquals(Optional.empty(), DailyRules.claim(s, 100, LEN));
        // Clock turned back: still no second claim.
        assertEquals(Optional.empty(), DailyRules.claim(s, 99, LEN));
    }

    @Test
    void nextDayAdvancesCalendarAndStreak() {
        DailyRules.State s = DailyRules.claim(DailyRules.State.NONE, 100, LEN).orElseThrow().after();
        DailyRules.Claim c = DailyRules.claim(s, 101, LEN).orElseThrow();
        assertEquals(2, c.day());
        assertEquals(2, c.streakAfter());
    }

    @Test
    void skippingADayKeepsTheCalendarButResetsTheStreak() {
        DailyRules.State s = new DailyRules.State(12, 100, 12, 12, 0, DailyRules.NEVER, 12);
        assertEquals(0, DailyRules.liveStreak(s, 102));
        assertEquals(12, DailyRules.liveStreak(s, 101));
        DailyRules.Claim c = DailyRules.claim(s, 102, LEN).orElseThrow();
        assertEquals(13, c.day(), "the calendar never goes back");
        assertEquals(1, c.streakAfter());
        assertTrue(c.streakBroken());
        assertEquals(12, c.after().lostStreak());
        assertEquals(102, c.after().brokenOn());
        assertEquals(12, c.after().best());
    }

    @Test
    void afterTheLastDayTheCalendarStartsAgain() {
        DailyRules.State s = new DailyRules.State(30, 100, 30, 30, 0, DailyRules.NEVER, 30);
        assertEquals(30, DailyRules.claimedUpTo(s, 100, LEN), "all days shown claimed on the day of day 30");
        assertEquals(0, DailyRules.claimedUpTo(s, 101, LEN), "a new round the next day");
        DailyRules.Claim c = DailyRules.claim(s, 101, LEN).orElseThrow();
        assertEquals(1, c.day());
        assertEquals(31, c.streakAfter());
        // A shorter calendar (owner removed days) also wraps.
        assertEquals(1, DailyRules.claim(new DailyRules.State(30, 100, 1, 1, 0, -1, 1), 101, 20).orElseThrow().day());
    }

    @Test
    void streakGemsEverySevenDays() {
        assertEquals(0, DailyRules.streakGems(6, 7, 10));
        assertEquals(10, DailyRules.streakGems(7, 7, 10));
        assertEquals(0, DailyRules.streakGems(8, 7, 10));
        assertEquals(10, DailyRules.streakGems(14, 7, 10));
        assertEquals(0, DailyRules.streakGems(7, 0, 10));
        assertEquals(1, DailyRules.untilGems(6, 7));
        assertEquals(7, DailyRules.untilGems(7, 7));
        assertEquals(7, DailyRules.untilGems(0, 7));
    }

    @Test
    void claimedUpToAndNextDayForTheMenu() {
        DailyRules.State s = new DailyRules.State(5, 100, 5, 5, 0, -1, 5);
        assertEquals(5, DailyRules.claimedUpTo(s, 100, LEN));
        assertEquals(5, DailyRules.claimedUpTo(s, 103, LEN));
        assertEquals(6, DailyRules.nextDay(s, LEN));
        assertEquals(1, DailyRules.nextDay(DailyRules.State.NONE, LEN));
    }
}
