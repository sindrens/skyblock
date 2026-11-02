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
}
