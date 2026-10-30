package net.skybornmc.skyblock.islands.stats;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Day/week buckets in Europe/Oslo, across both DST changes of 2026 (29 March: 23 h, 25 October: 25 h). */
class StatsClockTest {

    private static final ZoneId OSLO = ZoneId.of("Europe/Oslo");

    private static long at(String isoLocal) {
        return ZonedDateTime.of(java.time.LocalDateTime.parse(isoLocal), OSLO).toInstant().toEpochMilli();
    }

    private static int day(String date) {
        return (int) LocalDate.parse(date).toEpochDay();
    }

    @Test
    void dayIsTheLocalCalendarDay() {
        assertEquals(day("2026-10-30"), StatsClock.day(at("2026-10-30T00:00:00"), OSLO));
        assertEquals(day("2026-10-29"), StatsClock.day(at("2026-10-29T23:59:59"), OSLO));
        // 23:30 UTC on the 29th is already the 30th in Oslo (CET, +1)
        assertEquals(day("2026-10-30"), StatsClock.day(java.time.Instant.parse("2026-10-29T23:30:00Z").toEpochMilli(), OSLO));
    }

    @Test
    void dstDaysAre23And25Hours() {
        assertEquals(23.0, StatsClock.hoursIn(day("2026-03-29"), OSLO));
        assertEquals(25.0, StatsClock.hoursIn(day("2026-10-25"), OSLO));
        assertEquals(24.0, StatsClock.hoursIn(day("2026-10-26"), OSLO));
    }

    @Test
    void dstNightsStayInTheirDay() {
        // spring: 02:00 jumps to 03:00; 01:59 and 03:00 are the same day
        assertEquals(day("2026-03-29"), StatsClock.day(at("2026-03-29T01:59:00"), OSLO));
        assertEquals(day("2026-03-29"), StatsClock.day(at("2026-03-29T03:00:00"), OSLO));
        assertEquals(day("2026-03-29"), StatsClock.day(at("2026-03-29T23:59:59"), OSLO));
        assertEquals(day("2026-03-30"), StatsClock.day(at("2026-03-30T00:00:00"), OSLO));
        // autumn: 02:00-03:00 happens twice; both are the 25th
        long firstTwoThirty = java.time.Instant.parse("2026-10-25T00:30:00Z").toEpochMilli(); // 02:30 CEST
        long secondTwoThirty = java.time.Instant.parse("2026-10-25T01:30:00Z").toEpochMilli(); // 02:30 CET
        assertEquals(day("2026-10-25"), StatsClock.day(firstTwoThirty, OSLO));
        assertEquals(day("2026-10-25"), StatsClock.day(secondTwoThirty, OSLO));
        assertEquals(day("2026-10-25"), StatsClock.day(java.time.Instant.parse("2026-10-25T22:59:59Z").toEpochMilli(), OSLO));
        assertEquals(day("2026-10-26"), StatsClock.day(java.time.Instant.parse("2026-10-25T23:00:00Z").toEpochMilli(), OSLO));
    }

    @Test
    void weeksStartOnMonday() {
        int monday = day("2026-10-26");
        for (int i = 0; i < 7; i++) {
            assertEquals(monday, StatsClock.weekStart(monday + i));
        }
        assertEquals(monday - 7, StatsClock.weekStart(monday - 1)); // Sunday 25 Oct (the 25-hour day) is last week
        assertEquals(day("2026-03-23"), StatsClock.weekStart(day("2026-03-29")));
        assertEquals(day("2025-12-29"), StatsClock.weekStart(day("2026-01-01"))); // across the new year
        assertEquals(day("1969-12-29"), StatsClock.weekStart(0)); // epoch day 0 is a Thursday
    }

    @Test
    void cachedTodayRollsOverExactlyAtLocalMidnight() {
        AtomicLong now = new AtomicLong(at("2026-10-25T23:59:59.999"));
        StatsClock clock = new StatsClock(OSLO, now::get);
        assertEquals(day("2026-10-25"), clock.today());
        assertEquals(day("2026-10-19"), clock.weekStart());
        now.set(at("2026-10-26T00:00:00"));
        assertEquals(day("2026-10-26"), clock.today());
        assertEquals(day("2026-10-26"), clock.weekStart());
        now.set(at("2026-03-28T23:59:59.999"));
        assertEquals(day("2026-03-28"), clock.today()); // going back in time recomputes too
        now.set(at("2026-03-29T00:00:00"));
        assertEquals(day("2026-03-29"), clock.today());
        now.set(at("2026-03-29T23:59:59.999")); // only 23 h later
        assertEquals(day("2026-03-29"), clock.today());
        now.set(at("2026-03-30T00:00:00"));
        assertEquals(day("2026-03-30"), clock.today());
    }

    @Test
    void retentionBounds() {
        int today = day("2026-10-30"); // Friday
        assertEquals(today - 34, StatsTracker.minDay(today, 35));
        assertEquals(today, StatsTracker.minDay(today, 1));
        assertEquals(day("2026-10-26"), StatsTracker.minWeek(today, 1));
        assertEquals(day("2026-10-19"), StatsTracker.minWeek(today, 2));
    }
}
