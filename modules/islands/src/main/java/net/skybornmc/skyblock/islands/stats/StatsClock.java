package net.skybornmc.skyblock.islands.stats;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.function.LongSupplier;

/**
 * Day and week buckets of island stats (M2.6). A day is the local calendar day in the stats zone (Europe/Oslo by
 * default), so a day is 23 or 25 hours long on DST changes; a week starts on Monday 00:00 local time. Buckets are stored as
 * epoch days (the week by its Monday). {@link #today()} is O(1): the next midnight is cached and only recomputed when it
 * has passed. Thread-safe.
 */
public final class StatsClock {

    private final ZoneId zone;
    private final LongSupplier now;
    private volatile Cache cache = new Cache(Integer.MIN_VALUE, Long.MIN_VALUE, Long.MIN_VALUE);

    private record Cache(int day, long startMillis, long nextMillis) {
    }

    public StatsClock(ZoneId zone, LongSupplier now) {
        this.zone = zone;
        this.now = now;
    }

    public static StatsClock system(ZoneId zone) {
        return new StatsClock(zone, System::currentTimeMillis);
    }

    public ZoneId zone() {
        return zone;
    }

    /** Current epoch millis of this clock. */
    public long nowMillis() {
        return now.getAsLong();
    }

    /** The current day bucket. */
    public int today() {
        long t = now.getAsLong();
        Cache c = cache;
        if (t >= c.startMillis && t < c.nextMillis) {
            return c.day;
        }
        int day = day(t, zone);
        cache = new Cache(day, startOfDay(day, zone), startOfDay(day + 1, zone));
        return day;
    }

    /** The current week bucket (epoch day of this week's Monday). */
    public int weekStart() {
        return weekStart(today());
    }

    /** The day bucket of an instant in {@code zone}. */
    public static int day(long epochMillis, ZoneId zone) {
        return (int) Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate().toEpochDay();
    }

    /** The Monday of the week that contains {@code day} (ISO weeks). */
    public static int weekStart(int day) {
        int dow = LocalDate.ofEpochDay(day).getDayOfWeek().getValue(); // Monday = 1 … Sunday = 7
        return day - (dow - DayOfWeek.MONDAY.getValue());
    }

    /** Local midnight at the start of {@code day} as epoch millis (DST-aware). */
    public static long startOfDay(int day, ZoneId zone) {
        return LocalDate.ofEpochDay(day).atStartOfDay(zone).toInstant().toEpochMilli();
    }

    /** Length of a day bucket in hours (23, 24 or 25 in Europe/Oslo). */
    public static double hoursIn(int day, ZoneId zone) {
        return (startOfDay(day + 1, zone) - startOfDay(day, zone)) / 3_600_000.0;
    }
}
