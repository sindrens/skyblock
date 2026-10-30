package net.skybornmc.skyblock.skills;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SkillCurveTest {

    private final SkillCurve curve = new SkillCurve(600, 1.2, 1.13, 35, 66);

    @Test
    void matchesTheBalanceSheet() {
        // docs/ECONOMY_BALANCE.md §3.4: total skill XP per mine tier unlock level.
        assertEquals(0, curve.totalFor(0));
        assertEquals(600, curve.totalFor(1), 1e-6);
        assertEquals(4_465, curve.totalFor(5), 0.5);
        assertEquals(15_575, curve.totalFor(10), 0.5);
        assertEquals(43_221, curve.totalFor(15), 1);
        assertEquals(112_013, curve.totalFor(20), 1);
        assertEquals(709_129, curve.totalFor(30), 1);
        assertEquals(1.8e6, curve.totalFor(35), 0.05e6);
        assertEquals(5.5e6, curve.totalFor(42), 0.05e6);
        assertEquals(16.1e6, curve.totalFor(50), 0.05e6);
        assertEquals(44.4e6, curve.totalFor(58), 0.05e6);
        assertEquals(119.5e6, curve.totalFor(66), 0.05e6);
    }

    @Test
    void stepsGrowEarlyThenLate() {
        assertEquals(600, curve.step(0), 1e-9);
        assertEquals(720, curve.step(1), 1e-9);
        assertEquals(curve.step(34) * 1.2, curve.step(35), 1e-6);
        assertEquals(curve.step(35) * 1.13, curve.step(36), 1e-6);
    }

    @Test
    void levelForBoundaries() {
        assertEquals(0, curve.levelFor(0));
        assertEquals(0, curve.levelFor(-5));
        assertEquals(0, curve.levelFor(Double.NaN));
        assertEquals(0, curve.levelFor(599.9));
        assertEquals(1, curve.levelFor(600));
        assertEquals(4, curve.levelFor(curve.totalFor(5) - 0.001));
        assertEquals(5, curve.levelFor(curve.totalFor(5)));
        assertEquals(66, curve.levelFor(curve.totalFor(66)));
        assertEquals(66, curve.levelFor(1e15), "capped at the max level");
        for (int level = 0; level <= 66; level++) {
            assertEquals(level, curve.levelFor(curve.totalFor(level) + 0.01), "level " + level);
        }
    }

    @Test
    void progressInsideALevel() {
        assertEquals(0.0, curve.progress(0), 1e-9);
        assertEquals(0.5, curve.progress(300), 1e-9);
        assertEquals(0.0, curve.progress(600), 1e-9);
        assertEquals(0.5, curve.progress(600 + 360), 1e-9);
        assertEquals(360, curve.intoLevel(960), 1e-9);
        assertEquals(1.0, curve.progress(curve.totalFor(66)), 1e-9, "max level shows full");
    }

    @Test
    void configurableCurve() {
        SkillCurve flat = new SkillCurve(100, 1.0, 1.0, 0, 10);
        assertEquals(1_000, flat.totalFor(10), 1e-9);
        assertEquals(1_000, flat.totalFor(50), 1e-9, "clamped to max");
        assertEquals(3, flat.levelFor(399));
        assertEquals(10, flat.maxLevel());
    }

    @Test
    void rejectsBrokenSettings() {
        assertThrows(IllegalArgumentException.class, () -> new SkillCurve(0, 1.2, 1.1, 35, 66));
        assertThrows(IllegalArgumentException.class, () -> new SkillCurve(600, 0.9, 1.1, 35, 66));
        assertThrows(IllegalArgumentException.class, () -> new SkillCurve(600, 1.2, 1.1, 35, 0));
        assertThrows(IllegalArgumentException.class, () -> new SkillCurve(Double.NaN, 1.2, 1.1, 35, 10));
    }

    @Test
    void ownerCurveTo150KeepsTierLevelsAndGrowsTwoPercentAfter66() {
        SkillCurve owner = new SkillCurve(600, 1.2, 1.13, 35, 1.02, 66, 150);
        assertEquals(150, owner.maxLevel());
        for (int level = 0; level <= 66; level++) {
            assertEquals(curve.totalFor(level), owner.totalFor(level), 1e-3, "tier unlock XP unchanged at level " + level);
        }
        assertEquals(owner.step(65) * 1.13, owner.step(66), 1e-3);
        assertEquals(owner.step(66) * 1.02, owner.step(67), 1e-3);
        assertEquals(owner.step(148) * 1.02, owner.step(149), 1e-3);
        assertEquals(3.47e9, owner.totalFor(150), 0.01e9);
        assertEquals(150, owner.levelFor(1e15));
        assertEquals(149, owner.levelFor(owner.totalFor(150) - 1));
        assertThrows(IllegalArgumentException.class, () -> new SkillCurve(600, 1.2, 1.13, 35, 1.02, 30, 150));
        assertThrows(IllegalArgumentException.class, () -> new SkillCurve(600, 1.2, 1.13, 35, 0.99, 66, 150));
    }
}
