package net.skybornmc.skyblock.islands;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The ready-made farm on new islands (owner 3 Nov 2026). */
class StarterFarmTest {

    @Test
    void defaultFarmHasAWaterRowFarmlandWheatAndASealedRim() {
        List<StarterFarm.Piece> plan = StarterFarm.plan(11, 0, 6, 3, 2, "wheat");
        long water = plan.stream().filter(p -> p.block().equals("water")).count();
        long farmland = plan.stream().filter(p -> p.block().startsWith("farmland")).count();
        long wheat = plan.stream().filter(p -> p.block().startsWith("wheat")).count();
        assertEquals(6, water, "one water row along the plot");
        assertEquals(36, farmland, "6 columns x 3 rows on both sides");
        assertEquals(24, wheat, "the 2 inner rows each side are planted, the outer ones stay empty");
        // Every water block is closed in on x by the path and the rim, so it can't flow off the island.
        assertTrue(plan.stream().anyMatch(p -> p.dx() == 11 && p.dz() == 0 && p.dy() == 0 && p.block().equals("coarse_dirt")));
        assertTrue(plan.stream().anyMatch(p -> p.dx() == 18 && p.dz() == 0 && p.dy() == 0 && p.block().equals("mossy_stone_bricks")));
        assertTrue(plan.stream().anyMatch(p -> p.block().equals("composter")));
        assertTrue(plan.stream().anyMatch(p -> p.block().equals("crafting_table")));
        Set<String> spots = new HashSet<>();
        for (StarterFarm.Piece p : plan) {
            assertTrue(spots.add(p.dx() + "," + p.dy() + "," + p.dz()), "one block per spot: " + p);
        }
    }

    @Test
    void nothingForAnEmptyPlot() {
        assertTrue(StarterFarm.plan(11, 0, 0, 3, 2, "wheat").isEmpty());
    }
}
