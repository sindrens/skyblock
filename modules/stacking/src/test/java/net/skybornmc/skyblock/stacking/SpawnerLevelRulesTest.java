package net.skybornmc.skyblock.stacking;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Spawners v2 (owner plan 5 Nov 2026, FULL_PLAN.md 5): levels from kills, limit per mob type, prestige. */
class SpawnerLevelRulesTest {

    private static final StackingConfig C = new StackingConfig();

    @Test
    void levelsNeedBaseTimesLevelSquared() {
        long b = SpawnerLevelRules.base(C.levelBase, 1);
        assertEquals(100, b);
        assertEquals(1, SpawnerLevelRules.level(0, b, 16), "level 1 is free");
        assertEquals(1, SpawnerLevelRules.level(399, b, 16));
        assertEquals(2, SpawnerLevelRules.level(400, b, 16), "owner: tier 1 level 2 = 400 kills");
        assertEquals(16, SpawnerLevelRules.level(10_000_000, b, 16));
        assertEquals(1500, SpawnerLevelRules.base(C.levelBase, 4));
        assertEquals(0, SpawnerLevelRules.base(C.levelBase, 5), "premium: no levels");
        assertEquals(1, SpawnerLevelRules.level(1_000_000, 0, 16));
        assertEquals(900, SpawnerLevelRules.killsFor(3, 100));
        assertEquals(0.5, SpawnerLevelRules.progress(650, 100, 16), 1e-9, "halfway from 400 to 900");
        assertEquals(1, SpawnerLevelRules.progress(25_600, 100, 16), 1e-9);
    }

    @Test
    void limitIsSixtyFourThenThirtyTwoALevel() {
        assertEquals(64, SpawnerLevelRules.limit(1, 0, false, 64, 32, 544, 3, 16), "owner 7 Nov 2026: 64 at the first level");
        assertEquals(96, SpawnerLevelRules.limit(2, 0, false, 64, 32, 544, 3, 16));
        assertEquals(128, SpawnerLevelRules.limit(3, 0, false, 64, 32, 544, 3, 16));
        assertEquals(544, SpawnerLevelRules.limit(16, 0, false, 64, 32, 544, 3, 16));
        assertEquals(544, SpawnerLevelRules.limit(1, 1, false, 64, 32, 544, 3, 16), "a prestige never takes placed spawners away");
        assertEquals(3, SpawnerLevelRules.limit(16, 0, true, 64, 32, 544, 3, 16), "premium tier 5");
        assertEquals(SpawnerLevelRules.limit(16, 0, false, C.limitBase, C.limitPerLevel, C.limitMax, C.premiumLimit, C.levelMax), C.limitMax);
        assertTrue(C.spawnerStackStart >= C.limitMax + 320, "one block holds the whole limit with Spawner Capacity");
    }

    @Test
    void burningMeatIsCooked() {
        assertEquals(org.bukkit.Material.COOKED_BEEF, SpawnerDrops.cooked(org.bukkit.Material.BEEF));
        assertEquals(org.bukkit.Material.COOKED_PORKCHOP, SpawnerDrops.cooked(org.bukkit.Material.PORKCHOP));
        assertEquals(org.bukkit.Material.LEATHER, SpawnerDrops.cooked(org.bukkit.Material.LEATHER));
        assertEquals(org.bukkit.Material.ROTTEN_FLESH, SpawnerDrops.cooked(org.bukkit.Material.ROTTEN_FLESH));
    }

    @Test
    void prestigeAtTheTopUpToTen() {
        assertTrue(SpawnerLevelRules.canPrestige(25_600, 100, 16, 0, 10));
        assertFalse(SpawnerLevelRules.canPrestige(25_599, 100, 16, 0, 10));
        assertFalse(SpawnerLevelRules.canPrestige(25_600, 100, 16, 10, 10), "at most 10");
        assertFalse(SpawnerLevelRules.canPrestige(10_000_000, 0, 16, 0, 10), "premium never");
        assertEquals(1.5, SpawnerLevelRules.dropMultiplier(10, 5), 1e-9);
        assertEquals(1.0, SpawnerLevelRules.dropMultiplier(0, 5), 1e-9);
    }

    @Test
    void defaultsFollowThePlan() {
        assertEquals(List.of(100L, 250L, 600L, 1500L), C.levelBase);
        assertEquals(2048, C.spawnerStackMax);
        assertEquals(3, C.spawnRange);
        assertEquals("10", C.speedSteps.getFirst());
        assertEquals("5", C.speedSteps.getLast(), "Spawner Speed level 10: every 5 s");
        assertEquals(11, C.speedSteps.size());
        assertEquals("<c:flame>●●●●●</c:flame><c:locked>●●●●●</c:locked>", SpawnerMenu.bar(0.55));
    }
}
