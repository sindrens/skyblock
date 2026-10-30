package net.skybornmc.skyblock.economy.sell;

import net.skybornmc.skyblock.common.sell.Trend;

/**
 * The dynamic price formulas of M3.4 (PHASE-3 §M3.4, ECONOMY_BALANCE §8). Pure, unit-tested; no server needed.
 *
 * <ul>
 *   <li>Every item has a multiplier {@code m} between the floor (0.40) and the ceiling (1.50); the price is
 *       {@code base × m} (× the Hot Item factor).</li>
 *   <li>Every item sold lowers {@code m} by {@code impact}. Inside one sale the price drops item by item, so selling 100 at
 *       once pays exactly the same as selling 50 + 50 (no gain from splitting or bundling).</li>
 *   <li>Every interval (10 min) the gap to base closes by {@code recovery} (2%): {@code m ← 1 − (1 − m) × 0.98}. Above
 *       base the same pull brings {@code m} back down while the item is being sold.</li>
 *   <li>Items nobody sold for {@code quiet} minutes drift up toward the ceiling ({@code drift} per day, +10%).</li>
 *   <li>{@code impact} adapts to volume (ECONOMY_BALANCE §8): at the normal 24 h volume the price settles at the target
 *       (0.85), whether 30 or 300 players sell.</li>
 * </ul>
 */
public final class PriceMath {

    /** Below this distance to base the multiplier snaps to exactly 1.0 (so recovered items can start to drift up). */
    static final double SNAP = 1e-4;

    private PriceMath() {
    }

    /** {@code value} kept between floor and ceiling (NaN counts as base). */
    public static double clamp(double value, double floor, double ceiling) {
        if (Double.isNaN(value)) {
            return Math.max(floor, Math.min(ceiling, 1.0));
        }
        return Math.max(floor, Math.min(ceiling, value));
    }

    /**
     * The value of selling {@code amount} items, in "base prices" (multiply by base and the hot factor for money):
     * the sum of {@code max(floor, m − impact × k)} for k = 0 .. amount − 1.
     */
    public static double saleFactor(double m, double impact, long amount, double floor) {
        if (amount <= 0) {
            return 0;
        }
        double start = Math.max(floor, m);
        if (impact <= 0 || start <= floor) {
            return start * amount;
        }
        // Items still above the floor: k < (start - floor) / impact.
        double room = (start - floor) / impact;
        long above = room >= amount ? amount : (long) Math.ceil(room);
        above = Math.max(0, Math.min(amount, above));
        double sum = above * start - impact * ((double) above * (above - 1) / 2.0);
        return sum + (amount - above) * floor;
    }

    /** The multiplier after selling {@code amount} items. */
    public static double afterSale(double m, double impact, long amount, double floor, double ceiling) {
        if (amount <= 0 || impact <= 0) {
            return clamp(m, floor, ceiling);
        }
        return clamp(m - impact * amount, floor, ceiling);
    }

    /** Money for a sale line: {@code base × hotFactor × saleFactor}, in money (fractional; round the subtotal once). */
    public static double lineValue(long base, double hotFactor, double m, double impact, long amount, double floor) {
        if (base <= 0 || amount <= 0) {
            return 0;
        }
        return base * hotFactor * saleFactor(m, impact, amount, floor);
    }

    /** A subtotal rounded down once, saturated at {@link Long#MAX_VALUE}. */
    public static long roundDown(double money) {
        if (!(money > 0)) {
            return 0;
        }
        if (money >= 9.0e18) {
            return Long.MAX_VALUE;
        }
        return (long) Math.floor(money + 1e-9);
    }

    /**
     * One interval step.
     *
     * @param m          multiplier now
     * @param recovery   part of the gap to base closed per step (0.02)
     * @param quiet      true when the item had no sale for the quiet time: above base it drifts up instead of settling
     * @param driftStep  multiplier added per step while quiet and at/above base
     */
    public static double step(double m, double recovery, boolean quiet, double driftStep, double floor, double ceiling) {
        double r = Math.max(0, Math.min(1, recovery));
        double next;
        if (m < 1.0) {
            next = 1.0 - (1.0 - m) * (1.0 - r);
            if (1.0 - next < SNAP) {
                next = 1.0;
            }
        } else if (!quiet) {
            next = 1.0 + (m - 1.0) * (1.0 - r);
            if (next - 1.0 < SNAP) {
                next = 1.0;
            }
        } else {
            next = m + Math.max(0, driftStep);
        }
        return clamp(next, floor, ceiling);
    }

    /** The drift per step: {@code percentPerDay} spread over the steps of one day. */
    public static double driftStep(double percentPerDay, int intervalMinutes) {
        if (intervalMinutes <= 0 || percentPerDay <= 0) {
            return 0;
        }
        return percentPerDay / 100.0 * intervalMinutes / 1440.0;
    }

    /** Continuous recovery rate per hour (ECONOMY_BALANCE §8: 0.1212 for 2% per 10 min). */
    public static double recoveryPerHour(double recovery, int intervalMinutes) {
        if (recovery <= 0 || recovery >= 1 || intervalMinutes <= 0) {
            return 0;
        }
        return -(60.0 / intervalMinutes) * Math.log(1.0 - recovery);
    }

    /**
     * The adaptive impact per item sold: the price settles at {@code target} when items keep selling at the last 24 h
     * rate. {@code 1 − m = impact × perHour / recoveryPerHour} ⇒ {@code impact = recoveryPerHour × (1 − target) / perHour}.
     *
     * @param volume24h items sold in the last 24 h
     * @param minVolume below this volume the default impact is used (too little data)
     * @return impact as a fraction of base per item (1 ppm = 0.000001), clamped to [min, max]
     */
    public static double adaptiveImpact(long volume24h, long minVolume, double recovery, int intervalMinutes, double target,
                                        double defaultImpact, double minImpact, double maxImpact) {
        double lo = Math.max(0, minImpact);
        double hi = Math.max(lo, maxImpact);
        if (volume24h <= 0 || volume24h < minVolume) {
            return Math.max(lo, Math.min(hi, defaultImpact));
        }
        double perHour = volume24h / 24.0;
        double gap = Math.max(0, 1.0 - target);
        double impact = recoveryPerHour(recovery, intervalMinutes) * gap / perHour;
        return Math.max(lo, Math.min(hi, impact));
    }

    /** Rising/falling when the multiplier moved at least {@code thresholdPercent} points since an hour ago. */
    public static Trend trend(double now, double hourAgo, double thresholdPercent) {
        double diff = (now - hourAgo) * 100.0;
        double t = Math.max(0, thresholdPercent);
        if (diff >= t && diff > 0) {
            return Trend.RISING;
        }
        if (diff <= -t && diff < 0) {
            return Trend.FALLING;
        }
        return Trend.STEADY;
    }
}
