package net.skybornmc.skyblock.enchants;

import org.junit.jupiter.api.Test;

import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Gathering numbers: family caps on chance effects, area yields and the area budget. */
class GatherMathTest {

    @Test
    void chanceEffectsStayInsideTheFamilyCap() {
        // Geode Sense IV: 0.4% chance x 40 = +16% crystals
        assertEquals(0.4, GatherMath.cappedChance(0.4, 16, 0, 200), 1e-12);
        assertEquals(0.4, GatherMath.cappedChance(0.4, 16, 184, 200), 1e-12);
        assertEquals(0.2, GatherMath.cappedChance(0.4, 16, 192, 200), 1e-12, "half the room left: half the chance");
        assertEquals(0, GatherMath.cappedChance(0.4, 16, 250, 200), 1e-12, "family full: nothing");
        assertEquals(0, GatherMath.cappedChance(0, 16, 0, 200), 1e-12);
        assertEquals(5, GatherMath.cappedChance(5, 0, 500, 200), 1e-12, "no family value: unchanged");
    }

    @Test
    void randomRoundKeepsTheExpectedValue() {
        SplittableRandom random = new SplittableRandom(1);
        long sum = 0;
        int n = 100_000;
        for (int i = 0; i < n; i++) {
            sum += GatherMath.randomRound(2.25, random);
        }
        assertEquals(2.25, sum / (double) n, 0.01);
        assertEquals(0, GatherMath.randomRound(-1, random));
        assertEquals(0, GatherMath.randomRound(Double.NaN, random));
        assertEquals(3, GatherMath.randomRound(3.0, random));
    }

    @Test
    void areaYieldAndActions() {
        SplittableRandom random = new SplittableRandom(2);
        assertEquals(6, GatherMath.areaYield(2, 50, 6, random), "Vein Rend III: 6 ores x 2 drops x 50%");
        assertEquals(0, GatherMath.areaYield(0, 50, 6, random));
        assertEquals(0, GatherMath.areaYield(2, 0, 6, random));
        assertEquals(3, GatherMath.areaActions(6, 50), 1e-9);
        assertEquals(6.5, GatherMath.areaActions(26, 25), 1e-9);
        assertEquals(0, GatherMath.areaActions(0, 25), 1e-9);
    }

    @Test
    void geodeWorth() {
        assertEquals(40, GatherMath.geodeCrystals(1.0, 40, 5));
        assertEquals(5, GatherMath.geodeCrystals(0.05, 40, 5), "at least the minimum");
        assertEquals(5, GatherMath.geodeCrystals(Double.NaN, 40, 5));
    }

    @Test
    void everyNthFires() {
        int c = 0;
        int fired = 0;
        for (int i = 0; i < 300; i++) {
            c = GatherMath.everyNth(c, 100);
            if (c < 0) {
                fired++;
                c = 0;
            }
        }
        assertEquals(3, fired);
        assertEquals(0, GatherMath.everyNth(5, 0));
    }

    @Test
    void budgetRefillsAndCaps() {
        GatherMath.Budget b = new GatherMath.Budget(80, 0);
        assertEquals(80, b.take(200, 0, 40, 80));
        assertEquals(0, b.take(10, 0, 40, 80));
        assertEquals(20, b.take(50, 500, 40, 80), "half a second refills 20");
        assertEquals(80, b.take(500, 100_000, 40, 80), "never above the capacity");
        assertEquals(0, b.take(0, 200_000, 40, 80));
        assertFalse(GatherMath.roll(0, new SplittableRandom(3)));
        assertTrue(GatherMath.roll(100, new SplittableRandom(3)));
    }
}
