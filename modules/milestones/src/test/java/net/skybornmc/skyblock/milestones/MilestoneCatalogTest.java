package net.skybornmc.skyblock.milestones;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The bundled milestones.yml and the load validator (Complete Developer Spec §7.7, §9.2). */
class MilestoneCatalogTest {

    static final Map<String, Double> CAPS = Map.of("sell", 300.0, "crystals", 200.0, "extra_drops", 200.0, "tool_xp", 100.0,
            "skill_xp", 300.0, "crop_growth", 100.0, "production", 100.0, "rare_finds", 150.0);

    static Map<String, Object> bundled() throws Exception {
        try (InputStream in = MilestoneCatalogTest.class.getClassLoader().getResourceAsStream("milestones.yml")) {
            return MilestoneCatalog.map(new org.yaml.snakeyaml.Yaml().load(in));
        }
    }

    static Map<String, Object> yaml(String text) {
        return MilestoneCatalog.map(new org.yaml.snakeyaml.Yaml().load(text));
    }

    @Test
    void bundledCatalogLoadsWithoutProblemsAndInsideTheBudget() throws Exception {
        List<String> problems = new ArrayList<>();
        MilestoneCatalog c = MilestoneCatalog.parse(bundled(), CAPS, problems::add);
        assertEquals(List.of(), problems);
        assertEquals(51, c.size(), "owner 5 Nov 2026: island (crops, ores, logs, mobs) + Farm + Mine chains");
        MilestoneModel.Chain wheat = c.chain("crop_reaper_wheat").orElseThrow();
        assertEquals(MilestoneModel.Menu.ISLAND, wheat.menu());
        assertEquals(5, wheat.maxTier(), "owner F11: 5 tiers per chain");
        assertArrayEquals(new long[]{1_000, 4_500, 20_000, 91_000, 410_000}, wheat.tiers());
        assertTrue(wheat.perksOf(5).stream().allMatch(p -> "WHEAT".equals(p.target())), "perks help the activity ground");
    }

    @Test
    void theIndexFindsIslandAndPersonalChainsOfOneTarget() throws Exception {
        MilestoneCatalog c = MilestoneCatalog.parse(bundled(), CAPS, p -> { });
        List<String> ids = c.chainsFor("break_block", "coal_ore").stream().map(MilestoneModel.Chain::id).sorted().toList();
        assertEquals(List.of("island_miner_coal_ore", "mine_digger_coal_ore"), ids, "the island and the /mine chain");
        assertEquals(List.of(), c.chainsFor("kill_mob", "villager"));
        assertEquals(List.of(), c.chainsFor("fly", "WHEAT"));
    }

    @Test
    void tiersAreDerivedFromTheTotal() {
        MilestoneModel.Chain chain = new MilestoneModel.Chain("x", "X", "STONE", MilestoneModel.Menu.ISLAND, "c", "break_block",
                java.util.Set.of("STONE"), MilestoneModel.WorldFilter.ISLAND, new long[]{10, 20, 30}, Map.of(), 1);
        assertEquals(0, chain.tierFor(9));
        assertEquals(1, chain.tierFor(10));
        assertEquals(3, chain.tierFor(Long.MAX_VALUE));
        assertEquals(20, chain.target(2));
        assertEquals(-1, chain.target(4));
    }

    @Test
    void curvesAreRoundAndStrictlyIncreasing() {
        assertArrayEquals(new long[]{250, 980, 3_800, 15_000, 58_000}, new MilestoneModel.Curve(250, 3.9, 5).targets(1));
        assertArrayEquals(new long[]{1, 2, 3}, new MilestoneModel.Curve(1, 1.01, 3).targets(1), "never equal neighbours");
    }

    @Test
    void theValidatorSkipsBrokenChainsWithAReason() {
        List<String> problems = new ArrayList<>();
        MilestoneCatalog c = MilestoneCatalog.parse(yaml("""
                settings: {allowed-families: [extra_drops]}
                curves: {standard: {base: 10, growth: 2, tiers: 3}}
                templates:
                  wrong_target: {menu: island, action: harvest_crop, objective: {filter: {material: WHEAT, world: island}},
                                 perks: {1: [{family: extra_drops, value: 2, target: CARROTS}]}}
                  wrong_family: {menu: island, action: harvest_crop, objective: {filter: {material: POTATOES}},
                                 perks: {1: [{family: speed, value: 2}]}}
                  island_in_depths: {menu: island, action: break_block, objective: {filter: {material: STONE, world: depths}}}
                  bad_action: {menu: personal, action: fly, objective: {filter: {material: STONE}}}
                  good: {menu: island, action: harvest_crop, objective: {filter: {material: WHEAT, world: island}},
                         perks: {3: [{family: extra_drops, value: 40, target: WHEAT}]}}
                  dup_personal: {menu: personal, action: harvest_crop, objective: {filter: {material: WHEAT, world: any}}}
                """), Map.of("extra_drops", 200.0), problems::add);
        assertEquals(List.of("good"), c.all().stream().map(MilestoneModel.Chain::id).toList());
        assertEquals(6, problems.size(), String.join("\n", problems));
        assertTrue(problems.stream().anyMatch(p -> p.contains("budget")), "40% > 15% of 200");
    }
}
