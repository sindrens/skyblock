package net.skybornmc.skyblock.quests;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** RX19: daily reset 00:00 Europe/Oslo, weekly Monday 00:00, also on DST days (23 and 25 hour days). */
class QuestWindowsTest {

    private static final ZoneId OSLO = ZoneId.of("Europe/Oslo");

    private static long at(String localDateTime) {
        return LocalDateTime.parse(localDateTime).atZone(OSLO).toInstant().toEpochMilli();
    }

    private static int day(String date) {
        return (int) LocalDate.parse(date).toEpochDay();
    }

    @Test
    void dayStartsAtMidnightOslo() {
        assertEquals(day("2026-10-31"), QuestWindows.day(at("2026-10-31T00:00:00"), OSLO));
        assertEquals(day("2026-10-30"), QuestWindows.day(at("2026-10-30T23:59:59"), OSLO));
        // 23:30 UTC on Oct 31 is already Nov 1 in Oslo (UTC+1 in winter).
        long utc = LocalDateTime.parse("2026-10-31T23:30:00").atZone(ZoneId.of("UTC")).toInstant().toEpochMilli();
        assertEquals(day("2026-11-01"), QuestWindows.day(utc, OSLO));
    }

    @Test
    void weekStartsOnMonday() {
        // 2026-10-26 is a Monday.
        assertEquals(day("2026-10-26"), QuestWindows.weekStart(at("2026-10-26T00:00:00"), OSLO));
        assertEquals(day("2026-10-26"), QuestWindows.weekStart(at("2026-11-01T23:59:00"), OSLO));
        assertEquals(day("2026-11-02"), QuestWindows.weekStart(at("2026-11-02T00:00:01"), OSLO));
        assertEquals(day("2026-10-19"), QuestWindows.weekStart(at("2026-10-25T23:59:59"), OSLO));
    }

    @Test
    void periodPerBoard() {
        long now = at("2026-10-31T12:00:00"); // a Saturday
        assertEquals(day("2026-10-31"), QuestWindows.period(QuestKind.DAILY, now, OSLO));
        assertEquals(day("2026-10-26"), QuestWindows.period(QuestKind.WEEKLY, now, OSLO));
        assertEquals(day("2026-10-26"), QuestWindows.period(QuestKind.ISLAND, now, OSLO));
    }

    @Test
    void resetOnTheDaySummerTimeEnds() {
        // 2026-10-25 has 25 hours in Oslo (clocks go back at 03:00).
        assertEquals(at("2026-10-26T00:00:00"), QuestWindows.nextReset(QuestKind.DAILY, at("2026-10-25T01:00:00"), OSLO));
        assertEquals(Duration.ofHours(25).toMillis(), QuestWindows.untilReset(QuestKind.DAILY, at("2026-10-25T00:00:00"), OSLO));
    }

    @Test
    void resetOnTheDaySummerTimeStarts() {
        // 2026-03-29 has 23 hours in Oslo (clocks go forward at 02:00).
        assertEquals(Duration.ofHours(23).toMillis(), QuestWindows.untilReset(QuestKind.DAILY, at("2026-03-29T00:00:00"), OSLO));
        assertEquals(day("2026-03-29"), QuestWindows.day(at("2026-03-29T23:30:00"), OSLO));
        assertNotEquals(QuestWindows.day(at("2026-03-29T23:30:00"), OSLO), QuestWindows.day(at("2026-03-30T00:00:00"), OSLO));
    }

    @Test
    void weeklyResetIsNextMondayMidnight() {
        assertEquals(at("2026-11-02T00:00:00"), QuestWindows.nextReset(QuestKind.WEEKLY, at("2026-10-31T12:00:00"), OSLO));
        // The week with the DST change is 7 days + 1 hour long; the reset is still Monday 00:00.
        assertEquals(at("2026-10-26T00:00:00"), QuestWindows.nextReset(QuestKind.WEEKLY, at("2026-10-19T00:00:00"), OSLO));
        assertEquals(Duration.ofDays(7).plusHours(1).toMillis(), QuestWindows.untilReset(QuestKind.WEEKLY, at("2026-10-19T00:00:00"), OSLO));
    }
}
