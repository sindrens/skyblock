package net.skybornmc.skyblock.progression;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

/**
 * The week of the weekly island boards (PHASE-5 M5.7): Monday 00:00 to the next Monday 00:00 in the board time zone
 * (default Europe/Oslo, same weeks as island stats). A week is named by the epoch day of its Monday (the
 * {@code week_start} column). Pure, unit-tested; daylight saving time changes make a week 167 or 169 hours long.
 */
final class WeekWindow {

    private WeekWindow() {
    }

    /** The week {@code nowMillis} falls in: epoch day of its Monday in {@code zone}. */
    static int weekOf(long nowMillis, ZoneId zone) {
        LocalDate day = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate();
        return (int) day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toEpochDay();
    }

    /** When the week starts: its Monday 00:00 in {@code zone}, as epoch millis. */
    static long startMillis(int weekStart, ZoneId zone) {
        return LocalDate.ofEpochDay(weekStart).atStartOfDay(zone).toInstant().toEpochMilli();
    }

    /** When the week ends (the next Monday 00:00), as epoch millis. */
    static long endMillis(int weekStart, ZoneId zone) {
        return startMillis(weekStart + 7, zone);
    }

    /** Milliseconds left in the week {@code nowMillis} falls in (never negative). */
    static long millisLeft(long nowMillis, ZoneId zone) {
        return Math.max(0, endMillis(weekOf(nowMillis, zone), zone) - nowMillis);
    }
}
