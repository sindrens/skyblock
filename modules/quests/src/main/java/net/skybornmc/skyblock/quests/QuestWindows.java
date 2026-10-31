package net.skybornmc.skyblock.quests;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

/**
 * Quest windows (RX19): a day starts at 00:00 in the server zone (Europe/Oslo), a week on Monday 00:00. Stored as epoch
 * days of the window's first day, so DST days (23 or 25 hours) never shift a reset. Pure, unit-tested.
 */
final class QuestWindows {

    private QuestWindows() {
    }

    /** Epoch day of {@code now} in {@code zone}. */
    static int day(long nowMillis, ZoneId zone) {
        return (int) date(nowMillis, zone).toEpochDay();
    }

    /** Epoch day of the Monday of the week that holds {@code now}. */
    static int weekStart(long nowMillis, ZoneId zone) {
        return (int) date(nowMillis, zone).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toEpochDay();
    }

    /** The window of a board that holds {@code now}. */
    static int period(QuestKind kind, long nowMillis, ZoneId zone) {
        return kind.weekly() ? weekStart(nowMillis, zone) : day(nowMillis, zone);
    }

    /** The moment the window that holds {@code now} ends (the next reset), epoch millis. */
    static long nextReset(QuestKind kind, long nowMillis, ZoneId zone) {
        long start = period(kind, nowMillis, zone);
        LocalDate next = LocalDate.ofEpochDay(start + (kind.weekly() ? 7 : 1));
        return next.atStartOfDay(zone).toInstant().toEpochMilli();
    }

    /** Milliseconds until the next reset (at least 0). */
    static long untilReset(QuestKind kind, long nowMillis, ZoneId zone) {
        return Math.max(0, nextReset(kind, nowMillis, zone) - nowMillis);
    }

    private static LocalDate date(long nowMillis, ZoneId zone) {
        return Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate();
    }
}
