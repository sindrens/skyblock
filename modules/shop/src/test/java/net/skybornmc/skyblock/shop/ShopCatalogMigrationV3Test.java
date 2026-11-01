package net.skybornmc.skyblock.shop;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** MENUS-V3 6.3: the data-driven v2 → v3 migration of the live shop.yml. */
class ShopCatalogMigrationV3Test {

    private static YamlConfiguration copy(YamlConfiguration yaml) throws Exception {
        YamlConfiguration out = new YamlConfiguration();
        out.loadFromString(yaml.saveToString());
        return out;
    }

    private static ShopCatalog load(YamlConfiguration yaml) {
        List<String> problems = new ArrayList<>();
        ShopCatalog c = ShopCatalogTest.load(yaml, problems);
        assertTrue(problems.isEmpty(), () -> String.join("\n", problems));
        return c;
    }

    @Test
    void untouchedV2FileBecomesTheBundledV3() throws Exception {
        ShopCatalogMigration.Result r = ShopCatalogMigration.migrateV3(ShopCatalogTest.v2(), ShopCatalogTest.bundled(),
                ShopCatalogTest.v2()).orElseThrow();
        assertEquals(3, r.yaml().getInt("config-version"));
        ShopCatalog migrated = load(r.yaml());
        ShopCatalog expected = ShopCatalogTest.catalog();
        assertEquals(expected.items(), migrated.items(), "same tabs, items, order and prices as a fresh install");
        assertEquals(expected.categories(), migrated.categories(), "groups too");
        assertEquals(expected.worthOnly(), migrated.worthOnly());
        assertEquals(ShopCatalogGroupsTest.ORDER, migrated.categories().stream().map(ShopCatalog.Category::id).toList());
        // The live Spawners tab is the economy design's: 20 mobs, tiers 1 to 4 (ECON-SPAWNERS).
        assertEquals(100_000, migrated.item("spawner_pig").orElseThrow().price());
        assertEquals(1_000_000, migrated.item("spawner_zombified_piglin").orElseThrow().price());
        assertTrue(migrated.item("spawner_squid").isEmpty());
        assertTrue(r.changes().contains("removed spawner squid (not sold in Shop v3)"), () -> String.join("\n", r.changes()));
        assertTrue(r.changes().contains("spawner pig: price $117000 -> $100000"));
        assertTrue(r.changes().contains("added spawner creeper to spawners at $500000"));
    }

    @Test
    void droppedItemsAreParkedAndNewItemsAddedVerbatim() throws Exception {
        YamlConfiguration v3 = copy(ShopCatalogTest.bundled());
        v3.set("categories.wood.items.oak_planks", null);                          // ECON removes it
        v3.set("categories.stone-earth.items.rooted_dirt", Map.of("price", "auto")); // ECON adds it back from worth-only
        v3.set("categories.stone-earth.items.dripstone_block", "auto");
        v3.set("worth-only.rooted_dirt", null);
        v3.set("worth-only.dripstone_block", null);
        YamlConfiguration live = ShopCatalogTest.v2();
        live.set("categories.wood.items.oak_planks", 3);                           // owner price on a dropped item
        ShopCatalogMigration.Result r = ShopCatalogMigration.migrateV3(live, v3, ShopCatalogTest.v2()).orElseThrow();
        YamlConfiguration out = r.yaml();
        assertFalse(out.contains("categories.wood.items.oak_planks"));
        assertEquals(3, out.getInt("worth-only.oak_planks"), "parked at its live price");
        assertTrue(r.changes().contains("removed oak_planks (was $3 in wood); its price stays only as block worth"),
                () -> String.join("\n", r.changes()));
        assertEquals("auto", out.getString("categories.stone-earth.items.rooted_dirt.price"), "auto is copied verbatim");
        assertEquals("auto", out.getString("categories.stone-earth.items.dripstone_block"));
        assertFalse(out.contains("worth-only.rooted_dirt"), "a sold item leaves worth-only");
        assertFalse(out.contains("worth-only.dripstone_block"));
        assertTrue(r.changes().contains("added rooted_dirt to stone-earth at price auto (it left worth-only)"));
    }

    @Test
    void ownerEditsSurvive() throws Exception {
        YamlConfiguration live = ShopCatalogTest.v2();
        live.set("categories.redstone.items.hopper", 999);
        live.set("categories.essentials.items.anvil", Map.of("price", 400, "enabled", false));
        live.set("categories.farming.items.wheat_seeds", Map.of("price", 10, "requires", Map.of("farming", 4)));
        live.set("categories.decoration.items.jukebox", 900);                       // the owner's own item
        live.set("categories.wood.icon", "spruce_log");                             // the owner's own tab icon
        live.set("worth-only.ender_chest", 5_000);                                  // owner-edited worth
        ShopCatalogMigration.Result r = ShopCatalogMigration.migrateV3(live, ShopCatalogTest.bundled(), ShopCatalogTest.v2())
                .orElseThrow();
        ShopCatalog c = load(r.yaml());
        assertEquals(999, c.item("hopper").orElseThrow().price());
        assertFalse(c.item("anvil").orElseThrow().enabled());
        assertEquals("essentials", c.item("anvil").orElseThrow().category());
        assertEquals(4, c.item("wheat_seeds").orElseThrow().requires().get("farming"));
        assertEquals(900, c.item("jukebox").orElseThrow().price());
        assertEquals("decoration", c.item("jukebox").orElseThrow().category());
        assertTrue(r.changes().stream().anyMatch(l -> l.startsWith("kept your own item jukebox")));
        assertEquals("spruce_log", c.category("wood").orElseThrow().icon());
        assertEquals(5_000, c.worth("ender_chest").orElseThrow());
        assertEquals(1, c.item("stone").orElseThrow().contains().get("cobblestone"), "bundled contains survives");
        assertEquals(List.of("logs", "other"), c.category("wood").orElseThrow().groups().stream().map(ShopCatalog.Group::id).toList(),
                "groups come from the bundled file (an empty group is left out)");
    }

    @Test
    void aChangedDefaultMovesOnlyWhenUntouched() throws Exception {
        YamlConfiguration v3 = copy(ShopCatalogTest.bundled());
        v3.set("categories.stone-earth.items.dirt", 5);
        v3.set("categories.stone-earth.items.sand", 6);
        YamlConfiguration live = ShopCatalogTest.v2();
        live.set("categories.stone-earth.items.sand", 9);
        ShopCatalogMigration.Result r = ShopCatalogMigration.migrateV3(live, v3, ShopCatalogTest.v2()).orElseThrow();
        assertEquals(5, r.yaml().getInt("categories.stone-earth.items.dirt"));
        assertEquals(9, r.yaml().getInt("categories.stone-earth.items.sand"));
        assertTrue(r.changes().contains("dirt: price $4 -> $5 (Shop v3)"), () -> String.join("\n", r.changes()));
        assertTrue(r.changes().contains("sand: kept your price $9 (Shop v3 default would be $6)"));
    }

    @Test
    void theSecondRunDoesNothing() throws Exception {
        ShopCatalogMigration.Result r = ShopCatalogMigration.migrateV3(ShopCatalogTest.v2(), ShopCatalogTest.bundled(),
                ShopCatalogTest.v2()).orElseThrow();
        assertTrue(ShopCatalogMigration.migrateV3(copy(r.yaml()), ShopCatalogTest.bundled(), ShopCatalogTest.v2()).isEmpty());
        assertTrue(ShopCatalogMigration.migrateV3(ShopCatalogTest.bundled(), ShopCatalogTest.bundled(), ShopCatalogTest.v2())
                .isEmpty(), "a fresh v3 file is left alone");
        assertFalse(ShopModule.addMissing(copy(r.yaml()), ShopCatalogTest.bundled()), "nothing left to add");
    }

    @Test
    void aV1FileGoesThroughV2ThenV3() throws Exception {
        YamlConfiguration live = ShopCatalogTest.v1();
        live.set("categories.redstone.items.hopper", 999);
        ShopCatalogMigration.Result v2 = ShopCatalogMigration.migrate(live, ShopCatalogTest.v2(), ShopCatalogTest.v1()).orElseThrow();
        ShopCatalogMigration.Result v3 = ShopCatalogMigration.migrateV3(v2.yaml(), ShopCatalogTest.bundled(), ShopCatalogTest.v2())
                .orElseThrow();
        ShopCatalog c = load(v3.yaml());
        assertEquals(3, v3.yaml().getInt("config-version"));
        assertEquals(999, c.item("hopper").orElseThrow().price());
        assertEquals(ShopCatalogTest.catalog().size(), c.size());
        assertEquals(1_500, c.worth("ender_chest").orElseThrow());
    }
}
