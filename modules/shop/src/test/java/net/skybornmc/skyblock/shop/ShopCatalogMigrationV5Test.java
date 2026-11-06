package net.skybornmc.skyblock.shop;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Owner 2 Nov 2026: the v4 -> v5 move to the Building Blocks / Nature / Farming / Mob Drops tabs. */
class ShopCatalogMigrationV5Test {

    @Test
    void untouchedV4FileBecomesTheBundledV5() throws Exception {
        ShopCatalogMigration.Result r = ShopCatalogMigration.migrateV5(ShopCatalogTest.v4(), ShopCatalogTest.bundled(),
                ShopCatalogTest.v4()).orElseThrow();
        assertEquals(5, r.yaml().getInt("config-version"));
        ShopCatalog migrated = ShopCatalogTest.catalogOf(r.yaml());
        ShopCatalog expected = ShopCatalogTest.catalog();
        assertEquals(expected.items(), migrated.items(), "same tabs, items, order and prices as a fresh install");
        assertEquals(expected.categories(), migrated.categories());
        assertTrue(r.changes().contains("oak_log: moved from wood to building"), () -> String.join("\n", r.changes()));
        assertTrue(r.changes().contains("added bone to mob-drops at price auto"));
    }

    @Test
    void ownerEditsMoveWithTheirItems() throws Exception {
        YamlConfiguration live = ShopCatalogTest.v4();
        live.set("categories.wood.items.oak_log", Map.of("price", 12, "enabled", false));
        live.set("categories.special.items.jukebox", 900); // the owner's own item in a tab that is gone
        ShopCatalogMigration.Result r = ShopCatalogMigration.migrateV5(live, ShopCatalogTest.bundled(), ShopCatalogTest.v4())
                .orElseThrow();
        ShopCatalog c = ShopCatalogTest.catalogOf(r.yaml());
        assertEquals("building", c.item("oak_log").orElseThrow().category());
        assertEquals(12, c.item("oak_log").orElseThrow().price());
        assertFalse(c.item("oak_log").orElseThrow().enabled());
        assertEquals("decoration", c.item("jukebox").orElseThrow().category());
        assertTrue(ShopCatalogMigration.migrateV5(r.yaml(), ShopCatalogTest.bundled(), ShopCatalogTest.v4()).isEmpty());
        assertEquals("building", ShopCatalogMigration.TAB_ALIASES.get("wood"));
    }
}
