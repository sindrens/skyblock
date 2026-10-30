package net.skybornmc.skyblock.progression;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The pillar curve: base × growth^level per level, no cap, team-size damping (PHASE-5 M5.1). */
class PillarCurveTest {

    private final PillarCurve curve = new PillarCurve(250, 1.12);

    @Test
    void stepCostGrowsByTheFactor() {
        assertEquals(250, curve.stepCost(0), 1e-9);
        assertEquals(280, curve.stepCost(1), 1e-9);
        assertEquals(250 * Math.pow(1.12, 10), curve.stepCost(10), 1e-6);
    }

    @Test
    void totalIsTheSumOfTheSteps() {
        double sum = 0;
        for (int level = 0; level < 40; level++) {
            assertEquals(sum, curve.totalFor(level), 1e-6 * Math.max(1, sum));
            sum += curve.stepCost(level);
        }
        assertEquals(0, curve.totalFor(0));
        assertEquals(0, curve.totalFor(-5));
    }

    @Test
    void levelForExactBoundaries() {
        for (int level = 0; level <= 150; level++) {
            double total = curve.totalFor(level);
            assertEquals(level, curve.levelFor(total), "exact total of level " + level);
            if (level > 0) {
                assertEquals(level - 1, curve.levelFor(Math.nextDown(total)), "just under level " + level);
            }
        }
        assertEquals(0, curve.levelFor(0));
        assertEquals(0, curve.levelFor(-10));
        assertEquals(0, curve.levelFor(Double.NaN));
        assertEquals(PillarCurve.MAX_LEVEL, curve.levelFor(Double.POSITIVE_INFINITY));
    }

    @Test
    void noLevelCap() {
        assertTrue(curve.levelFor(1e15) > 150);
        assertTrue(curve.levelFor(1e300) <= PillarCurve.MAX_LEVEL);
    }

    @Test
    void progressInsideTheLevel() {
        double from = curve.totalFor(5);
        double to = curve.totalFor(6);
        assertEquals(0.0, curve.progress(from), 1e-9);
        assertEquals(0.5, curve.progress((from + to) / 2), 1e-9);
        assertEquals(0.0, curve.progress(0), 1e-9);
    }

    @Test
    void paceTargetsOfTheDefaultBase() {
        // ECONOMY_BALANCE skill pace (~1-1.5 base XP per Depths block early): pillar 10 ≈ 4.4k XP, about 1-2 h of one activity.
        assertEquals(4387, curve.totalFor(10), 1);
        assertTrue(curve.totalFor(25) > 30_000 && curve.totalFor(25) < 40_000);
        assertTrue(curve.totalFor(60) > 1_500_000 && curve.totalFor(60) < 2_500_000);
    }

    @Test
    void teamSizeIsDampedByTheCurve() {
        // 5 members earning 5x the XP of a solo player gain only ln5/ln1.12 ≈ 14.2 levels more (plan M5.1).
        assertEquals(Math.log(5) / Math.log(1.12), curve.extraLevelsFor(5), 1e-9);
        assertEquals(14.2, curve.extraLevelsFor(5), 0.05);
        for (int solo : new int[]{20, 40, 60}) {
            int team = curve.levelFor(5 * curve.totalFor(solo));
            assertTrue(team - solo >= 13 && team - solo <= 15, "solo " + solo + " → team " + team);
        }
        assertEquals(0, curve.extraLevelsFor(1));
        assertEquals(0, curve.extraLevelsFor(0.5));
    }

    @Test
    void flatCurveAndBadInput() {
        PillarCurve flat = new PillarCurve(100, 1.0);
        assertEquals(1000, flat.totalFor(10), 1e-9);
        assertEquals(10, flat.levelFor(1000));
        assertEquals(9, flat.levelFor(999.9));
        assertEquals(Double.POSITIVE_INFINITY, flat.extraLevelsFor(5));
        PillarCurve broken = new PillarCurve(-1, 0.5);
        assertEquals(1, broken.base());
        assertEquals(1, broken.growth());
    }
}
