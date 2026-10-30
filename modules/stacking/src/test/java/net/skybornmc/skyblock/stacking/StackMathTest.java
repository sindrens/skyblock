package net.skybornmc.skyblock.stacking;

import org.junit.jupiter.api.Test;

import java.util.OptionalInt;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Merge, kill, loot, breeding, spawner and limit math (owner decisions S2–S23, SK1, SK2). */
class StackMathTest {

    // ------------------------------------------------------------------ merging

    @Test
    void roomUnderTheCap() {
        assertEquals(1024, StackMath.room(0, 1024));
        assertEquals(1, StackMath.room(1023, 1024));
        assertEquals(0, StackMath.room(1024, 1024));
        assertEquals(0, StackMath.room(5000, 1024));
    }

    @Test
    void distributeFillsExistingStacksFirst() {
        StackMath.Distribution d = StackMath.distribute(100, new int[]{1000, 1020}, 1024, 8);
        assertArrayEquals(new int[]{24, 4}, d.added());
        assertArrayEquals(new int[]{72}, d.newStacks());
        assertEquals(0, d.discarded());
        assertEquals(100, d.placed());
    }

    @Test
    void distributeSplitsIntoNewStacksOfAtMostTheCap() {
        StackMath.Distribution d = StackMath.distribute(2500, new int[0], 1024, 8);
        assertArrayEquals(new int[]{1024, 1024, 452}, d.newStacks());
        assertEquals(0, d.discarded());
    }

    @Test
    void distributeDropsWhatDoesNotFitTheChunk() {
        // Owner SK1: 8 stacks per type per chunk; a full chunk cancels the rest.
        StackMath.Distribution d = StackMath.distribute(5000, new int[]{1024}, 1024, 2);
        assertArrayEquals(new int[]{0}, d.added());
        assertArrayEquals(new int[]{1024, 1024}, d.newStacks());
        assertEquals(5000 - 2048, d.discarded());
        StackMath.Distribution none = StackMath.distribute(10, new int[]{1024}, 1024, 0);
        assertEquals(10, none.discarded());
        assertEquals(0, none.placed());
    }

    @Test
    void distributeNothing() {
        StackMath.Distribution d = StackMath.distribute(0, new int[]{3}, 1024, 8);
        assertArrayEquals(new int[]{0}, d.added());
        assertEquals(0, d.newStacks().length);
        assertThrows(IllegalArgumentException.class, () -> StackMath.distribute(1, new int[0], 0, 1));
    }

    // ------------------------------------------------------------------ kills

    @Test
    void oneKillPerHitByDefault() {
        assertEquals(1, StackMath.killed(45, false, 1));
        assertEquals(1, StackMath.killed(45, false, 0));
        assertEquals(1, StackMath.killed(45, false, -3));
    }

    @Test
    void weaponKillsAreClampedToTheStack() {
        assertEquals(10, StackMath.killed(45, false, 10));
        assertEquals(45, StackMath.killed(45, false, 1000));
    }

    @Test
    void grinderDamageKillsTheWholeStack() {
        assertEquals(1024, StackMath.killed(1024, true, 1));
        assertEquals(1, StackMath.killed(1, true, 1));
        assertEquals(1, StackMath.killed(0, true, 1));
    }

    @Test
    void xpTimesKilledWithoutOverflow() {
        assertEquals(5 * 1024, StackMath.xp(5, 1024));
        assertEquals(0, StackMath.xp(-5, 10));
        assertEquals(Integer.MAX_VALUE, StackMath.xp(Integer.MAX_VALUE, 3));
    }

    // ------------------------------------------------------------------ loot

    @Test
    void scaleIsExactWhenSamplesEqualMobs() {
        assertEquals(17, StackMath.scale(17, 8, 8, 0.99));
    }

    @Test
    void scaleRoundsTheFractionByChance() {
        // 8 samples dropped 10 items in total; 100 mobs -> 125 exactly.
        assertEquals(125, StackMath.scale(10, 8, 100, 0.5));
        // 3 samples, 4 items, 10 mobs -> 13.33: 14 with a low roll, 13 with a high one.
        assertEquals(14, StackMath.scale(4, 3, 10, 0.1));
        assertEquals(13, StackMath.scale(4, 3, 10, 0.9));
        assertEquals(0, StackMath.scale(0, 8, 100, 0.0));
        assertEquals(0, StackMath.scale(5, 0, 100, 0.0));
    }

    @Test
    void scaleAverageIsUnbiased() {
        Random random = new Random(1);
        long total = 0;
        int runs = 20_000;
        for (int i = 0; i < runs; i++) {
            total += StackMath.scale(4, 3, 10, random.nextDouble());
        }
        assertEquals(13.333, (double) total / runs, 0.05);
    }

    @Test
    void binomialEdgesAndAverage() {
        Random random = new Random(7);
        assertEquals(0, StackMath.binomial(0, 0.5, random::nextDouble));
        assertEquals(0, StackMath.binomial(100, 0, random::nextDouble));
        assertEquals(100, StackMath.binomial(100, 1, random::nextDouble));
        long sum = 0;
        for (int i = 0; i < 200; i++) {
            sum += StackMath.binomial(1024, 0.02, random::nextDouble);
        }
        assertEquals(1024 * 0.02, sum / 200.0, 1.5);
        long big = StackMath.binomial(1_000_000, 0.5, random::nextDouble);
        assertTrue(big > 495_000 && big < 505_000, "normal approximation: " + big);
    }

    // ------------------------------------------------------------------ breeding (owner S10)

    @Test
    void oneBabyPerFoodItem() {
        // Owner example: 15 cows + 5 wheat = 5 babies.
        StackMath.Breed b = StackMath.breed(15, 0, 5, 1024);
        assertEquals(StackMath.BreedResult.OK, b.result());
        assertEquals(5, b.babies());
        assertEquals(10, b.busyAfter());
    }

    @Test
    void babiesLimitedByPairs() {
        StackMath.Breed b = StackMath.breed(15, 0, 64, 1024);
        assertEquals(7, b.babies());
        assertEquals(14, b.busyAfter());
    }

    @Test
    void parentsOnCooldownDoNotBreedAgain() {
        StackMath.Breed second = StackMath.breed(20, 10, 64, 1024);
        assertEquals(5, second.babies());
        assertEquals(20, second.busyAfter());
        StackMath.Breed third = StackMath.breed(25, 20, 64, 1024);
        assertEquals(2, third.babies());
        assertEquals(StackMath.BreedResult.COOLDOWN, StackMath.breed(20, 20, 64, 1024).result());
        assertEquals(StackMath.BreedResult.COOLDOWN, StackMath.breed(21, 20, 64, 1024).result());
    }

    @Test
    void breedingStopsAtTheStackCap() {
        StackMath.Breed b = StackMath.breed(1022, 0, 64, 1024);
        assertEquals(2, b.babies());
        assertEquals(StackMath.BreedResult.FULL, StackMath.breed(1024, 0, 64, 1024).result());
    }

    @Test
    void breedingNeedsTwoAndFood() {
        assertEquals(StackMath.BreedResult.TOO_FEW, StackMath.breed(1, 0, 5, 1024).result());
        assertEquals(StackMath.BreedResult.NO_FOOD, StackMath.breed(10, 0, 0, 1024).result());
        // A stale busy count above the stack is clamped.
        assertEquals(StackMath.BreedResult.COOLDOWN, StackMath.breed(4, 99, 5, 1024).result());
    }

    // ------------------------------------------------------------------ spawners

    @Test
    void spawnerOutputIsFullRateTimesStackTimesMastery() {
        StackMath.Output out = StackMath.spawnerOutput(4, 8, 1.0, 0);
        assertEquals(32, out.mobs());
        assertEquals(0, out.carry(), 1e-9);
        StackMath.Output fast = StackMath.spawnerOutput(4, 8, 1.5, 0);
        assertEquals(48, fast.mobs());
    }

    @Test
    void spawnerOutputCarriesFractions() {
        StackMath.Output first = StackMath.spawnerOutput(0.4, 1, 1.0, 0);
        assertEquals(0, first.mobs());
        StackMath.Output second = StackMath.spawnerOutput(0.4, 1, 1.0, first.carry());
        assertEquals(0, second.mobs());
        StackMath.Output third = StackMath.spawnerOutput(0.4, 1, 1.0, second.carry());
        assertEquals(1, third.mobs());
        assertEquals(0.2, third.carry(), 1e-9);
    }

    @Test
    void stackLimitFromMasteryClampedToMax() {
        assertEquals(8, StackMath.stackLimit(OptionalInt.empty(), 8, 512));
        assertEquals(64, StackMath.stackLimit(OptionalInt.of(64), 8, 512));
        assertEquals(512, StackMath.stackLimit(OptionalInt.of(9999), 8, 512));
        assertEquals(1, StackMath.stackLimit(OptionalInt.of(0), 8, 512));
        assertEquals(5, StackMath.stackLimit(OptionalInt.empty(), 8, 5));
    }

    @Test
    void breakTakesOneOrAll() {
        assertEquals(1, StackMath.breakAmount(8, false));
        assertEquals(8, StackMath.breakAmount(8, true));
        assertEquals(0, StackMath.breakAmount(0, true));
    }

    @Test
    void intervalBetweenMinAndMax() {
        assertEquals(200, StackMath.intervalTicks(10, 40, 0));
        assertEquals(800, StackMath.intervalTicks(10, 40, 1));
        assertEquals(500, StackMath.intervalTicks(10, 40, 0.5));
        assertEquals(200, StackMath.intervalTicks(40, 10, 0)); // swapped values still work
        assertEquals(20, StackMath.intervalTicks(0, 0, 0.5));
    }

    // ------------------------------------------------------------------ limits (owner SK2)

    @Test
    void entityLimitWithUpgrade() {
        assertEquals(20, StackMath.entityLimit(20, 0));
        assertEquals(30, StackMath.entityLimit(20, 50));
        assertEquals(90, StackMath.entityLimit(60, 50));
        assertEquals(15, StackMath.entityLimit(10, 50));
        assertEquals(45, StackMath.entityLimit(30, 50));
        assertEquals(20, StackMath.entityLimit(20, -10));
    }
}
