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

    @Test
    void personalCropKeepsTheGrowthStage() {
        assertEquals(7, GrindAreas.viewAge(7, 7, 7), "ripe wheat shows ripe carrots");
        assertEquals(3, GrindAreas.viewAge(7, 7, 3), "ripe wheat shows ripe beetroot");
        assertEquals(0, GrindAreas.viewAge(0, 7, 3), "a replanted crop shows young");
        assertEquals(1, GrindAreas.viewAge(4, 7, 3));
        assertEquals(2, GrindAreas.viewAge(6, 7, 3), "not ripe until the real crop is ripe");
        assertEquals(5, GrindAreas.viewAge(5, 7, 7));
    }

    @Test
    void onlyChunksOverTheAreaAreResent() {
        // Farm: x and z -30..30 -> chunks -2..1.
        assertTrue(GrindAreas.chunkOverlaps(-2, -2, GrindAreas.FARM_X, GrindAreas.FARM_HALF));
        assertTrue(GrindAreas.chunkOverlaps(1, 1, GrindAreas.FARM_X, GrindAreas.FARM_HALF));
        assertTrue(!GrindAreas.chunkOverlaps(2, 0, GrindAreas.FARM_X, GrindAreas.FARM_HALF));
        assertTrue(!GrindAreas.chunkOverlaps(0, -3, GrindAreas.FARM_X, GrindAreas.FARM_HALF));
        // Mine: x 980..1020 -> chunks 61..63.
        assertTrue(GrindAreas.chunkOverlaps(61, 0, GrindAreas.MINE_X, GrindAreas.MINE_HALF));
        assertTrue(GrindAreas.chunkOverlaps(63, -2, GrindAreas.MINE_X, GrindAreas.MINE_HALF));
        assertTrue(!GrindAreas.chunkOverlaps(60, 0, GrindAreas.MINE_X, GrindAreas.MINE_HALF));
        assertTrue(!GrindAreas.chunkOverlaps(64, 0, GrindAreas.MINE_X, GrindAreas.MINE_HALF));
    }
}
