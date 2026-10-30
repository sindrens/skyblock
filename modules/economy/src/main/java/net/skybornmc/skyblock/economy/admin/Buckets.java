package net.skybornmc.skyblock.economy.admin;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Time buckets of the economy watch (M3.8). Sums are stored per UTC hour; days are the server's display days
 * (Europe/Oslo, {@code Format.zone()}). Every real time zone offset used here is a whole number of hours, so a day is
 * always made of whole hour buckets.
 */
public final class Buckets {

    public static final long HOUR_MS = 3_600_000L;

    private Buckets() {
    }

    /** The hour bucket of a time (epoch millis). */
    public static long hour(long millis) {
        return Math.floorDiv(millis, HOUR_MS);
    }

    /** Start of an hour bucket in epoch millis. */
    public static long start(long hour) {
        return hour * HOUR_MS;
    }

    /** The display day of an hour bucket. */
    public static LocalDate day(long hour, ZoneId zone) {
        return Instant.ofEpochMilli(start(hour)).atZone(zone).toLocalDate();
    }

    /** The first hour bucket of a display day. */
    public static long firstHour(LocalDate day, ZoneId zone) {
        return hour(day.atStartOfDay(zone).toInstant().toEpochMilli());
    }
}
