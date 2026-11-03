package net.skybornmc.skyblock.shop;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ECON-TABLES D (Part B): what the bundled shop sells, how auto prices and the buy multiplier work. */
class ShopListRulesTest {

    static final List<String> REMOVED = List.of(
            "crafting_table", "furnace", "smoker", "chest", "barrel", "composter", "grindstone", "ladder", "baked_potato",
            "oak_planks", "spruce_planks", "birch_planks", "jungle_planks", "acacia_planks", "dark_oak_planks", "mangrove_planks",
            "cherry_planks", "pale_oak_planks", "crimson_planks", "warped_planks", "bamboo_planks",
            "coarse_dirt", "sandstone", "stone_bricks", "mossy_stone_bricks", "mossy_cobblestone", "andesite", "polished_andesite",
            "polished_diorite", "polished_granite", "polished_deepslate", "deepslate_bricks",
            "polished_blackstone", "polished_blackstone_bricks", "end_stone_bricks", "quartz_bricks", "quartz_pillar", "packed_ice",
            "blue_ice", "glass_pane", "painting", "armor_stand", "white_banner", "chiseled_bookshelf", "moss_carpet",
            "redstone_torch", "repeater", "dropper", "lever", "stone_button", "oak_button", "stone_pressure_plate", "note_block");

    static final Set<String> OWNER_PRICES = Set.of("hopper", "bone_meal", "flint_and_steel");

    @Test
    void the53RemovedItemsAreBlockWorthOnly() throws Exception {
        assertEquals(53, REMOVED.size());
        ShopCatalog c = ShopCatalogTest.catalog();
        List<String> wrong = new ArrayList<>();
        for (String id : REMOVED) {
            if (c.item(id).isPresent() || c.worth(id).isEmpty()) {
                wrong.add(id);
            }
        }
        assertTrue(wrong.isEmpty(), "still sold or without block worth: " + wrong);
    }

    @Test
    void the46AddedItemsUseAutoPrices() throws Exception {
        YamlConfiguration yaml = ShopCatalogTest.bundled();
        ShopCatalog c = ShopCatalogTest.catalog();
        int auto = 0;
        for (ShopCatalog.Item item : c.items()) {
            Object raw = yaml.get("categories." + item.category() + ".items." + item.id());
            if (ShopCatalog.isAuto(ShopCatalogMigration.priceValue(raw))) {
                auto++;
                assertTrue(item.price() >= 5, item.id() + " costs " + item.price());
            }
        }
        assertEquals(46 + 19, auto, "ECON-TABLES D3 plus the 19 mob drops");
        assertEquals(5, c.item("oak_leaves").orElseThrow().price(), "10 x $0.50");
        assertEquals(10, c.item("blue_orchid").orElseThrow().price(), "10 x $1");
        assertEquals(15, c.item("peony").orElseThrow().price(), "10 x $1.50, rounded up");
        assertEquals(20, c.item("crimson_fungus").orElseThrow().price());
    }

    @Test
    void ownerPricesStay() throws Exception {
        ShopCatalog c = ShopCatalogTest.catalog();
        assertEquals(Map.of("hopper", 2_500L, "bone_meal", 30L, "flint_and_steel", 40L),
                Map.of("hopper", c.item("hopper").orElseThrow().price(), "bone_meal", c.item("bone_meal").orElseThrow().price(),
                        "flint_and_steel", c.item("flint_and_steel").orElseThrow().price()));
        assertTrue(OWNER_PRICES.stream().noneMatch(REMOVED::contains));
    }

    @Test
    void autoWithoutASellPriceIsSkipped() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                categories:
                  a:
                    icon: stone
                    items:
                      dirt: auto
                      stone: { price: auto, enabled: false }
                      sand: 4
                """);
        List<String> problems = new ArrayList<>();
        ShopCatalog.Pricing pricing = new ShopCatalog.Pricing(id -> id.equals("stone") ? 1.01 : Double.NaN, 10, 1.0);
        ShopCatalog c = ShopCatalog.load(yaml, ShopCatalogTest::realItem, t -> false, pricing, problems::add);
        assertTrue(c.item("dirt").isEmpty());
        assertEquals(11, c.item("stone").orElseThrow().price(), "ceil(10 x 1.01)");
        assertEquals(1, problems.size(), () -> String.join("\n", problems));
        assertTrue(problems.get(0).contains("needs a sell price"));
    }

    @Test
    void buyMultiplierScalesEveryPriceButNotBlockWorth() throws Exception {
        List<String> problems = new ArrayList<>();
        ShopCatalog.Pricing base = ShopCatalogTest.pricing();
        ShopCatalog.Pricing doubled = new ShopCatalog.Pricing(base.sellBase(), 10, 2.0);
        ShopCatalog c = ShopCatalog.load(ShopCatalogTest.bundled(), ShopCatalogTest::realItem,
                ShopCatalogTest.SPAWNER_TYPES::contains, doubled, problems::add);
        assertTrue(problems.isEmpty(), () -> String.join("\n", problems));
        assertEquals(5_000, c.item("hopper").orElseThrow().price());
        assertEquals(10, c.item("oak_leaves").orElseThrow().price());
        assertEquals(2_000_000, c.item("spawner_blaze").orElseThrow().price());
        assertEquals(7_000, c.item("cactus").orElseThrow().price(), "3,500 x 2");
        assertEquals(2_500, c.worth("hopper").orElseThrow(), "block worth stays at the base price");
        ShopCatalog.Pricing odd = new ShopCatalog.Pricing(base.sellBase(), 10, 1.3);
        assertEquals(620, odd.scale(475), "617.5 -> 2 significant figures");
        assertEquals(10, odd.scale(8), "10.4 -> 10");
        assertEquals(2_000_000, odd.scale(1_500_000), "1,950,000 -> 2 significant figures");
        assertEquals(475, base.scale(475), "1.0 never rounds");
    }
}
