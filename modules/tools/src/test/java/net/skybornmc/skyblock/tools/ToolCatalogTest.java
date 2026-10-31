package net.skybornmc.skyblock.tools;

import net.skybornmc.skyblock.common.tools.ToolKind;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The shipped catalog parses without problems and follows the plan (Depths tiers, balance sheet prices). */
class ToolCatalogTest {

    /** No server registries in unit tests: every known material counts as an item and a block. */
    static final ToolCatalog.Checks CHECKS = new ToolCatalog.Checks(m -> !m.name().equals("AIR"), m -> true);

    static YamlConfiguration defaults() throws Exception {
        try (var in = ToolCatalogTest.class.getClassLoader().getResourceAsStream("tools-defaults.yml")) {
            assertNotNull(in, "tools-defaults.yml missing");
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    static ToolCatalog catalog() throws Exception {
        List<String> problems = new ArrayList<>();
        ToolCatalog catalog = ToolCatalog.load(defaults(), problems::add, CHECKS);
        assertEquals(List.of(), problems);
        return catalog;
    }

    @Test
    void tiersFollowTheDepthsWings() throws Exception {
        ToolCatalog c = catalog();
        assertEquals(12, c.maxTier(ToolKind.PICKAXE), "Mine: Coal → Voidstone");
        assertEquals(11, c.maxTier(ToolKind.AXE), "Forest: Oak → Warped");
        assertEquals(10, c.maxTier(ToolKind.SWORD), "Combat: 10 tiers");
        assertEquals(11, c.maxTier(ToolKind.HOE), "PHASE-7 M7.4: one hoe per crop tier");
        assertEquals(12, c.maxTier(ToolKind.ROD), "PHASE-7 M7.4: one rod per fishing tier");
        assertEquals("coal", c.tier(ToolKind.PICKAXE, 1).orElseThrow().id());
        assertEquals("voidstone", c.tier(ToolKind.PICKAXE, 12).orElseThrow().id());
        assertEquals("warped", c.tier(ToolKind.AXE, 11).orElseThrow().id());
        assertEquals(Material.NETHERITE_PICKAXE, c.tier(ToolKind.PICKAXE, "voidstone").orElseThrow().material());
        assertEquals(3, c.tier(ToolKind.PICKAXE, "3").orElseThrow().tier());
    }

    @Test
    void forgePricesFollowTheBalanceSheet() throws Exception {
        ToolCatalog c = catalog();
        assertEquals(0, c.tier(ToolKind.PICKAXE, 1).orElseThrow().recipe().money(), "tier 1 = free starter kit");
        for (int t = 2; t <= 12; t++) {
            ToolCatalog.Recipe r = c.tier(ToolKind.PICKAXE, t).orElseThrow().recipe();
            double money = 15_000 * Math.pow(2.35, t - 2);
            double mats = 256 * Math.pow(1.35, t - 2);
            assertEquals(money, r.money(), money * 0.01, "money tier " + t);
            int total = r.materials().values().stream().mapToInt(Integer::intValue).sum();
            assertEquals(mats, total, 1.0, "materials tier " + t);
        }
    }

    @Test
    void skillLevelsMatchTheTierUnlocks() throws Exception {
        ToolCatalog c = catalog();
        List<Integer> mine = List.of(0, 5, 10, 15, 20, 25, 30, 35, 42, 50, 58, 66);
        for (int t = 1; t <= 12; t++) {
            assertEquals(mine.get(t - 1), c.tier(ToolKind.PICKAXE, t).orElseThrow().skillLevel());
        }
    }

    /** PHASE-7 M7.4 (PD14): hoe and rod Forge costs follow F(t) and M(t) like pickaxes; materials are the tier before. */
    @Test
    void hoeAndRodCostsFollowTheBalanceSheet() throws Exception {
        ToolCatalog c = catalog();
        for (ToolKind kind : List.of(ToolKind.HOE, ToolKind.ROD)) {
            assertEquals(0, c.tier(kind, 1).orElseThrow().recipe().money(), kind + " tier 1 = free starter kit");
            assertTrue(c.tier(kind, 1).orElseThrow().recipe().materials().isEmpty());
            for (int t = 2; t <= c.maxTier(kind); t++) {
                ToolCatalog.Recipe r = c.tier(kind, t).orElseThrow().recipe();
                double money = 15_000 * Math.pow(2.35, t - 2);
                double mats = 256 * Math.pow(1.35, t - 2);
                assertEquals(money, r.money(), money * 0.01, kind + " money tier " + t);
                assertEquals(1, r.materials().size(), kind + " one material per tier");
                assertEquals(mats, r.materials().values().iterator().next(), 1.0, kind + " materials tier " + t);
            }
        }
    }

    /** Hoe tier t needs the crop of tier t-1 and the Farming level of crop tier t (crops.yml defaults, WORLD §5.1). */
    @Test
    void hoeTiersFollowTheCropTiers() throws Exception {
        ToolCatalog c = catalog();
        List<Integer> farming = List.of(0, 5, 10, 15, 20, 25, 30, 35, 42, 50, 58);
        List<String> previousCrop = List.of("", "wheat", "carrot", "beetroot", "sugar_cane", "melon_slice", "cocoa_beans", "cactus",
                "sweet_berries", "nether_wart", "torchflower");
        for (int t = 1; t <= 11; t++) {
            ToolCatalog.TierDef def = c.tier(ToolKind.HOE, t).orElseThrow();
            assertEquals(farming.get(t - 1), def.skillLevel(), "Farming level, hoe tier " + t);
            assertTrue(def.material().name().endsWith("_HOE"), "a vanilla hoe (Farming XP needs a hoe)");
            if (t > 1) {
                assertEquals(previousCrop.get(t - 1), def.recipe().materials().keySet().iterator().next(), "material of hoe tier " + t);
            }
        }
        assertEquals("basic", c.tier(ToolKind.HOE, 1).orElseThrow().id(), "the starter hoe keeps its id");
        assertEquals(Material.STONE_HOE, c.tier(ToolKind.HOE, 1).orElseThrow().material());
        List<Integer> fishing = List.of(0, 5, 10, 15, 20, 25, 30, 35, 42, 50, 58, 66);
        for (int t = 1; t <= 12; t++) {
            assertEquals(fishing.get(t - 1), c.tier(ToolKind.ROD, t).orElseThrow().skillLevel(), "Fishing level, rod tier " + t);
            assertEquals(Material.FISHING_ROD, c.tier(ToolKind.ROD, t).orElseThrow().material());
        }
    }

    @Test
    void blueprintsAbilitiesAndSkins() throws Exception {
        ToolCatalog c = catalog();
        ToolCatalog.BlueprintDef magma = c.blueprint("magma_pickaxe").orElseThrow();
        assertEquals("auto_smelt", magma.ability());
        assertEquals(ToolKind.PICKAXE, magma.kind());
        assertTrue(magma.dropChance() > 0);
        assertTrue(c.ability("auto_smelt").orElseThrow().kinds().contains(ToolKind.PICKAXE));
        assertTrue(c.skin("frostbite").orElseThrow().fits(ToolKind.SWORD));
        assertEquals(Material.IRON_INGOT, c.smelted(Material.RAW_IRON).orElseThrow());
        assertEquals(1.0, c.islandXp(Material.COAL_ORE));
        assertEquals(0.0, c.islandXp(Material.DIRT));
    }

    @Test
    void recipeMissing() {
        ToolCatalog.Recipe r = new ToolCatalog.Recipe(100, Map.of("coal", 10, "raw_iron", 5));
        assertEquals(Map.of("coal", 4), r.missing(Map.of("coal", 6, "raw_iron", 9)));
        assertTrue(r.missing(Map.of("coal", 10, "raw_iron", 5)).isEmpty());
    }

    @Test
    void brokenEntriesAreReported() throws Exception {
        YamlConfiguration yml = new YamlConfiguration();
        yml.loadFromString("""
                tiers:
                  pickaxe:
                    - {id: coal, material: stone_pickaxe}
                    - {id: Bad!, material: nope}
                  shovel:
                    - {id: x, material: stone_shovel}
                blueprints:
                  broken: {kind: pickaxe, tier: 9, ability: none}
                """);
        List<String> problems = new ArrayList<>();
        ToolCatalog c = ToolCatalog.load(yml, problems::add, CHECKS);
        assertEquals(1, c.maxTier(ToolKind.PICKAXE));
        assertFalse(c.kinds().contains(ToolKind.SWORD));
        assertTrue(c.blueprints().isEmpty());
        assertEquals(3, problems.size(), problems.toString());
    }
}
