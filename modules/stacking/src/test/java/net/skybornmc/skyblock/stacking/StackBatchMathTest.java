package net.skybornmc.skyblock.stacking;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Batch kills (E3, ENCHANTS §7.2): loot is rolled once per batch for every mob, Plunder rolls and Soul Reaper XP on top. */
class StackBatchMathTest {

    @Test
    void lootMobsCountEveryKilledMobOnce() {
        assertEquals(7, StackMath.lootMobs(8, true, 0), "a lethal hit: the dying mob drops through its death event");
        assertEquals(0, StackMath.lootMobs(1, true, 0), "one kill, nothing extra");
        assertEquals(8, StackMath.lootMobs(8, false, 0), "area kills have no death event");
        assertEquals(10, StackMath.lootMobs(8, false, 2), "plus the extra (Plunder) rolls");
        assertEquals(2, StackMath.lootMobs(1, true, 2));
        assertEquals(0, StackMath.lootMobs(0, true, -3), "never negative");
    }

    @Test
    void extraRollsAreAtMostOnePerMob() {
        assertEquals(48, StackMath.extraRolls(48, 100, () -> 0.5));
        assertEquals(48, StackMath.extraRolls(48, 250, () -> 0.0), "above 100% is one roll per mob");
        assertEquals(0, StackMath.extraRolls(48, 0, () -> 0.0));
        assertEquals(0, StackMath.extraRolls(48, Double.NaN, () -> 0.0));
        Random random = new Random(11);
        long sum = 0;
        for (int i = 0; i < 1_000; i++) {
            sum += StackMath.extraRolls(48, 20, random::nextDouble);
        }
        double average = sum / 1_000.0;
        assertTrue(Math.abs(average - 9.6) < 0.4, "Plunder IV: 20% of 48 mobs ~ 9.6 rolls, got " + average);
    }

    @Test
    void xpWithSoulReaperBonus() {
        assertEquals(240, StackMath.xp(5, 48, 0));
        assertEquals(360, StackMath.xp(5, 48, 50), "Soul Reaper V: +50% vanilla XP");
        assertEquals(5, StackMath.xp(5, 1, 10), "rounded down");
        assertEquals(0, StackMath.xp(-1, 48, 50));
        assertEquals(Integer.MAX_VALUE, StackMath.xp(Integer.MAX_VALUE, 1_024, 50), "clamped");
        assertEquals(StackMath.xp(5, 48), StackMath.xp(5, 48, Double.NaN), "a broken bonus is no bonus");
    }

    @Test
    void scaledLootForABatchKeepsTheAverage() {
        // 4 sampled mobs dropped 10 rotten flesh in total; the batch killed 48 (+2 Plunder rolls) = 50 mobs of loot.
        Random random = new Random(5);
        long total = 0;
        int runs = 2_000;
        for (int i = 0; i < runs; i++) {
            total += StackMath.scale(10, 4, StackMath.lootMobs(48, false, 2), random.nextDouble());
        }
        assertEquals(125.0, total / (double) runs, 0.05, "10 / 4 x 50 = 125 per batch on average");
    }

    @Test
    void killedNeverPassesTheStack() {
        assertEquals(30, StackMath.killed(30, false, 72), "Slaughter X + Bloodlust on a stack of 30");
        assertEquals(1, StackMath.killed(30, false, 0));
        assertEquals(30, StackMath.killed(30, true, 1), "grinder damage takes the whole stack");
    }
}
