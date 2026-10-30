package net.skybornmc.skyblock.common.economy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Owner decision #46: the total sell bonus is capped at +300%. */
class MultiplierCapTest {

    private static final double CAP = MultiplierCap.DEFAULT_CAP_PERCENT;

    @Test
    void defaultCapIsPlus300Percent() {
        assertEquals(300.0, CAP);
        assertEquals(4.0, MultiplierCap.multiplier(1_000, CAP), 1e-9);
    }

    @Test
    void bonusesAddUpBeforeTheCap() {
        double total = MultiplierCap.total(10, 25, 50, Double.NaN);
        assertEquals(85.0, total, 1e-9);
        assertEquals(1.85, MultiplierCap.multiplier(total, CAP), 1e-9);
        assertFalse(MultiplierCap.isCapped(total, CAP));
    }

    @Test
    void totalAboveTheCapIsCut() {
        double total = MultiplierCap.total(150, 120, 80); // 350
        assertEquals(300.0, MultiplierCap.cappedBonus(total, CAP), 1e-9);
        assertTrue(MultiplierCap.isCapped(total, CAP));
        assertEquals(400, MultiplierCap.apply(100, total, CAP));
        assertFalse(MultiplierCap.isCapped(300, CAP), "exactly at the cap is not cut");
    }

    @Test
    void capIsConfigurable() {
        assertEquals(200, MultiplierCap.apply(100, 350, 100));
        assertEquals(100, MultiplierCap.apply(100, 350, 0));
        assertEquals(100, MultiplierCap.apply(100, 350, -50), "a negative cap counts as 0");
        assertEquals(400, MultiplierCap.apply(100, 350, Double.NaN), "a broken cap falls back to the default");
    }

    @Test
    void penaltiesNeverGoBelowZero() {
        assertEquals(50, MultiplierCap.apply(100, -50, CAP));
        assertEquals(0, MultiplierCap.apply(100, -500, CAP));
        assertEquals(0, MultiplierCap.apply(-100, 50, CAP));
    }

    @Test
    void roundsDownAndNeverOverflows() {
        assertEquals(133, MultiplierCap.apply(99, 34.5, CAP)); // 133.155
        assertEquals(Long.MAX_VALUE, MultiplierCap.apply(Long.MAX_VALUE / 2, 300, CAP));
        assertEquals(Long.MAX_VALUE, MultiplierCap.apply(Long.MAX_VALUE, 0, CAP));
    }
}
