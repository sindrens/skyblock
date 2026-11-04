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
    void oneOreFillsTheMinePerTier() {
        java.util.List<Long> none = java.util.List.of();
        assertEquals(Material.COAL_ORE, GrindAreas.oreTier(0, none).block(), "owner 4 Nov 2026: tier 1 is all coal");
        assertEquals(Material.COAL_ORE, GrindAreas.BASE_ORE);
        assertEquals(Material.COPPER_ORE, GrindAreas.oreTier(400, none).block());
        assertEquals(Material.EMERALD_ORE, GrindAreas.oreTier(1_000_000, none).block());
        java.util.List<Long> custom = java.util.List.of(0L, 10L, -1L);
        assertEquals(Material.COPPER_ORE, GrindAreas.oreTier(10, custom).block(), "thresholds from the config");
        assertEquals(1_200, GrindAreas.unlockAt(2, custom), "a broken entry keeps the built-in threshold");
        assertEquals(0, GrindAreas.unlockAt(0, custom));
        assertEquals(java.util.List.of(Material.COAL_ORE, Material.COPPER_ORE, Material.IRON_ORE, Material.GOLD_ORE, Material.REDSTONE_ORE,
                Material.LAPIS_ORE, Material.DIAMOND_ORE, Material.EMERALD_ORE), GrindAreas.ORE_TIERS.stream().map(GrindAreas.Ore::block).toList());
        assertEquals(120, GrindAreas.tierAmount(java.util.List.of(40, 70, 120), 2, 5));
        assertEquals(5, GrindAreas.tierAmount(java.util.List.of(40), 3, 5), "missing tier: the fallback");
        assertEquals(5, GrindAreas.tierAmount(java.util.List.of("x"), 0, 5));
        EssentialsConfig c = new EssentialsConfig();
        assertTrue(c.grindBonusChance > 0 && c.grindBonusChance < 20, "the money bonus is rare");
        assertTrue(c.grindMineBonus.getLast() > c.grindMineBonus.getFirst() * 10, "and grows with the tier");
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
