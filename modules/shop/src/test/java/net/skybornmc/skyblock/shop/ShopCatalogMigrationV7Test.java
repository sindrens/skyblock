package net.skybornmc.skyblock.shop;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Batch 9 (Q-42): a live v6 shop.yml sells the removed items again, once. */
class ShopCatalogMigrationV7Test {

    /** The frozen v6 file as an old live file: the 19 workstations still parked under worth-only. */
    private static YamlConfiguration oldLive() throws Exception {
        YamlConfiguration live = ShopCatalogTest.resource(ShopModule.V6_DEFAULTS);
        for (String tab : List.of("essentials", "redstone")) {
            var items = live.getConfigurationSection("categories." + tab + ".items");
            for (String id : List.of("crafting_table", "furnace", "chest", "repeater", "lever")) {
                if (items.contains(id)) {
                    live.set("worth-only." + id, items.get(id));
                    items.set(id, null);
                }
            }
        }
        return live;
    }

    @Test
    void parkedItemsAreSoldAgainAndUneditedRowsFollow() throws Exception {
        YamlConfiguration live = oldLive();
        live.set("categories.redstone.groups.power", List.of("lever", "redstone_torch")); // an owner edit: kept
        YamlConfiguration bundled = ShopCatalogTest.bundled();
        var result = ShopCatalogMigration.itemsV7(live, bundled, ShopCatalogTest.resource(ShopModule.V6_DEFAULTS));
        assertTrue(result.isPresent());
        assertEquals(7, live.getInt("config-version"));
        for (String id : ShopListRulesTest.REMOVED) {
            assertFalse(live.contains("worth-only." + id), id + " left worth-only");
        }
        assertTrue(live.contains("categories.building.items.oak_planks"));
        assertTrue(live.contains("categories.essentials.items.crafting_table"), "parked before the version: back too");
        assertTrue(live.contains("categories.redstone.items.repeater"));
        assertEquals(bundled.getStringList("categories.building.groups.planks"), live.getStringList("categories.building.groups.planks"));
        assertEquals(List.of("lever", "redstone_torch"), live.getStringList("categories.redstone.groups.power"), "edited rows stay");
        assertTrue(live.contains("worth-only.packed_mud"), "items that stay out keep their worth");

        ShopModule.addMissing(live, bundled);
        List<String> problems = new ArrayList<>();
        ShopCatalog c = ShopCatalogTest.load(live, problems);
        assertTrue(c.item("arrow").isPresent(), "new mob drops are added afterwards");
        assertEquals(ShopCatalogTest.catalog().size(), c.size(), "same items as a fresh install");
        assertTrue(ShopCatalogMigration.itemsV7(live, bundled, ShopCatalogTest.resource(ShopModule.V6_DEFAULTS)).isEmpty(), "once");
    }
}
