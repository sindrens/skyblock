package net.skybornmc.skyblock.shop;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ECON-SELL-SHOP-AH B1: the v3 → v4 migration of the live shop.yml (ECON-TABLES D lists). */
class ShopCatalogMigrationV4Test {

    private static YamlConfiguration copy(YamlConfiguration yaml) throws Exception {
        YamlConfiguration out = new YamlConfiguration();
        out.loadFromString(yaml.saveToString());
        return out;
    }

    @Test
    void untouchedV3FileBecomesTheBundledV4() throws Exception {
        ShopCatalogMigration.Result r = ShopCatalogMigration.migrateV4(ShopCatalogTest.v3(), ShopCatalogTest.v4(),
                ShopCatalogTest.v3()).orElseThrow();
        assertEquals(4, r.yaml().getInt("config-version"));
        ShopCatalog migrated = ShopCatalogTest.catalogOf(r.yaml());
        ShopCatalog expected = ShopCatalogTest.catalogOf(ShopCatalogTest.v4());
        assertEquals(expected.items(), migrated.items(), "same tabs, items, order and prices as a fresh install");
        assertEquals(expected.categories(), migrated.categories(), "groups too");
        assertEquals(expected.worthOnly(), migrated.worthOnly());
        assertTrue(r.changes().contains("removed oak_planks (was $2 in wood); its price stays only as block worth"),
                () -> String.join("\n", r.changes()));
        assertTrue(r.changes().contains("removed furnace (was $10 in essentials); its price stays only as block worth"));
        assertTrue(r.changes().contains("added oak_leaves to wood at price auto"));
        assertTrue(r.changes().contains("added rooted_dirt to stone-earth at price auto (it left worth-only)"));
    }

    @Test
    void ownerEditsSurvive() throws Exception {
        YamlConfiguration live = ShopCatalogTest.v3();
        live.set("categories.essentials.items.furnace", 25);                        // owner price on a dropped item
        live.set("categories.redstone.items.hopper", 999);                          // owner price on a kept item
        live.set("categories.decoration.items.jukebox", 900);                       // the owner's own item
        live.set("categories.wood.items.oak_log", Map.of("price", 8, "enabled", false));
        ShopCatalogMigration.Result r = ShopCatalogMigration.migrateV4(live, ShopCatalogTest.v4(), ShopCatalogTest.v3())
                .orElseThrow();
        ShopCatalog c = ShopCatalogTest.catalogOf(r.yaml());
        assertTrue(c.item("furnace").isEmpty());
        assertEquals(25, c.worth("furnace").orElseThrow(), "parked at the owner's price");
        assertEquals(999, c.item("hopper").orElseThrow().price());
        assertEquals(900, c.item("jukebox").orElseThrow().price());
        assertFalse(c.item("oak_log").orElseThrow().enabled());
        assertEquals(5, c.item("oak_leaves").orElseThrow().price(), "auto: 10 x $0.50");
    }

    @Test
    void anOwnersAutoPriceStaysAuto() throws Exception {
        YamlConfiguration live = ShopCatalogTest.v3();
        live.set("categories.stone-earth.items.dirt", "auto");
        ShopCatalogMigration.Result r = ShopCatalogMigration.migrateV4(live, ShopCatalogTest.v4(), ShopCatalogTest.v3())
                .orElseThrow();
        assertEquals("auto", r.yaml().getString("categories.stone-earth.items.dirt"));
        assertTrue(r.changes().stream().noneMatch(l -> l.startsWith("dirt:")), () -> String.join("\n", r.changes()));
    }

    @Test
    void theSecondRunDoesNothing() throws Exception {
        ShopCatalogMigration.Result r = ShopCatalogMigration.migrateV4(ShopCatalogTest.v3(), ShopCatalogTest.v4(),
                ShopCatalogTest.v3()).orElseThrow();
        assertTrue(ShopCatalogMigration.migrateV4(copy(r.yaml()), ShopCatalogTest.v4(), ShopCatalogTest.v3()).isEmpty());
        assertTrue(ShopCatalogMigration.migrateV4(ShopCatalogTest.v4(), ShopCatalogTest.v4(), ShopCatalogTest.v3())
                .isEmpty(), "a fresh v4 file is left alone");
        assertFalse(ShopModule.addMissing(copy(r.yaml()), ShopCatalogTest.v4()), "removed items are not added back");
    }

    @Test
    void aV1FileGoesAllTheWay() throws Exception {
        YamlConfiguration live = ShopCatalogTest.v1();
        live.set("categories.redstone.items.hopper", 999);
        YamlConfiguration v2 = ShopCatalogMigration.migrate(live, ShopCatalogTest.v2(), ShopCatalogTest.v1()).orElseThrow().yaml();
        YamlConfiguration v3 = ShopCatalogMigration.migrateV3(v2, ShopCatalogTest.v3(), ShopCatalogTest.v2()).orElseThrow().yaml();
        YamlConfiguration v4 = ShopCatalogMigration.migrateV4(v3, ShopCatalogTest.v4(), ShopCatalogTest.v3()).orElseThrow().yaml();
        assertEquals(4, v4.getInt("config-version"));
        YamlConfiguration v5 = ShopCatalogMigration.migrateV5(v4, ShopCatalogTest.bundled(), ShopCatalogTest.v4()).orElseThrow().yaml();
        ShopCatalog c = ShopCatalogTest.catalogOf(v5);
        assertEquals(5, v5.getInt("config-version"));
        assertEquals(999, c.item("hopper").orElseThrow().price());
        assertEquals(ShopCatalogTest.catalogOf(ShopCatalogTest.bundled()).size(), c.size(), "everything the bundled catalog sells");
        assertEquals(1_500, c.worth("ender_chest").orElseThrow());
        assertTrue(c.worth("oak_planks").isPresent(), "removed in v4: block worth only");
    }
}
