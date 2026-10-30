package net.skybornmc.skyblock.wardrobe;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The level curve (WARDROBE §3.2, WD14/WD15), the multiplier and the tome cap (WD13), against the plan's table. */
class SetLevelsTest {

    private static final SetLevels L = new WardrobeConfig().levels();

    @Test
    void defaultsAreThePlansCurve() {
        assertEquals(200, L.baseXp());
        assertEquals(1.10, L.growth(), 1e-12);
        assertEquals(50, L.maxLevel());
        assertEquals(1.5, L.mergedFactor(), 1e-12);
    }

    @Test
    void everyRowOfTheTable() {
        // level, XP for this level (step into it), total XP (plan §3.2)
        int[][] rows = {{1, 200, 200}, {5, 293, 1221}, {10, 472, 3187}, {15, 759, 6354}, {20, 1223, 11455}, {25, 1970, 19669},
                {30, 3173, 32899}, {35, 5110, 54205}, {40, 8229, 88519}, {45, 13253, 143781}, {50, 21344, 232782}};
        for (int[] row : rows) {
            assertEquals(row[1], Math.round(L.step(row[0] - 1, false)), "step into level " + row[0]);
            assertEquals(row[2], Math.round(L.total(row[0], false)), "total for level " + row[0]);
        }
    }

    @Test
    void mergedSetsNeedOneAndAHalfTimesTheXp() {
        assertEquals(349_173, Math.round(L.total(50, true)));
        assertEquals(300, Math.round(L.step(0, true)));
        assertEquals(L.total(25, false) * 1.5, L.total(25, true), 1e-6);
    }

    @Test
    void levelFollowsTheTotal() {
        assertEquals(0, L.level(0, false));
        assertEquals(0, L.level(-5, false));
        assertEquals(0, L.level(199.99, false));
        assertEquals(1, L.level(200, false));
        assertEquals(24, L.level(L.total(25, false) - 0.01, false));
        assertEquals(25, L.level(L.total(25, false), false), "a total stored exactly at the start of a level counts");
        assertEquals(50, L.level(L.total(50, false), false));
        assertEquals(50, L.level(1e12, false));
        assertEquals(33, L.level(L.total(33, true) + 1, true));
        assertEquals(28, L.level(L.total(33, false) + 1, true), "merged: the same XP is a lower level");
    }

    @Test
    void xpPastTheMaxIsNotKept() {
        assertEquals(L.total(50, false), L.clamp(1e12, false), 1e-6);
        assertEquals(0, L.clamp(-3, false));
        assertEquals(0, L.clamp(Double.NaN, false));
    }

    @Test
    void progressAndNext() {
        double half = L.total(10, false) + L.step(10, false) / 2;
        assertEquals(0.5, L.progress(half, false), 1e-9);
        assertEquals(L.step(10, false) / 2, L.toNext(half, false), 1e-6);
        assertEquals(1, L.progress(L.total(50, false), false));
        assertEquals(0, L.toNext(L.total(50, false), false));
        assertEquals(0, L.progress(0, false));
    }

    @Test
    void multiplierIsLinear() {
        assertEquals(0, SetBonuses.multiplier(0, true, 1, 50, 2), 1e-12);
        assertEquals(0.04, SetBonuses.multiplier(1, true, 1, 50, 2), 1e-12);
        assertEquals(1.0, SetBonuses.multiplier(25, true, 1, 50, 2), 1e-12);
        assertEquals(1.2, SetBonuses.multiplier(30, true, 1, 50, 2), 1e-12);
        assertEquals(2.0, SetBonuses.multiplier(50, true, 1, 50, 2), 1e-12);
        assertEquals(2.0, SetBonuses.multiplier(99, true, 1, 50, 2), 1e-12, "clamped at the max");
        assertEquals(1.0, SetBonuses.multiplier(0, false, 1, 50, 2), 1e-12, "levels off: the fixed value");
    }

    @Test
    void tomesStopAtLevelForty() {
        double at39 = L.total(39, false) + 10;
        double after = L.tome(at39, 2_500, 40, false);
        assertEquals(L.total(39, false) + 2_510, after, 1e-6);
        assertEquals(39, L.level(after, false));
        double near = L.total(40, false) - 100;
        assertEquals(L.total(40, false), L.tome(near, 10_000, 40, false), 1e-6, "capped at the start of level 40");
        assertEquals(40, L.level(L.tome(near, 10_000, 40, false), false));
        assertTrue(L.tomeUsable(near, 40, false));
        assertFalse(L.tomeUsable(L.total(40, false), 40, false));
        double at45 = L.total(45, false);
        assertEquals(at45, L.tome(at45, 2_500, 40, false), 1e-6, "never lowers the XP");
        assertEquals(L.total(10, false), L.tome(L.total(10, false), 0, 40, false), 1e-6);
    }

    @Test
    void brokenValuesFallBackSafely() {
        SetLevels broken = new SetLevels(-1, 0.5, 0, -2);
        assertEquals(1, broken.maxLevel());
        assertEquals(1, broken.step(0, true), 1e-12);
        assertEquals(1, broken.level(5, false));
    }
}
