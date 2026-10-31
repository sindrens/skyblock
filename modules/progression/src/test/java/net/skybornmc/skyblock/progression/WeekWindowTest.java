package net.skybornmc.skyblock.progression;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Weekly board window (M5.7): Monday 00:00 Europe/Oslo, also across daylight saving time changes. */
class WeekWindowTest {

    private static final ZoneId OSLO = ZoneId.of("Europe/Oslo");
    private static final long HOUR = 3_600_000L;

    private static long oslo(int y, int m, int d, int h, int min) {
        return LocalDateTime.of(y, m, d, h, min).atZone(OSLO).toInstant().toEpochMilli();
    }

    private static int monday(int y, int m, int d) {
        return (int) LocalDate.of(y, m, d).toEpochDay();
    }

    @Test
    void sundayNightBelongsToTheOldWeekMondayMidnightToTheNew() {
        // Monday 2 Nov 2026
        assertEquals(monday(2026, 10, 26), WeekWindow.weekOf(oslo(2026, 11, 1, 23, 59), OSLO));
        assertEquals(monday(2026, 11, 2), WeekWindow.weekOf(oslo(2026, 11, 2, 0, 0), OSLO));
        assertEquals(monday(2026, 11, 2), WeekWindow.weekOf(oslo(2026, 11, 8, 23, 59), OSLO));
    }

    @Test
    void usesOsloTimeNotUtc() {
        // Sunday 23:30 UTC = Monday 00:30 Oslo (winter time, UTC+1): already the new week in Oslo.
        long sundayLateUtc = LocalDateTime.of(2026, 11, 1, 23, 30).atZone(ZoneId.of("UTC")).toInstant().toEpochMilli();
        assertEquals(monday(2026, 11, 2), WeekWindow.weekOf(sundayLateUtc, OSLO));
        assertEquals(monday(2026, 10, 26), WeekWindow.weekOf(sundayLateUtc, ZoneId.of("UTC")));
    }

    @Test
    void springForwardWeekHas167Hours() {
        // Clocks go forward on Sunday 29 March 2026 (02:00 -> 03:00).
        int week = monday(2026, 3, 23);
        assertEquals(167 * HOUR, WeekWindow.endMillis(week, OSLO) - WeekWindow.startMillis(week, OSLO));
        assertEquals(week, WeekWindow.weekOf(oslo(2026, 3, 29, 3, 30), OSLO));
        assertEquals(week + 7, WeekWindow.weekOf(WeekWindow.endMillis(week, OSLO), OSLO));
        assertEquals(week, WeekWindow.weekOf(WeekWindow.endMillis(week, OSLO) - 1, OSLO));
    }

    @Test
    void fallBackWeekHas169Hours() {
        // Clocks go back on Sunday 25 October 2026 (03:00 -> 02:00).
        int week = monday(2026, 10, 19);
        assertEquals(169 * HOUR, WeekWindow.endMillis(week, OSLO) - WeekWindow.startMillis(week, OSLO));
        assertEquals(oslo(2026, 10, 26, 0, 0), WeekWindow.endMillis(week, OSLO));
    }

    @Test
    void normalWeekAndMillisLeft() {
        int week = monday(2026, 11, 2);
        assertEquals(168 * HOUR, WeekWindow.endMillis(week, OSLO) - WeekWindow.startMillis(week, OSLO));
        assertEquals(HOUR, WeekWindow.millisLeft(oslo(2026, 11, 8, 23, 0), OSLO));
        assertEquals(168 * HOUR, WeekWindow.millisLeft(oslo(2026, 11, 2, 0, 0), OSLO));
    }
}
