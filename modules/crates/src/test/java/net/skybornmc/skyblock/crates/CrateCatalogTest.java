package net.skybornmc.skyblock.crates;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrateCatalogTest {

    @Test
    void bundledHasTheSevenCratesInOrder() {
        CrateCatalog c = Fixtures.bundled();
        assertEquals(List.of("ember", "cinder", "blaze", "inferno", "phoenix", "eclipse", "conqueror"), c.ids());
        assertEquals(List.of("ember", "cinder", "blaze", "inferno", "phoenix", "eclipse"),
                c.visible().stream().map(CrateCatalog.Crate::id).toList(), "Conqueror stays hidden until KOTH (RX9)");
    }

    @Test
    void everyBundledTableAddsUpTo100() {
        for (CrateCatalog.Crate crate : Fixtures.bundled().crates()) {
            assertEquals(100.0, crate.totalChance(), 1e-9, crate.id());
            double shares = crate.rewards().stream().mapToDouble(crate::percent).sum();
            assertEquals(100.0, shares, 1e-9, crate.id());
            assertTrue(crate.hasTop(), crate.id() + " needs a top band for pity");
            assertTrue(crate.rewards().size() <= PreviewMenu.REWARD_SLOTS.length, crate.id() + " fits the preview");
        }
    }

    @Test
    void topBandsAndPityMatchThePlanOverview() {
        CrateCatalog c = Fixtures.bundled();
        double[][] expected = {{8, 30}, {9, 28}, {11, 23}, {14, 18}, {14, 18}, {8, 10}, {12, 20}};
        List<CrateCatalog.Crate> crates = c.crates();
        for (int i = 0; i < crates.size(); i++) {
            assertEquals(expected[i][0], crates.get(i).topPercent(), 1e-9, crates.get(i).id() + " top band");
            assertEquals((int) expected[i][1], crates.get(i).pity(), crates.get(i).id() + " pity");
        }
    }

    @Test
    void keysPointAtExistingCrates() {
        CrateCatalog c = Fixtures.bundled();
        for (CrateCatalog.Crate crate : c.crates()) {
            for (CrateCatalog.Reward r : crate.rewards()) {
                if (r.type() == RewardType.KEY) {
                    assertTrue(c.crate(r.crate()).isPresent(), crate.id() + "." + r.id());
                }
            }
        }
    }

    @Test
    void brokenEntriesAreSkippedWithAReason() {
        List<String> problems = new ArrayList<>();
        CrateCatalog c = Fixtures.parse("""
                crates:
                  a:
                    rarity: rare
                    pity: 5
                    rewards:
                      - {id: money, type: money, chance: 50, minutes: 2}
                      - {id: money, type: crystals, chance: 10, base: 5}
                      - {id: bad_type, type: rainbow, chance: 10}
                      - {id: no_chance, type: crystals, base: 5}
                      - {id: key, type: key, chance: 40, crate: nowhere, top: true}
                      - {id: no_minutes, type: money, chance: 5}
                  Bad-Id:
                    rewards: []
                """, problems);
        CrateCatalog.Crate a = c.crate("a").orElseThrow();
        assertEquals(List.of("money", "key"), a.rewards().stream().map(CrateCatalog.Reward::id).toList());
        assertEquals(RewardType.COMING_SOON, a.reward("key").orElseThrow().type(), "a key to an unknown crate pays crystals");
        assertEquals("rare", a.reward("money").orElseThrow().rarity(), "rarity defaults to the crate's");
        assertFalse(c.crate("bad-id").isPresent());
        assertTrue(problems.stream().anyMatch(p -> p.contains("duplicate reward id")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("unknown type")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("'chance' must be above 0")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("unknown crate 'nowhere'")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("'minutes' must be at least 1")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("Bad-Id")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.contains("instead of 100")), problems.toString());
    }

    @Test
    void mergeAddsOnlyNewCrates() throws Exception {
        org.bukkit.configuration.file.YamlConfiguration file = new org.bukkit.configuration.file.YamlConfiguration();
        file.loadFromString("""
                crates:
                  ember:
                    pity: 99
                    rewards:
                      - {id: money, type: money, chance: 100, minutes: 1}
                """);
        assertTrue(CatalogMerge.addMissing(file, Fixtures.resource(CrateCatalog.FILE)));
        List<String> problems = new ArrayList<>();
        CrateCatalog c = CrateCatalog.load(file, problems::add);
        assertEquals(7, c.size());
        assertEquals(99, c.crate("ember").orElseThrow().pity(), "an existing crate keeps the owner's edits");
        assertEquals(1, c.crate("ember").orElseThrow().rewards().size(), "deleted rewards never come back");
        assertEquals(Fixtures.bundled().crate("blaze").orElseThrow().rewards().size(), c.crate("blaze").orElseThrow().rewards().size());
        assertFalse(CatalogMerge.addMissing(file, Fixtures.resource(CrateCatalog.FILE)), "nothing left to add");
    }
}
