package net.skybornmc.skyblock.milestones;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Spec §9.1: highest claimed tier per chain, target match only, island and personal add up. */
class PerkRulesTest {

    @Test
    void perksFollowTheHighestClaimedTierAndTheTarget() throws Exception {
        MilestoneCatalog c = MilestoneCatalog.parse(MilestoneCatalogTest.bundled(), MilestoneCatalogTest.CAPS, p -> { });
        Map<String, Integer> island = Map.of("crop_reaper_wheat", 3, "island_slayer_zombie", 5, "removed_chain", 2);
        Map<String, Integer> personal = Map.of("dungeon_slayer_zombie", 2);
        assertEquals(6, PerkRules.value("extra_drops", "WHEAT", c::chain, island, personal), 1e-9, "tier 3 replaces tiers 1-2");
        assertEquals(6, PerkRules.value("extra_drops", "wheat", c::chain, island), 1e-9, "case-insensitive target");
        assertEquals(2, PerkRules.value("crop_growth", "WHEAT", c::chain, island), 1e-9);
        assertEquals(0, PerkRules.value("extra_drops", "CARROTS", c::chain, island), 1e-9, "other crop: nothing");
        assertEquals(14, PerkRules.value("extra_drops", "ZOMBIE", c::chain, island, personal), 1e-9,
                "island zombie perk also counts in the Depths (owner V6) and adds to the personal one");
        assertEquals(0.5, PerkRules.value("production", "spawner", c::chain, island), 1e-9);
        assertEquals(0, PerkRules.value("extra_drops", null, c::chain, island, null), 1e-9, "no target: targeted perks never apply");
    }
}
