package net.skybornmc.skyblock.quests;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Targets, rewards, tiers and the progress rules of a quest. */
class QuestMathTest {

    private static final double[] MINE = {31.8, 37.5, 44, 48.4, 53.2, 60.2, 65.7, 68.3, 77.3, 83.6, 92.4, 97};
    private static final double[] COMBAT = {6.1, 6.4, 6.6, 6.9, 7.1, 7.4, 7.6, 7.8, 8.1, 8.3};
    private static final long[] REF = {827, 1467, 2560, 4507, 7920, 13867, 24347, 42667, 122133, 349333, 1000000, 2853333};

    @Test
    void niceRounding() {
        assertEquals(1, QuestMath.nice(0));
        assertEquals(12, QuestMath.nice(12));
        assertEquals(85, QuestMath.nice(87));
        assertEquals(240, QuestMath.nice(237));
        assertEquals(1500, QuestMath.nice(1523));
        assertEquals(9900, QuestMath.nice(9924));
        assertEquals(1_200_000, QuestMath.nice(1_234_567));
    }

    @Test
    void targetsScaleWithTheTier() {
        QuestPool.Entry mine = new QuestPool.Entry(QuestType.MINE, 0);
        // Daily: 12 minutes. Tier 1: 31.8 x 12 = 381.6 -> 380; tier 12: 97 x 12 = 1164 -> 1200.
        assertEquals(380, QuestMath.target(mine, MINE, REF, 1, 1, 12, 0.8));
        assertEquals(1200, QuestMath.target(mine, MINE, REF, 12, 1, 12, 0.8));
        // Weekly: 45 minutes at tier 5: 53.2 x 45 = 2394 -> 2400.
        assertEquals(2400, QuestMath.target(mine, MINE, REF, 5, 1, 45, 0.8));
        // Kills: 6.1 x 12 = 73.2 -> 75.
        assertEquals(75, QuestMath.target(new QuestPool.Entry(QuestType.KILL, 0), COMBAT, REF, 1, 1, 12, 0.8));
        // Tiers past the table use the last entry.
        assertEquals(QuestMath.target(new QuestPool.Entry(QuestType.KILL, 0), COMBAT, REF, 10, 1, 12, 0.8),
                QuestMath.target(new QuestPool.Entry(QuestType.KILL, 0), COMBAT, REF, 14, 1, 12, 0.8));
        // Sell: REF(t)/min x minutes x share, by the stage tier: 827 x 12 x 0.8 = 7939 -> 7900.
        assertEquals(7900, QuestMath.target(new QuestPool.Entry(QuestType.SELL, 0), new double[0], REF, 0, 1, 12, 0.8));
        // Fixed targets stay.
        assertEquals(3, QuestMath.target(new QuestPool.Entry(QuestType.VISIT, 3), MINE, REF, 7, 7, 12, 0.8));
    }

    @Test
    void rewards() {
        // P10-21: 3 minutes of REF at tier 1 = 2,481; P10-22: 8 minutes at tier 6 = 110,936.
        assertEquals(2481, QuestMath.money(REF, 1, 3));
        assertEquals(110_936, QuestMath.money(REF, 6, 8));
        assertEquals(0, QuestMath.money(REF, 6, 0));
        // Crystal Pouch S = 20 x 1.3^(t-1): tier 1 = 20, tier 3 = 33.8 -> 34.
        assertEquals(20, QuestMath.crystals(20, 1, 1.3));
        assertEquals(34, QuestMath.crystals(20, 3, 1.3));
        assertEquals(0, QuestMath.crystals(0, 5, 1.3));
    }

    @Test
    void tiers() {
        int[] levels = {0, 5, 10, 15, 20, 25, 30, 35, 42, 50, 58, 66};
        assertEquals(1, QuestMath.tierFor(0, levels));
        assertEquals(1, QuestMath.tierFor(4, levels));
        assertEquals(2, QuestMath.tierFor(5, levels));
        assertEquals(12, QuestMath.tierFor(150, levels));
        assertEquals(1, QuestMath.wingTier(0, 1));
        assertEquals(1, QuestMath.wingTier(1, 1));
        assertEquals(4, QuestMath.wingTier(5, 1));
        assertEquals(5, QuestMath.wingTier(5, 0));
    }

    @Test
    void depthsRegionTiers() {
        assertEquals(4, QuestMath.tierOf("mine_t04_north"));
        assertEquals(11, QuestMath.tierOf("forest_t11_a"));
        assertEquals(1, QuestMath.tierOf("combat_t01"));
        assertEquals(0, QuestMath.tierOf("event_mine"));
        assertEquals(0, QuestMath.tierOf("mine_gate"));
        assertEquals(0, QuestMath.tierOf(null));
    }

    @Test
    void actionBarSteps() {
        assertEquals(25, QuestMath.stepCrossed(99, 100, 400, 25));
        assertEquals(0, QuestMath.stepCrossed(100, 101, 400, 25));
        assertEquals(75, QuestMath.stepCrossed(10, 350, 400, 25), "several steps at once: the highest");
        assertEquals(0, QuestMath.stepCrossed(350, 400, 400, 25), "100% is the completion, not a step");
        assertEquals(50, QuestMath.stepCrossed(0, 1, 2, 25));
    }

    @Test
    void islandHelpers() {
        assertTrue(QuestMath.helped(20, 400, 5));
        assertFalse(QuestMath.helped(19, 400, 5));
        assertFalse(QuestMath.helped(0, 400, 0));
        assertTrue(QuestMath.helped(1, 400, 0));
    }

    @Test
    void progressRules() {
        Quest mine = new Quest(UUID.randomUUID(), 0, QuestType.MINE, 4, 100, 10, 10, 100, 0, 0);
        assertTrue(mine.counts(QuestType.MINE, 4));
        assertTrue(mine.counts(QuestType.MINE, 9), "deeper tiers count");
        assertFalse(mine.counts(QuestType.MINE, 3), "lower tiers don't");
        assertFalse(mine.counts(QuestType.CHOP, 9));
        assertEquals(0, mine.add(70));
        assertEquals(70, mine.add(70));
        assertEquals(100, mine.progress, "capped at the target");
        assertTrue(mine.reached());
        assertTrue(mine.dirty());
        mine.doneAt = 1;
        assertFalse(mine.counts(QuestType.MINE, 9), "a finished quest counts nothing");
        assertFalse(mine.dirty());

        Quest sell = new Quest(UUID.randomUUID(), 1, QuestType.SELL, 0, 5000, 10, 10, 100, 0, 0);
        assertTrue(sell.counts(QuestType.SELL, 0), "non-Depths quests ignore the tier");
    }
}
