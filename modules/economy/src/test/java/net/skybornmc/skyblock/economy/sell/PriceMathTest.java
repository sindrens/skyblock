package net.skybornmc.skyblock.economy.sell;

import net.skybornmc.skyblock.common.sell.Trend;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Price math (ECONOMY_BALANCE §8): sale value, splitting invariance, floor, recovery, drift, adaptive impact, trend. */
class PriceMathTest {

    private static final double EPS = 1e-9;

    @Test
    void saleFactorWithoutImpactIsAmountTimesMultiplier() {
        assertEquals(64 * 0.9, PriceMath.saleFactor(0.9, 0, 64, 0.4), EPS);
        assertEquals(0, PriceMath.saleFactor(0.9, 0.01, 0, 0.4), EPS);
    }

    @Test
    void priceDropsItemByItem() {
        // 3 items at m = 1.0, impact 0.1: 1.0 + 0.9 + 0.8
        assertEquals(2.7, PriceMath.saleFactor(1.0, 0.1, 3, 0.4), EPS);
        assertEquals(0.7, PriceMath.afterSale(1.0, 0.1, 3, 0.4, 1.5), EPS);
    }

    @Test
    void floorHoldsInsideOneSale() {
        // m = 0.6, impact 0.1, floor 0.4: 0.6 + 0.5 + 0.4 + 0.4 + 0.4
        assertEquals(2.3, PriceMath.saleFactor(0.6, 0.1, 5, 0.4), EPS);
        assertEquals(0.4, PriceMath.afterSale(0.6, 0.1, 5, 0.4, 1.5), EPS);
        // Already at the floor: every item at the floor.
        assertEquals(4.0, PriceMath.saleFactor(0.4, 0.1, 10, 0.4), EPS);
    }

    @Test
    void splittingASaleNeverPaysMore() {
        double m = 1.2;
        double impact = 0.0003;
        double whole = PriceMath.saleFactor(m, impact, 1000, 0.4);
        double first = PriceMath.saleFactor(m, impact, 400, 0.4);
        double after = PriceMath.afterSale(m, impact, 400, 0.4, 1.5);
        double second = PriceMath.saleFactor(after, impact, 600, 0.4);
        assertEquals(whole, first + second, 1e-6);
        // Also across the floor.
        double big = PriceMath.saleFactor(0.5, 0.001, 300, 0.4);
        double a = PriceMath.saleFactor(0.5, 0.001, 50, 0.4);
        double b = PriceMath.saleFactor(PriceMath.afterSale(0.5, 0.001, 50, 0.4, 1.5), 0.001, 250, 0.4);
        assertEquals(big, a + b, 1e-6);
    }

    @Test
    void lineValueAndRounding() {
        assertEquals(64 * 20 * 1.25, PriceMath.lineValue(20, 1.25, 1.0, 0, 64, 0.4), EPS);
        assertEquals(0, PriceMath.lineValue(0, 1, 1, 0, 64, 0.4), EPS);
        assertEquals(12, PriceMath.roundDown(12.999));
        assertEquals(13, PriceMath.roundDown(12.9999999999)); // float noise counts as the whole number
        assertEquals(0, PriceMath.roundDown(-5));
        assertEquals(0, PriceMath.roundDown(Double.NaN));
        assertEquals(Long.MAX_VALUE, PriceMath.roundDown(1e30));
    }

    @Test
    void recoveryClosesTwoPercentOfTheGap() {
        double m = PriceMath.step(0.5, 0.02, false, 0, 0.4, 1.5);
        assertEquals(1 - 0.5 * 0.98, m, EPS);
        // Half-life ~5.7 h at 10 min steps: 34 steps ≈ half the gap.
        double x = 0.5;
        for (int i = 0; i < 34; i++) {
            x = PriceMath.step(x, 0.02, false, 0, 0.4, 1.5);
        }
        assertEquals(0.75, x, 0.01);
    }

    @Test
    void aboveBaseSettlesWhileSellingAndDriftsWhenQuiet() {
        assertEquals(1 + 0.4 * 0.98, PriceMath.step(1.4, 0.02, false, 0.001, 0.4, 1.5), EPS);
        assertEquals(1.401, PriceMath.step(1.4, 0.02, true, 0.001, 0.4, 1.5), EPS);
        // Below base, quiet: recovers first (never drifts below base).
        assertEquals(1 - 0.2 * 0.98, PriceMath.step(0.8, 0.02, true, 0.001, 0.4, 1.5), EPS);
        // Near base snaps to base, so a recovered item can start drifting.
        assertEquals(1.0, PriceMath.step(0.99995, 0.02, true, 0.001, 0.4, 1.5), EPS);
        // Ceiling holds.
        assertEquals(1.5, PriceMath.step(1.4999, 0.02, true, 0.01, 0.4, 1.5), EPS);
    }

    @Test
    void driftReachesTenPercentAfterOneQuietDay() {
        double step = PriceMath.driftStep(10, 10);
        double m = 1.0;
        for (int i = 0; i < 144; i++) {
            m = PriceMath.step(m, 0.02, true, step, 0.4, 1.5);
        }
        assertEquals(1.10, m, 1e-9);
        assertEquals(0, PriceMath.driftStep(10, 0), EPS);
    }

    @Test
    void adaptiveImpactMatchesTheBalanceSheet() {
        // ECONOMY_BALANCE §8: coal, 1,909 drops per player per hour, 10 players → 0.95 ppm.
        assertEquals(0.1212, PriceMath.recoveryPerHour(0.02, 10), 0.0001);
        long volume24h = 1_909L * 10 * 24;
        double impact = PriceMath.adaptiveImpact(volume24h, 1000, 0.02, 10, 0.85, 1e-6, 1e-8, 1e-4);
        assertEquals(0.95e-6, impact, 0.01e-6);
        // Equilibrium: at that volume and impact the price settles at the target.
        double perHour = volume24h / 24.0;
        double gap = impact * perHour / PriceMath.recoveryPerHour(0.02, 10);
        assertEquals(0.15, gap, 1e-9);
    }

    @Test
    void adaptiveImpactFallsBackAndClamps() {
        assertEquals(1e-6, PriceMath.adaptiveImpact(0, 1000, 0.02, 10, 0.85, 1e-6, 1e-8, 1e-4), EPS);
        assertEquals(1e-6, PriceMath.adaptiveImpact(999, 1000, 0.02, 10, 0.85, 1e-6, 1e-8, 1e-4), EPS);
        // Very little volume → huge impact, clamped to the max.
        assertEquals(1e-4, PriceMath.adaptiveImpact(1000, 0, 0.02, 10, 0.85, 1e-6, 1e-8, 1e-4), EPS);
        // Enormous volume → tiny impact, clamped to the min.
        assertEquals(1e-8, PriceMath.adaptiveImpact(Long.MAX_VALUE / 2, 0, 0.02, 10, 0.85, 1e-6, 1e-8, 1e-4), EPS);
    }

    @Test
    void trendNeedsTheThreshold() {
        assertEquals(Trend.RISING, PriceMath.trend(1.01, 1.0, 0.5));
        assertEquals(Trend.FALLING, PriceMath.trend(0.98, 1.0, 0.5));
        assertEquals(Trend.STEADY, PriceMath.trend(1.004, 1.0, 0.5));
        assertEquals(Trend.STEADY, PriceMath.trend(1.0, 1.0, 0));
    }

    @Test
    void clampHandlesNaN() {
        assertEquals(1.0, PriceMath.clamp(Double.NaN, 0.4, 1.5), EPS);
        assertTrue(PriceMath.clamp(-3, 0.4, 1.5) == 0.4);
    }
}
