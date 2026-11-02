package net.skybornmc.skyblock.islandtools;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Island tools (Complete Developer Spec D4, T3, T7-T12). */
class ToolRulesTest {

    @Test
    void tierLadderAndMaterials() {
        assertEquals(List.of("wood", "stone", "iron", "gold", "diamond", "netherite"), ToolRules.TIERS);
        assertEquals("wooden_pickaxe", ToolRules.material(Kind.PICKAXE, 0));
        assertEquals("golden_hoe", ToolRules.material(Kind.HOE, 3));
        assertEquals("netherite_sword", ToolRules.material(Kind.SWORD, 5));
        assertEquals("netherite_axe", ToolRules.material(Kind.AXE, 99), "never past netherite");
    }

    @Test
    void bundledUpgradesAreSevenPerTool() {
        List<String> problems = new ArrayList<>();
        Upgrades u = Upgrades.parse(new IslandToolsConfig().upgrades, problems::add);
        assertEquals(List.of(), problems);
        for (Kind k : Kind.values()) {
            assertEquals(7, u.of(k).size(), "D4: 7 upgrades for " + k);
        }
        assertTrue(u.get(Kind.HOE, "row").isPresent());
        assertTrue(u.get(Kind.HOE, "storage").isPresent());
    }

    @Test
    void costsGrowAndRoundToTen() {
        Upgrades.Def d = new Upgrades.Def(Kind.PICKAXE, "x", Upgrades.Effect.DROPS, "", 5, 150, 2.2);
        assertEquals(150, d.cost(0));
        assertEquals(330, d.cost(1));
        assertTrue(d.cost(4) > d.cost(3));
    }

    @Test
    void brokenLinesAreSkipped() {
        List<String> problems = new ArrayList<>();
        Upgrades u = Upgrades.parse(List.of("pickaxe:a:nope:5:1:2", "rod:b:haste:2:1:2", "axe:c:enchant:3:1:2", "hoe:d:row:4:300:3"), problems::add);
        assertEquals(3, problems.size());
        assertEquals(1, u.of(Kind.HOE).size());
    }

    @Test
    void levelsRoundTrip() {
        Map<String, Integer> m = Upgrades.decode("efficiency=3, fortune=1,bad=x,zero=0");
        assertEquals(Map.of("efficiency", 3, "fortune", 1), m);
        assertEquals("efficiency=3,fortune=1", Upgrades.encode(m));
    }

    @Test
    void hoeRowAndStorage() {
        List<Integer> rows = List.of(1, 3, 5, 7, 9);
        assertEquals(1, ToolRules.row(0, rows));
        assertEquals(9, ToolRules.row(4, rows));
        assertEquals(9, ToolRules.row(7, rows));
        assertEquals(256, ToolRules.storage(0, 256, 3), "T9: starts at 256");
        assertEquals(768, ToolRules.storage(1, 256, 3), "about x3 per step");
        assertEquals(5, ToolRules.rowOffsets(5, 0f).length);
        assertEquals(-2, ToolRules.rowOffsets(5, 0f)[0][0], "facing south: the row runs east-west");
        assertEquals(-2, ToolRules.rowOffsets(5, 90f)[0][1], "facing west: the row runs north-south");
    }

    @Test
    void regrowFromSixtyToFifteenWithAFloor() {
        assertEquals(60, ToolRules.regrowSeconds(0, 0, 60, 15, 120, 10), 1e-9, "T11: 60 s at the start");
        assertEquals(15, ToolRules.regrowSeconds(120, 0, 60, 15, 120, 10), 1e-9, "T11: 15 s at the top upgrade");
        assertEquals(15, ToolRules.regrowSeconds(500, 0, 60, 15, 120, 10), 1e-9);
        assertEquals(10, ToolRules.regrowSeconds(120, 100, 60, 15, 120, 10), 1e-9, "G11: perks never go under the floor");
        assertEquals(6, ToolRules.burstPercent(120, 0.05, 10), 1e-9);
        assertEquals(10, ToolRules.burstPercent(1000, 0.05, 10), 1e-9);
    }

    @Test
    void confirmAndRounding() {
        assertFalse(ToolRules.needsConfirm(4999, 5000));
        assertTrue(ToolRules.needsConfirm(5000, 5000));
        assertFalse(ToolRules.needsConfirm(10_000, 0));
        assertEquals(2, ToolRules.roll(2.3, 0.5));
        assertEquals(3, ToolRules.roll(2.3, 0.2));
    }
}
