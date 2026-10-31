package net.skybornmc.skyblock.production;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PHASE-7 M7.3: who gets Farming XP (PD11), planting gates (PD10), growth multipliers and Harvest Yield math. */
class FarmingRulesTest {

    private static final double EPS = 1e-9;

    private static FarmingRules.Harvest h(boolean ripe, boolean member, boolean hoe, boolean meal) {
        return new FarmingRules.Harvest(ripe, member, hoe, meal);
    }

    // ------------------------------------------------------------------ harvest XP

    @Test
    void onlyRipeCropsWithAHoeByAMemberGiveXp() {
        assertEquals(4.0, FarmingRules.skillXp(h(true, true, true, false), 1, 4, true), EPS);
        assertEquals(0, FarmingRules.skillXp(h(false, true, true, false), 1, 4, true), "unripe (or placed) crop");
        assertEquals(0, FarmingRules.skillXp(h(true, false, true, false), 1, 4, true), "coop players and visitors");
        assertEquals(0, FarmingRules.skillXp(h(true, true, false, false), 1, 4, true), "no hoe");
        assertEquals(0, FarmingRules.skillXp(h(true, true, true, true), 1, 4, true), "bone meal");
        assertEquals(4.0, FarmingRules.skillXp(h(true, true, false, false), 1, 4, false), "hoe rule switched off");
    }

    @Test
    void aGrownColumnCountsEveryGrownBlock() {
        assertEquals(12.0, FarmingRules.skillXp(h(true, true, true, false), 3, 4, true), EPS);
        assertEquals(0, FarmingRules.skillXp(h(true, true, true, false), 0, 4, true), "a placed column has no grown block");
        assertEquals(0, FarmingRules.skillXp(h(true, true, true, false), 1, 0, true), "a crop without XP");
        assertEquals(0, FarmingRules.skillXp(null, 1, 4, true));
    }

    @Test
    void questsNeedNoHoeButTheSameAntiFarmRules() {
        assertTrue(FarmingRules.countsForQuests(h(true, true, false, false)));
        assertFalse(FarmingRules.countsForQuests(h(false, true, true, false)));
        assertFalse(FarmingRules.countsForQuests(h(true, false, true, false)));
        assertFalse(FarmingRules.countsForQuests(h(true, true, true, true)));
        assertFalse(FarmingRules.countsForQuests(null));
    }

    @Test
    void plantingNeedsTheCropLevel() {
        assertTrue(FarmingRules.mayPlant(0, 0), "wheat is open from the start");
        assertFalse(FarmingRules.mayPlant(15, 9), "Farming 15 needed: 9/15");
        assertTrue(FarmingRules.mayPlant(15, 15));
        assertTrue(FarmingRules.mayPlant(15, 40));
    }

    // ------------------------------------------------------------------ growth

    @Test
    void growthMultiplierIsUpgradeTimesCappedFamily() {
        assertEquals(1.0, FarmingRules.growthMultiplier(0, 0, 100), EPS);
        assertEquals(1.12, FarmingRules.growthMultiplier(12, 0, 100), EPS, "Crop Growth I");
        assertEquals(1.12 * 1.15, FarmingRules.growthMultiplier(12, 15, 100), EPS, "plus Cropwarden");
        assertEquals(2.2 * 2.0, FarmingRules.growthMultiplier(120, 250, 100), EPS, "the family is capped at +100%");
        assertEquals(1.0, FarmingRules.growthMultiplier(-50, -10, 100), EPS, "never slower than vanilla");
        assertEquals(1.0, FarmingRules.growthMultiplier(Double.NaN, Double.POSITIVE_INFINITY, 100), EPS);
        assertEquals(1.0, FarmingRules.growthMultiplier(0, 40, 0), EPS, "cap 0 = no family bonus");
    }

    @Test
    void extraStagesFollowTheMultiplier() {
        assertEquals(0, FarmingRules.extraStages(1.0, 0.0), "vanilla speed");
        assertEquals(1, FarmingRules.extraStages(1.36, 0.35), "36% chance hit");
        assertEquals(0, FarmingRules.extraStages(1.36, 0.37), "36% chance missed");
        assertEquals(1, FarmingRules.extraStages(2.5, 0.9));
        assertEquals(2, FarmingRules.extraStages(2.5, 0.1));
        assertEquals(0, FarmingRules.extraStages(Double.NaN, 0.0));
    }

    @Test
    void extraStagesAverageTheMultiplier() {
        java.util.Random r = new java.util.Random(7);
        long total = 0;
        int n = 200_000;
        for (int i = 0; i < n; i++) {
            total += 1 + FarmingRules.extraStages(1.48, r.nextDouble());
        }
        assertEquals(1.48, total / (double) n, 0.01, "average stages per growth step = the multiplier");
    }

    @Test
    void boostedAgeNeverPassesTheMaximum() {
        assertEquals(4, FarmingRules.boostedAge(3, 1, 7));
        assertEquals(7, FarmingRules.boostedAge(6, 3, 7));
        assertEquals(3, FarmingRules.boostedAge(3, -2, 7));
    }

    // ------------------------------------------------------------------ yield

    @Test
    void harvestYieldGivesExtraCopiesByChance() {
        assertEquals(0, FarmingRules.extraCopies(0, 0.0));
        assertEquals(1, FarmingRules.extraCopies(12, 0.11), "Harvest Yield I: 12% double");
        assertEquals(0, FarmingRules.extraCopies(12, 0.12));
        assertEquals(1, FarmingRules.extraCopies(100, 0.99), "100% = always double");
        assertEquals(2, FarmingRules.extraCopies(120, 0.19), "120% = double + 20% triple");
        assertEquals(1, FarmingRules.extraCopies(120, 0.2));
        assertEquals(0, FarmingRules.extraCopies(-5, 0.0));
        assertTrue(FarmingRules.extraCopies(1e12, 0.5) <= 65, "never absurd");
    }

    // ------------------------------------------------------------------ memory used for grown / bone-mealed crops

    @Test
    void grownAndBoneMealedPositionsCountOnce() {
        UUID island = UUID.randomUUID();
        GeneratorMemory grown = new GeneratorMemory(16);
        grown.remember(island, 10, 64, -3, "melon");
        assertNotNull(grown.take(island, 10, 64, -3), "a melon that grew is ripe");
        assertNull(grown.take(island, 10, 64, -3), "placed back again: not ripe any more");
        grown.remember(island, 1, 70, 1, "sugar_cane");
        grown.forget(island, 1, 70, 1);
        assertNull(grown.take(island, 1, 70, 1), "a block placed over a grown position is not grown");
    }
}
