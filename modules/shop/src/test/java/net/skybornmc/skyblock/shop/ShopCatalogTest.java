package net.skybornmc.skyblock.shop;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The bundled shop.yml and the parser. */
class ShopCatalogTest {

    static YamlConfiguration bundled() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        try (InputStream in = ShopCatalogTest.class.getClassLoader().getResourceAsStream("shop.yml")) {
            yaml.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return yaml;
    }

    static ShopCatalog catalog() throws Exception {
        List<String> problems = new ArrayList<>();
        ShopCatalog c = ShopCatalog.load(bundled(), ShopCatalogTest::realItem, problems::add);
        assertTrue(problems.isEmpty(), () -> String.join("\n", problems));
        return c;
    }

    /** Every id must be a real item of the Paper API we build against (no server needed for the enum lookup). */
    static boolean realItem(String id) {
        Material m = Material.matchMaterial(id);
        return m != null && !m.name().startsWith("LEGACY_") && m != Material.AIR;
    }

    @Test
    void bundledShopLoadsWithRealItemsOnly() throws Exception {
        ShopCatalog c = catalog();
        assertTrue(c.size() > 200, "a real builder shop");
        assertEquals(4, c.item("dirt").orElseThrow().price());
        assertEquals(2_400, c.item("redstone_block").orElseThrow().price());
        assertEquals(9, c.item("redstone_block").orElseThrow().contains().get("redstone"));
    }

    @Test
    void categoriesAreThePhaseDocOnes() throws Exception {
        // PHASE-3 M3.5: Building blocks, Wood, Stone & bricks, Glass & colors, Decoration, Redstone, Farming, Utility.
        assertEquals(List.of("building-blocks", "wood", "stone-bricks", "glass-colors", "decoration", "redstone", "farming", "utility"),
                catalog().categories().stream().map(ShopCatalog.Category::id).toList());
        Set<Integer> slots = new HashSet<>();
        for (ShopCatalog.Category cat : catalog().categories()) {
            assertTrue(ShopCatalog.INNER_SLOTS.contains(cat.slot()), cat.id());
            assertTrue(slots.add(cat.slot()), "two categories in slot " + cat.slot());
            assertFalse(cat.enabledItems().isEmpty(), cat.id());
            assertTrue(realItem(cat.icon()), cat.icon());
        }
    }

    @Test
    void bundledShopSaysTheOwnerApprovesPrices() throws Exception {
        String text = Files.readString(Path.of(ShopCatalogTest.class.getClassLoader().getResource("shop.yml").toURI()));
        assertTrue(text.contains("THE OWNER APPROVES THESE PRICES BEFORE LAUNCH"));
    }

    @Test
    void brokenEntriesAreSkippedWithAProblem() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                categories:
                  a:
                    icon: stone
                    slot: 99
                    items:
                      stone: 5
                      dirt: 0
                      not_an_item: 4
                      Glass: 3
                      sand: { price: 4, enabled: false }
                      gravel: { price: 4, contains: { flint: 0 } }
                      clay: { price: 4, requires: { cooking: 3 } }
                      oak_log: { price: 8, requires: { farming: 3 } }
                  b:
                    icon: nonsense
                    items:
                      stone: 7
                """);
        List<String> problems = new ArrayList<>();
        ShopCatalog c = ShopCatalog.load(yaml, ShopCatalogTest::realItem, problems::add);
        assertEquals(List.of("stone", "sand", "oak_log"), c.items().stream().map(ShopCatalog.Item::id).toList());
        assertFalse(c.item("sand").orElseThrow().enabled());
        assertEquals(3, c.item("oak_log").orElseThrow().requires().get("farming"));
        assertEquals(5, c.item("stone").orElseThrow().price(), "listed twice: first one kept");
        assertEquals(List.of("stone", "sand", "oak_log"), c.category("a").orElseThrow().items().stream()
                .map(ShopCatalog.Item::id).toList());
        assertEquals(List.of(), c.category("b").orElseThrow().items());
        assertTrue(ShopCatalog.INNER_SLOTS.contains(c.category("a").orElseThrow().slot()), "bad slot replaced");
        assertEquals("chest", c.category("b").orElseThrow().icon());
        assertEquals(8, problems.size(), () -> String.join("\n", problems));
    }

    @Test
    void missingFileGivesAnEmptyShop() {
        List<String> problems = new ArrayList<>();
        assertEquals(0, ShopCatalog.load(null, id -> true, problems::add).size());
        assertEquals(1, problems.size());
    }

    @Test
    void newBundledItemsAreAddedToAnOwnersFile() throws Exception {
        YamlConfiguration owner = new YamlConfiguration();
        owner.loadFromString("""
                categories:
                  wood:
                    icon: oak_log
                    items:
                      oak_log: 99
                  decoration:
                    items:
                      dirt: 1
                """);
        assertTrue(ShopModule.addMissing(owner, bundled()));
        assertEquals(99, owner.getInt("categories.wood.items.oak_log"), "owner prices are kept");
        assertTrue(owner.contains("categories.wood.items.spruce_log"));
        assertFalse(owner.contains("categories.building-blocks.items.dirt"), "moved items are not added back");
        assertTrue(owner.contains("categories.utility"));
        assertFalse(ShopModule.addMissing(owner, bundled()), "nothing left to add");
    }
}
