package net.skybornmc.skyblock.islandtools;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Island farming v2 (owner 3 Nov 2026): the stage clock. */
class IslandGrowthTest {

    @Test
    void theFullTimeIsSplitOverTheStages() {
        assertEquals(8_571, IslandGrowth.stepMillis(60, 7, 500), "wheat: 60 s over 7 steps");
        assertEquals(2_143, IslandGrowth.stepMillis(15, 7, 500), "the top upgrade: 15 s");
        assertEquals(500, IslandGrowth.stepMillis(1, 7, 500), "never faster than the floor");
        assertEquals(10_000, IslandGrowth.stepMillis(10, 0, 500));
    }

    @Test
    void cropNamesReadWell() {
        assertEquals("Wheat", IslandGrowth.name(Material.WHEAT));
        assertEquals("Sweet Berry Bush", IslandGrowth.name(Material.SWEET_BERRY_BUSH));
        assertEquals("Melon", IslandGrowth.name(Material.MELON_STEM));
        assertEquals("Torchflower", IslandGrowth.name(Material.TORCHFLOWER_CROP));
        assertTrue(IslandGrowth.CROPS.contains(Material.CARROTS));
    }

    /** Feedback 4 Nov 2026 (D18a): torchflowers get their last (flower) step; pitcher tops and ripe crops stay off the clock. */
    @Test
    void whichCropsNeedTheClock() {
        assertEquals(7, IslandGrowth.doneAge(Material.WHEAT, 7));
        assertEquals(2, IslandGrowth.doneAge(Material.TORCHFLOWER_CROP, 1), "age 1 is not a flower yet");
        assertTrue(IslandGrowth.needsClock(Material.TORCHFLOWER_CROP, 1, 1, false));
        assertTrue(IslandGrowth.needsClock(Material.WHEAT, 3, 7, false));
        assertTrue(!IslandGrowth.needsClock(Material.WHEAT, 7, 7, false), "ripe");
        assertTrue(!IslandGrowth.needsClock(Material.PITCHER_CROP, 3, 4, true), "the top half grows with the bottom");
        assertTrue(!IslandGrowth.needsClock(Material.SUGAR_CANE, 0, 15, false), "vanilla growth");
    }

    /**
     * D18a, the owner's test: a 60x60 field (3,600 crops, 7 stages, 3 s per stage) must be ripe in about the configured
     * 21 s. The budget is shared by every island: the old 600 per run was exactly one such field (two fields: half speed).
     */
    @Test
    void aBigFieldIsRipeInAboutTheConfiguredTime() {
        long step = 3_000;
        int budget = new IslandToolsConfig().growthBudget;
        assertTrue(fieldDone(3_600, 7, step, budget, true) <= 7 * step + 3_000, "one 60x60 field");
        assertTrue(fieldDone(2 * 3_600, 7, step, budget, true) <= 7 * step + 3_000, "two 60x60 fields at the same time");
        assertTrue(fieldDone(2 * 3_600, 7, step, 600, true) > 7 * step * 3 / 2, "the old budget: far too slow for two");
        assertEquals(10_500, IslandGrowth.nextDue(7_500, 3_000, 9_000), "late by 1.5 s: the next stage keeps its time");
        assertEquals(20_000, IslandGrowth.nextDue(7_500, 3_000, 20_000), "never in the past");
    }

    /** Milliseconds until every crop is ripe, half-second runs with {@code budget} crops each (the tick's queue). */
    private static long fieldDone(int crops, int stages, long step, int budget, boolean keepTime) {
        java.util.PriorityQueue<long[]> queue = new java.util.PriorityQueue<>((a, b) -> Long.compare(a[0], b[0]));
        for (int i = 0; i < crops; i++) {
            queue.add(new long[]{step + (i % 10) * 50L, 0}); // planted over half a second
        }
        long now = 0;
        long last = 0;
        while (!queue.isEmpty()) {
            now += 500;
            int left = budget;
            while (!queue.isEmpty() && queue.peek()[0] <= now && left-- > 0) {
                long[] d = queue.poll();
                long age = d[1] + 1;
                if (age >= stages) {
                    last = now;
                    continue;
                }
                long next = keepTime ? IslandGrowth.nextDue(d[0], step, now) : now + step;
                queue.add(new long[]{next, age});
            }
        }
        return last;
    }
}
