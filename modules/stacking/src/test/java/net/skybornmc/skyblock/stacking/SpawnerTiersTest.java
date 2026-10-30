package net.skybornmc.skyblock.stacking;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Spawner tiers (owner SK3–SK8) and rare drops (SK10) from stacking.yml. */
class SpawnerTiersTest {

    private static final StackingConfig DEFAULTS = new StackingConfig();

    private static List<List<String>> lists(StackingConfig c) {
        return List.of(c.tier1, c.tier2, c.tier3, c.tier4, c.tier5);
    }

    @Test
    void ownerTiersLoadWithoutProblems() {
        List<String> problems = new ArrayList<>();
        SpawnerTiers tiers = SpawnerTiers.load(lists(DEFAULTS), id -> true, problems::add);
        assertEquals(List.of(), problems);
        assertEquals(1, tiers.tier("pig").orElseThrow());
        assertEquals(2, tiers.tier("cave_spider").orElseThrow());
        assertEquals(3, tiers.tier("magma_cube").orElseThrow());
        assertEquals(4, tiers.tier("blaze").orElseThrow());
        assertEquals(5, tiers.tier("iron_golem").orElseThrow());
        assertEquals(5, tiers.tier("minecraft:EVOKER").orElseThrow());
        assertEquals(30, tiers.types().size()); // 5 + 6 + 6 + 7 + 6
        assertEquals("pig", tiers.types().getFirst());
    }

    @Test
    void neverTypesAreRefusedEvenIfListed() {
        List<String> problems = new ArrayList<>();
        SpawnerTiers tiers = SpawnerTiers.load(List.of(List.of("pig", "villager"), List.of("warden", "allay"), List.of(),
                List.of("ender_dragon", "elder_guardian"), List.of("wither", "breeze")), id -> true, problems::add);
        for (String never : Set.of("villager", "warden", "allay", "ender_dragon", "elder_guardian", "wither")) {
            assertFalse(tiers.allowed(never), never);
        }
        assertTrue(tiers.allowed("pig"));
        assertTrue(tiers.allowed("breeze"));
        assertEquals(6, problems.size());
    }

    @Test
    void unknownAndDuplicateTypesAreSkipped() {
        List<String> problems = new ArrayList<>();
        SpawnerTiers tiers = SpawnerTiers.load(List.of(List.of("pig", "dragonfly", " "), List.of("pig")), id -> !id.equals("dragonfly"),
                problems::add);
        assertEquals(List.of("pig"), tiers.types());
        assertEquals(1, tiers.tier("pig").orElseThrow());
        assertEquals(2, problems.size());
    }

    @Test
    void rareDropsDefaults() {
        List<String> problems = new ArrayList<>();
        List<SpawnerTiers.RareDrop> drops = SpawnerTiers.rareDrops(DEFAULTS.rareDrops, id -> true, id -> true, problems::add);
        assertEquals(List.of(), problems);
        assertEquals(List.of(new SpawnerTiers.RareDrop("evoker", "totem_of_undying", 0.02),
                new SpawnerTiers.RareDrop("shulker", "shulker_shell", 0.05)), drops);
    }

    @Test
    void badRareDropLinesAreSkipped() {
        List<String> problems = new ArrayList<>();
        List<SpawnerTiers.RareDrop> drops = SpawnerTiers.rareDrops(List.of("evoker:totem_of_undying", "evoker:totem:2",
                "evoker:totem:x", "nobody:totem:0.1", "evoker:nothing:0.1", "minecraft:evoker"), id -> !id.equals("nobody"),
                id -> !id.equals("nothing"), problems::add);
        assertEquals(List.of(), drops);
        assertEquals(6, problems.size());
    }
}
