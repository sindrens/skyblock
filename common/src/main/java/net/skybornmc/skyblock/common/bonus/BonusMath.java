package net.skybornmc.skyblock.common.bonus;

import java.util.Collection;

/** Pure family math (unit-tested): values add up, then the cap applies once. Never negative below -100. */
public final class BonusMath {

    private BonusMath() {
    }

    /** Sum of the values; NaN and infinite values are ignored. */
    public static double sum(Collection<Double> values) {
        double total = 0;
        for (Double v : values) {
            if (v != null && Double.isFinite(v)) {
                total += v;
            }
        }
        return total;
    }

    /** {@code min(cap, total)}, never below -100; a negative or broken cap counts as 0. */
    public static double capped(double total, double cap) {
        double c = Double.isFinite(cap) ? Math.max(0, cap) : 0;
        double t = Double.isFinite(total) ? total : 0;
        return Math.max(-100, Math.min(c, t));
    }

    /** Whether the cap cut the total. */
    public static boolean isCapped(double total, double cap) {
        return Double.isFinite(total) && capped(total, cap) < total;
    }

    /** {@code 1 + capped / 100}. */
    public static double multiplier(double total, double cap) {
        return 1 + capped(total, cap) / 100.0;
    }
}
