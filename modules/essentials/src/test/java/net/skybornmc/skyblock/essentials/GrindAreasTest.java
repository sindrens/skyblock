package net.skybornmc.skyblock.essentials;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrindAreasTest {

    @Test
    void cropsUnlockInOrder() {
        assertEquals(Material.WHEAT, GrindAreas.cropFor(0).block());
        assertEquals(Material.WHEAT, GrindAreas.cropFor(499).block());
        assertEquals(Material.CARROTS, GrindAreas.cropFor(500).block());
        assertEquals(Material.BEETROOTS, GrindAreas.cropFor(1_000_000).block());
    }

    @Test
    void oresAreStablePerPositionAndOnlyUnlockedOnesShow() {
        int ores = 0;
        for (int x = 0; x < 100; x++) {
            for (int z = 0; z < 100; z++) {
                Material a = GrindAreas.oreAt(x, 99, z, 0);
                assertEquals(a, GrindAreas.oreAt(x, 99, z, 0), "same view every time");
                assertTrue(a == Material.STONE || a == Material.COAL_ORE || a == Material.COPPER_ORE, "only tier 0 ores: " + a);
                if (a != Material.STONE) {
                    ores++;
                }
            }
        }
        assertTrue(ores > 1_000 && ores < 2_200, "about 15% ores at the start: " + ores);
        assertEquals(Material.EMERALD_ORE, GrindAreas.oreTier(20_000).block());
    }
}
