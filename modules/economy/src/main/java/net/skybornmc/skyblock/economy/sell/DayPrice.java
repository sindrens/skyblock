package net.skybornmc.skyblock.economy.sell;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Dynamic prices v2 (docs/plans/DYNAMIC_PRICES_V2.md): what one island (or one player without an island) gets for an
 * item depends on how much of it they sold today. The first {@code free} items of the day pay 100%; after that every
 * {@code step} items pay {@code stepPercent} less, down to {@code floorPercent}. At the daily reset part of the excess
 * carries over. Pure, unit-tested.
 *
 * @param enabled      off = every item pays 100% (only the weekly drift and the Hot Item move prices)
 * @param free         items per item per day at the full price (3,000)
 * @param step         items per step after that (100)
 * @param stepPercent  percent lost per step (1)
 * @param floorPercent the lowest price in percent (60)
 * @param carryPercent the share of the excess over {@code free} that carries into the next day (50)
 * @param resetHour    the hour (server zone) the sales day starts (4)
 */
public record DayPrice(boolean enabled, long free, long step, double stepPercent, double floorPercent, double carryPercent,
                       int resetHour) {

    /** The plan's defaults (also the config defaults). */
    public static final DayPrice DEFAULTS = new DayPrice(true, 3_000, 100, 1, 60, 50, 4);

    /** Rolling over more days than this changes nothing (any count is down to 0 long before). */
    private static final int MAX_ROLL_DAYS = 64;

    public DayPrice {
        free = Math.max(0, free);
        step = Math.max(1, step);
        stepPercent = Math.max(0, Math.min(100, stepPercent));
        floorPercent = Math.max(1, Math.min(100, floorPercent));
        carryPercent = Math.max(0, Math.min(100, carryPercent));
        resetHour = Math.max(0, Math.min(23, resetHour));
    }

    /** The price factor of the item sold as number {@code index} today (0-based): 1.0 for the first {@code free}. */
    public double factor(long index) {
        if (!enabled || index < free) {
            return 1.0;
        }
        long steps = (index - free) / step + 1;
        double f = 1.0 - steps * stepPercent / 100.0;
        return Math.max(floorPercent / 100.0, f);
    }

    /** The sum of {@link #factor} for the items {@code start .. start + amount - 1} (what a sale of {@code amount} pays per base). */
    public double sum(long start, long amount) {
        if (amount <= 0) {
            return 0;
        }
        start = Math.max(0, start);
        if (!enabled) {
            return amount;
        }
        double floor = floorPercent / 100.0;
        double total = 0;
        long i = start;
        long end = start + amount;
        if (end < start) {
            end = Long.MAX_VALUE;
        }
        while (i < end) {
            double f = factor(i);
            if (f <= floor) {
                total += (double) (end - i) * floor; // at the floor: the rest is flat
                break;
            }
            // the next item where the factor changes
            long next = i < free ? free : free + ((i - free) / step + 1) * step;
            long upTo = Math.min(end, next);
            total += (double) (upTo - i) * f;
            i = upTo;
        }
        return total;
    }

    /** The average factor of a sale of {@code amount} items starting at {@code start} (1.0 for an empty sale). */
    public double average(long start, long amount) {
        return amount <= 0 ? factor(start) : sum(start, amount) / amount;
    }

    /** The count after the daily reset(s): {@code days} times, the excess over {@code free} times the carry share. */
    public long rollover(long count, long days) {
        if (days <= 0) {
            return Math.max(0, count);
        }
        long c = Math.max(0, count);
        for (int d = 0; d < Math.min(days, MAX_ROLL_DAYS) && c > 0; d++) {
            c = (long) Math.floor(Math.max(0, c - free) * carryPercent / 100.0);
        }
        return days > MAX_ROLL_DAYS ? 0 : c;
    }

    /** The sales day of a moment: the date (server zone) shifted back by the reset hour, as an epoch day. */
    public long day(long millis, ZoneId zone) {
        ZonedDateTime t = Instant.ofEpochMilli(millis).atZone(zone).minusHours(resetHour);
        return t.toLocalDate().toEpochDay();
    }

    /** "Price today" in whole percent for display, before the next item ({@code 100} below the free amount). */
    public int percent(long count) {
        return (int) Math.round(factor(Math.max(0, count)) * 100.0);
    }

    /** Items left at the full price today (0 when past it). */
    public long fullLeft(long count) {
        return enabled ? Math.max(0, free - Math.max(0, count)) : Long.MAX_VALUE;
    }
}
