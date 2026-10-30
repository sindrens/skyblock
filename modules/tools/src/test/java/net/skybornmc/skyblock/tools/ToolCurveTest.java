package net.skybornmc.skyblock.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Tool XP curve ×1.21 per level (ECONOMY_BALANCE §3.5, owner-approved). */
class ToolCurveTest {

    private final ToolCurve curve = new ToolCurve(400, 1.21, 50);

    @Test
    void totalsMatchTheBalanceSheet() {
        assertEquals(0, curve.xpForLevel(1));
        assertEquals(400, curve.xpForLevel(2), 1e-9);
        assertEquals(2_178, curve.xpForLevel(5), 1);          // slot 1
        assertEquals(25_564, curve.xpForLevel(15), 1);        // slot 2
        assertEquals(477_405, curve.xpForLevel(30), 1);       // slot 3
        assertEquals(8.4e6, curve.xpForLevel(45), 0.05e6);    // slot 4
        assertEquals(21.7e6, curve.xpForLevel(50), 0.05e6);   // max / prestige
        assertEquals(curve.xpForLevel(50), curve.maxXp());
    }

    @Test
    void eachStepCosts121PercentOfThePrevious() {
        for (int level = 1; level < 49; level++) {
            assertEquals(1.21, curve.step(level + 1) / curve.step(level), 1e-9);
        }
        assertEquals(400 * Math.pow(1.21, 9), curve.step(10), 1e-6);
    }

    @Test
    void levelForXp() {
        assertEquals(1, curve.levelFor(0));
        assertEquals(1, curve.levelFor(-5));
        assertEquals(1, curve.levelFor(399.9));
        assertEquals(2, curve.levelFor(400));
        assertEquals(4, curve.levelFor(curve.xpForLevel(5) - 0.5));
        assertEquals(5, curve.levelFor(curve.xpForLevel(5)));
        assertEquals(50, curve.levelFor(curve.maxXp()));
        assertEquals(50, curve.levelFor(curve.maxXp() * 10), "capped at the max level");
    }

    @Test
    void progressInsideALevel() {
        double half = curve.xpForLevel(10) + curve.step(10) / 2;
        assertEquals(0.5, curve.progress(half), 1e-9);
        assertEquals(curve.step(10) / 2, curve.intoLevel(half), 1e-6);
        assertEquals(1.0, curve.progress(curve.maxXp()));
    }

    @Test
    void flatCurveAndValidation() {
        ToolCurve flat = new ToolCurve(100, 1.0, 5);
        assertEquals(400, flat.xpForLevel(5), 1e-9);
        assertEquals(3, flat.levelFor(250));
        assertThrows(IllegalArgumentException.class, () -> new ToolCurve(0, 1.2, 50));
        assertThrows(IllegalArgumentException.class, () -> new ToolCurve(400, 0.9, 50));
    }
}
