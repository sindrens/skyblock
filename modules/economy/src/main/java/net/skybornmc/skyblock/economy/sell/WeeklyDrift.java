package net.skybornmc.skyblock.economy.sell;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.IsoFields;

/**
 * Dynamic prices v2: every Monday 00:00 (server zone) each item gets a price change for the week, between
 * {@code -maxPercent} and {@code +maxPercent} in {@code stepPercent} steps, the same for everyone. Worked out from the
 * week and the item key (nothing stored), so it is the same after a restart. Pure, unit-tested.
 */
public record WeeklyDrift(boolean enabled, double maxPercent, double stepPercent, long salt) {

    public static final WeeklyDrift DEFAULTS = new WeeklyDrift(true, 10, 2, 104_729);

    public WeeklyDrift {
        maxPercent = Math.max(0, Math.min(50, maxPercent));
        stepPercent = Math.max(0.1, stepPercent);
    }

    /** The week of a moment: ISO week-based year x 100 + ISO week (202645). */
    public static int week(long millis, ZoneId zone) {
        ZonedDateTime t = Instant.ofEpochMilli(millis).atZone(zone);
        return t.get(IsoFields.WEEK_BASED_YEAR) * 100 + t.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
    }

    /** This week's change of an item in percent ({@code -10 .. +10}, a multiple of the step); 0 when off. */
    public double percent(int week, String itemKey) {
        if (!enabled || maxPercent <= 0) {
            return 0;
        }
        int steps = (int) Math.floor(maxPercent / stepPercent + 1e-9);
        if (steps <= 0) {
            return 0;
        }
        long h = mix(salt ^ ((long) week * 0x9E3779B97F4A7C15L) ^ (itemKey == null ? 0 : itemKey.hashCode() * 0xC2B2AE3D27D4EB4FL));
        int pick = (int) Math.floorMod(h, 2L * steps + 1) - steps;
        return pick * stepPercent;
    }

    /** The factor of an item this week (0.90 .. 1.10). */
    public double factor(int week, String itemKey) {
        return 1.0 + percent(week, itemKey) / 100.0;
    }

    /** The highest factor any item can get (for the shop's no-profit rule). */
    public double maxFactor() {
        return enabled ? 1.0 + maxPercent / 100.0 : 1.0;
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
