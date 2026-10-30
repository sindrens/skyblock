package net.skybornmc.skyblock.common.economy;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The global sell-multiplier cap (owner decision #46: at most <b>+300%</b>, reached only with the best of everything).
 * Every sell bonus (rank, Sell Multiplier upgrade, sets, boosters, events, O11 personal/global boosters …) is a percent
 * that is <b>added up</b> (balance sheet: {@code 1 + min(cap, sum)}), then capped once here. Sell code must always go
 * through this class so no combination can pass the cap. The cap itself is {@code economy.yml → sell.multiplier-cap-percent},
 * read with {@link EconomyService#sellBonusCapPercent()}.
 *
 * <p>Pure logic, unit-tested. Bonuses below -100% count as -100% (nothing is paid, never a negative price).
 */
public final class MultiplierCap {

    /** Owner decision #46: +300% (×4.0). */
    public static final double DEFAULT_CAP_PERCENT = 300.0;

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal MAX_LONG = BigDecimal.valueOf(Long.MAX_VALUE);

    private MultiplierCap() {
    }

    /** The sum of several bonus percents (e.g. 10, 25, 50 → 85). NaN/infinite parts are ignored. */
    public static double total(double... bonusPercents) {
        double sum = 0;
        for (double part : bonusPercents) {
            if (Double.isFinite(part)) {
                sum += part;
            }
        }
        return sum;
    }

    /** The total bonus after the cap, in percent: {@code min(cap, total)}, never below -100. A negative cap counts as 0. */
    public static double cappedBonus(double totalBonusPercent, double capPercent) {
        double cap = Double.isFinite(capPercent) ? Math.max(0, capPercent) : DEFAULT_CAP_PERCENT;
        double bonus = Double.isFinite(totalBonusPercent) ? totalBonusPercent : 0;
        return Math.max(-100, Math.min(cap, bonus));
    }

    /** The final multiplier: {@code 1 + cappedBonus / 100} (+300% → 4.0). */
    public static double multiplier(double totalBonusPercent, double capPercent) {
        return 1 + cappedBonus(totalBonusPercent, capPercent) / 100.0;
    }

    /** Whether the cap cut this total (for a "capped" hint in menus). */
    public static boolean isCapped(double totalBonusPercent, double capPercent) {
        return cappedBonus(totalBonusPercent, capPercent) < (Double.isFinite(totalBonusPercent) ? totalBonusPercent : 0);
    }

    /**
     * {@code base} with the capped bonus applied, rounded down to whole units and saturated at {@link Long#MAX_VALUE}
     * (never overflows). A negative base gives 0.
     */
    public static long apply(long base, double totalBonusPercent, double capPercent) {
        if (base <= 0) {
            return 0;
        }
        BigDecimal factor = BigDecimal.valueOf(cappedBonus(totalBonusPercent, capPercent)).add(HUNDRED);
        BigDecimal value = BigDecimal.valueOf(base).multiply(factor).divide(HUNDRED, 0, RoundingMode.FLOOR);
        return value.compareTo(MAX_LONG) >= 0 ? Long.MAX_VALUE : value.longValueExact();
    }
}
