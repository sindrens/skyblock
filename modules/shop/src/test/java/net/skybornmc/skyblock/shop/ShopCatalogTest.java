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
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The bundled shop.yml (Shop v2), the parser and the one-time v1 → v2 catalog migration. */
class ShopCatalogTest {

    /** stacking.yml defaults: tier 1 and 2 spawner types (owner SK3/SK4). */
    static final Set<String> SPAWNER_TYPES = Set.of("pig", "cow", "chicken", "sheep", "rabbit",
            "zombie", "skeleton", "spider", "cave_spider", "slime", "squid");

    static YamlConfiguration resource(String name) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        try (InputStream in = ShopCatalogTest.class.getClassLoader().getResourceAsStream(name)) {
            yaml.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return yaml;
    }

    static YamlConfiguration bundled() throws Exception {
        return resource("shop.yml");
    }

    static YamlConfiguration v1() throws Exception {
        return resource(ShopModule.V1_DEFAULTS);
    }

    static ShopCatalog load(YamlConfiguration yaml, List<String> problems) {
        return ShopCatalog.load(yaml, ShopCatalogTest::realItem, SPAWNER_TYPES::contains, problems::add);
    }

    static ShopCatalog catalog() throws Exception {
        List<String> problems = new ArrayList<>();
        ShopCatalog c = load(bundled(), problems);
        assertTrue(problems.isEmpty(), () -> String.join("\n", problems));
        return c;
    }

    /** Every id must be a real item of the Paper API we build against (no server needed for the enum lookup). */
    static boolean realItem(String id) {
        Material m = Material.matchMaterial(id);
        return m != null && !m.name().startsWith("LEGACY_") && m != Material.AIR;
    }

    // ------------------------------------------------------------------ the bundled catalog (SHOP_DESIGN §2)

    @Test
    void nineTabsInTheDesignOrderOnTheTopRow() throws Exception {
        ShopCatalog c = catalog();
        assertEquals(List.of("essentials", "farming", "wood", "stone-earth", "special", "colors", "decoration", "redstone", "spawners"),
                c.categories().stream().map(ShopCatalog.Category::id).toList());
        for (int i = 0; i < c.categories().size(); i++) {
            ShopCatalog.Category tab = c.categories().get(i);
            assertEquals(i, tab.slot(), "tab " + tab.id() + " sits in slot " + i);
            assertTrue(realItem(tab.icon()), tab.icon());
            assertFalse(tab.enabledItems().isEmpty(), tab.id());
            int pages = tab.id().equals("colors") ? 2 : 1;
            assertTrue(tab.items().size() <= 28 * pages, tab.id() + " fits " + pages + " page(s)");
        }
        assertEquals(ShopCatalog.Kind.SPAWNER, c.category("spawners").orElseThrow().kind());
    }

    @Test
    void about253ItemsWithTheDesignCounts() throws Exception {
        ShopCatalog c = catalog();
        assertEquals(253, c.size());
        int[] expected = {26, 27, 24, 28, 28, 53, 28, 28, 11};
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], c.categories().get(i).items().size(), c.categories().get(i).id());
        }
    }

    @Test
    void ownerPriceDecisions() throws Exception {
        ShopCatalog c = catalog();
        assertEquals(2_500, c.item("hopper").orElseThrow().price(), "S6");
        assertEquals(30, c.item("bone_meal").orElseThrow().price(), "S7");
        assertEquals(40, c.item("flint_and_steel").orElseThrow().price(), "S4");
        assertEquals(150, c.item("fishing_rod").orElseThrow().price());
        assertEquals(60, c.item("shears").orElseThrow().price());
        assertEquals(15, c.item("bread").orElseThrow().price());
        assertEquals(15, c.item("baked_potato").orElseThrow().price());
        assertEquals(4, c.item("dirt").orElseThrow().price(), "unchanged prices stay");
        assertEquals(1, c.item("stone").orElseThrow().contains().get("cobblestone"));
    }

    @Test
    void cleanerCatalogLeavesOutWhatS3AndS6Remove() throws Exception {
        ShopCatalog c = catalog();
        for (String gone : List.of("ender_chest", "redstone_block", "stripped_oak_log", "red_dye", "red_wool", "red_carpet",
                "loom", "lectern", "smithing_table", "cartography_table", "fletching_table", "blue_orchid", "sea_pickle")) {
            assertTrue(c.item(gone).isEmpty(), gone + " is not sold");
        }
        assertTrue(c.item("white_wool").isPresent(), "white wool stays (Colors)");
        assertEquals(2_400, c.worth("redstone_block").orElseThrow(), "removed items keep their block worth");
        assertEquals(1_500, c.worth("ender_chest").orElseThrow());
        assertEquals(2_500, c.worth("hopper").orElseThrow(), "sold items: their shop price");
        assertTrue(c.worth("spawner_pig").isEmpty(), "spawners are no block worth");
    }

    @Test
    void spawnersTabSellsTier1And2AtAFixedPrice() throws Exception {
        ShopCatalog.Category tab = catalog().category("spawners").orElseThrow();
        for (ShopCatalog.Item item : tab.items()) {
            assertTrue(item.spawner(), item.id());
            assertTrue(item.id().startsWith(ShopCatalog.SPAWNER_PREFIX));
            assertTrue(SPAWNER_TYPES.contains(item.mob()), item.mob());
        }
        assertEquals(117_000, catalog().item("spawner_pig").orElseThrow().price());
        assertEquals(213_000, catalog().item("spawner_zombie").orElseThrow().price());
        assertEquals(11, tab.items().size());
    }

    @Test
    void spawnersAreSkippedWithoutTheStackingModule() throws Exception {
        List<String> problems = new ArrayList<>();
        ShopCatalog c = ShopCatalog.load(bundled(), ShopCatalogTest::realItem, problems::add);
        assertTrue(c.category("spawners").orElseThrow().items().isEmpty());
        assertEquals(11, problems.size(), () -> String.join("\n", problems));
    }

    @Test
    void bundledShopSaysTheOwnerApprovesPrices() throws Exception {
        String text = Files.readString(Path.of(ShopCatalogTest.class.getClassLoader().getResource("shop.yml").toURI()));
        assertTrue(text.contains("THE OWNER APPROVES THESE PRICES BEFORE LAUNCH"));
        assertEquals(2, bundled().getInt("config-version"));
    }

    @Test
    void brokenEntriesAreSkippedWithAProblem() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                categories:
                  a:
                    icon: stone
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
                  c:
                    kind: spawners
                    icon: spawner
                    items:
                      pig: 100
                      warden: 5
                worth-only:
                  stone: 99
                  ender_chest: 1500
                  bogus: 3
                """);
        List<String> problems = new ArrayList<>();
        ShopCatalog c = load(yaml, problems);
        assertEquals(List.of("stone", "sand", "oak_log", "spawner_pig"), c.items().stream().map(ShopCatalog.Item::id).toList());
        assertFalse(c.item("sand").orElseThrow().enabled());
        assertEquals(3, c.item("oak_log").orElseThrow().requires().get("farming"));
        assertEquals(5, c.item("stone").orElseThrow().price(), "listed twice: first one kept");
        assertEquals(List.of(), c.category("b").orElseThrow().items());
        assertEquals("chest", c.category("b").orElseThrow().icon());
        assertEquals(2, c.category("c").orElseThrow().slot());
        assertEquals(5, c.worth("stone").orElseThrow(), "a sold item's worth is its shop price");
        assertEquals(1_500, c.worth("ender_chest").orElseThrow());
        assertEquals(9, problems.size(), () -> String.join("\n", problems));
    }

    @Test
    void onlyNineTabsFit() throws Exception {
        StringBuilder text = new StringBuilder("categories:\n");
        for (int i = 0; i < 11; i++) {
            text.append("  t").append(i).append(":\n    icon: stone\n    items:\n      dirt").append(i == 0 ? "" : "_x").append(": 4\n");
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(text.toString());
        List<String> problems = new ArrayList<>();
        ShopCatalog c = load(yaml, problems);
        assertEquals(9, c.categories().size());
        assertTrue(problems.stream().anyMatch(p -> p.contains("only 9 tabs")));
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
                config-version: 2
                categories:
                  wood:
                    icon: oak_log
                    items:
                      oak_log: 99
                  decoration:
                    items:
                      dirt: 1
                worth-only:
                  lantern: 40
                """);
        assertTrue(ShopModule.addMissing(owner, bundled()));
        assertEquals(99, owner.getInt("categories.wood.items.oak_log"), "owner prices are kept");
        assertTrue(owner.contains("categories.wood.items.spruce_log"));
        assertFalse(owner.contains("categories.stone-earth.items.dirt"), "moved items are not added back");
        assertFalse(owner.contains("categories.decoration.items.lantern"), "items parked under worth-only stay parked");
        assertTrue(owner.contains("categories.essentials"));
        assertEquals("spawners", owner.getString("categories.spawners.kind"));
        assertFalse(ShopModule.addMissing(owner, bundled()), "nothing left to add");
    }

    // ------------------------------------------------------------------ v1 → v2 migration (GUI-3)

    @Test
    void untouchedV1FileBecomesTheBundledV2Catalog() throws Exception {
        ShopCatalogMigration.Result r = ShopCatalogMigration.migrate(v1(), bundled(), v1()).orElseThrow();
        assertEquals(2, r.yaml().getInt("config-version"));
        List<String> problems = new ArrayList<>();
        ShopCatalog migrated = load(r.yaml(), problems);
        assertTrue(problems.isEmpty(), () -> String.join("\n", problems));
        ShopCatalog expected = catalog();
        assertEquals(expected.items(), migrated.items(), "same tabs, items, order and prices as a fresh install");
        assertEquals(expected.worthOnly(), migrated.worthOnly());
        assertTrue(r.changes().contains("hopper: price $120 -> $2500 (Shop v2)"), () -> String.join("\n", r.changes()));
        assertTrue(r.changes().contains("bone_meal: price $6 -> $30 (Shop v2)"));
        assertTrue(r.changes().stream().anyMatch(c -> c.startsWith("removed ender_chest (was $1500 in utility)")));
        assertTrue(r.changes().stream().anyMatch(c -> c.startsWith("added flint_and_steel to essentials at $40")));
        assertTrue(r.changes().stream().anyMatch(c -> c.startsWith("added spawner pig to spawners at $117000")));
    }

    @Test
    void ownerEditsSurviveTheMigration() throws Exception {
        YamlConfiguration live = v1();
        live.set("categories.redstone.items.hopper", 999);                         // owner price on a design change
        live.set("categories.building-blocks.items.dirt", 7);                      // owner price, no design change
        live.set("categories.stone-bricks.items.stone", 20);                       // scalar over a map with contains
        live.set("categories.utility.items.anvil", java.util.Map.of("price", 400, "enabled", false));
        live.set("categories.utility.items.ender_chest", 5_000);                   // removed by the design
        live.set("categories.farming.items.wheat_seeds", java.util.Map.of("price", 10, "requires", java.util.Map.of("farming", 4)));
        live.set("categories.decoration.items.jukebox", 900);                      // the owner's own item
        ShopCatalogMigration.Result r = ShopCatalogMigration.migrate(live, bundled(), v1()).orElseThrow();
        List<String> problems = new ArrayList<>();
        ShopCatalog c = load(r.yaml(), problems);
        assertTrue(problems.isEmpty(), () -> String.join("\n", problems));
        assertEquals(999, c.item("hopper").orElseThrow().price(), "an owner-edited price is kept");
        assertTrue(r.changes().contains("hopper: kept your price $999 (Shop v2 default would be $2500)"));
        assertEquals(30, c.item("bone_meal").orElseThrow().price(), "an untouched default moves to the new price");
        assertEquals(7, c.item("dirt").orElseThrow().price());
        assertEquals("stone-earth", c.item("dirt").orElseThrow().category());
        assertEquals(20, c.item("stone").orElseThrow().price());
        assertEquals(1, c.item("stone").orElseThrow().contains().get("cobblestone"), "contains survives an owner price");
        assertFalse(c.item("anvil").orElseThrow().enabled(), "switched-off items stay off");
        assertEquals(4, c.item("wheat_seeds").orElseThrow().requires().get("farming"));
        assertTrue(c.item("ender_chest").isEmpty(), "S6: ender chest leaves the shop");
        assertEquals(5_000, c.worth("ender_chest").orElseThrow(), "its live price stays as block worth");
        assertEquals(900, c.item("jukebox").orElseThrow().price(), "the owner's own item is kept");
        assertEquals("decoration", c.item("jukebox").orElseThrow().category());
        assertTrue(r.changes().stream().anyMatch(l -> l.startsWith("kept your own item jukebox")));
    }

    @Test
    void migrationRunsOnlyOnce() throws Exception {
        ShopCatalogMigration.Result r = ShopCatalogMigration.migrate(v1(), bundled(), v1()).orElseThrow();
        YamlConfiguration again = new YamlConfiguration();
        again.loadFromString(r.yaml().saveToString());
        assertTrue(ShopCatalogMigration.migrate(again, bundled(), v1()).isEmpty());
        assertTrue(ShopCatalogMigration.migrate(bundled(), bundled(), v1()).isEmpty(), "a fresh v2 file is left alone");
    }

    @Test
    void v1SnapshotIsTheOldDefaultCatalog() throws Exception {
        List<String> problems = new ArrayList<>();
        ShopCatalog old = ShopCatalog.load(v1(), ShopCatalogTest::realItem, problems::add);
        assertEquals(332, old.size());
        assertEquals(120, old.item("hopper").orElseThrow().price());
        assertEquals(1, ShopCatalogMigration.version(v1()));
    }
}
