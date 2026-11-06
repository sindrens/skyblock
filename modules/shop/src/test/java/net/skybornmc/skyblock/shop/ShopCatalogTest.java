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

/** The bundled shop.yml (Shop v3), the parser and the one-time v1 → v2 catalog migration (against the frozen v2 file). */
class ShopCatalogTest {

    /** stacking.yml defaults: the spawner types of tiers 1 to 4 the shop sells (ECON-SPAWNERS / ECON-TABLES A). */
    static final Set<String> SPAWNER_TYPES = Set.of("pig", "cow", "chicken", "sheep", "rabbit",
            "zombie", "skeleton", "spider", "cave_spider", "slime",
            "creeper", "witch", "drowned", "husk", "stray",
            "blaze", "enderman", "guardian", "hoglin", "zombified_piglin");
    /** The spawner types of the frozen Shop v2 file (tier 1 and 2, owner SK3/SK4; squid left in ECON-SPAWNERS). */
    static final Set<String> V2_SPAWNER_TYPES = Set.of("pig", "cow", "chicken", "sheep", "rabbit",
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

    static YamlConfiguration v2() throws Exception {
        return resource(ShopModule.V2_DEFAULTS);
    }

    static YamlConfiguration v3() throws Exception {
        return resource(ShopModule.V3_DEFAULTS);
    }

    static YamlConfiguration v4() throws Exception {
        return resource(ShopModule.V4_DEFAULTS);
    }

    /** B1: {@code price: auto} from the real sell.yml (economy module, multiplier 1), markup 10, buy multiplier 1. */
    static ShopCatalog.Pricing pricing() {
        try {
            net.skybornmc.skyblock.economy.sell.SellCatalog sell = BuySellInvariantTest.sellCatalog();
            return new ShopCatalog.Pricing(id -> sell.get(id).map(e -> e.base()).orElse(Double.NaN), 10, 1.0);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** A catalog of {@code yaml} that must load without problems. */
    static ShopCatalog catalogOf(YamlConfiguration yaml) {
        List<String> problems = new ArrayList<>();
        ShopCatalog c = load(yaml, problems);
        assertTrue(problems.isEmpty(), () -> String.join("\n", problems));
        return c;
    }

    /** A file in the Shop v2 shape (v2 spawner types). */
    static ShopCatalog loadV2(YamlConfiguration yaml, List<String> problems) {
        return ShopCatalog.load(yaml, ShopCatalogTest::realItem, V2_SPAWNER_TYPES::contains, problems::add);
    }

    static ShopCatalog load(YamlConfiguration yaml, List<String> problems) {
        return ShopCatalog.load(yaml, ShopCatalogTest::realItem, SPAWNER_TYPES::contains, pricing(), problems::add);
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
        // MENUS-V3 6.2: row 1 wood .. decoration, row 2 essentials .. spawners (5 + 4 on the category menu).
        assertEquals(ShopCatalogGroupsTest.ORDER,
                c.categories().stream().map(ShopCatalog.Category::id).toList());
        for (int i = 0; i < c.categories().size(); i++) {
            ShopCatalog.Category tab = c.categories().get(i);
            assertEquals(i, tab.slot(), "tab " + tab.id() + " sits in slot " + i);
            assertTrue(realItem(tab.icon()), tab.icon());
            assertFalse(tab.enabledItems().isEmpty(), tab.id());
            int pages = tab.id().equals("building") ? 4 : Set.of("colors", "nature").contains(tab.id()) ? 3 : 2; // batch 9: planks and crafted stone
            assertTrue(tab.items().size() <= 28 * pages, tab.id() + " fits " + pages + " page(s)");
        }
        assertEquals(ShopCatalog.Kind.SPAWNER, c.category("spawners").orElseThrow().kind());
    }

    @Test
    void the255ItemsOfEconTablesD4() throws Exception {
        ShopCatalog c = catalog();
        // ECON-TABLES D4 (Part B): remove 53, add 46, spawners 20 (tiers 1 to 4).
        // MENUS-V3 tab order: wood, stone-earth, special, colors, decoration, essentials, farming, redstone, spawners.
        assertEquals(329, c.size()); // + 19 workstations (5 Nov 2026), + 34 back and 8 mob drops (batch 9, Q-42), - 6 copy spawners (7 Nov)
        int[] expected = {85, 57, 20, 27, 52, 17, 29, 28, 14}; // Shop v5 tabs (owner 2 Nov 2026), plus Mob Drops
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], c.categories().get(i).items().size(), c.categories().get(i).id());
        }
    }

    @Test
    void ownerPriceDecisions() throws Exception {
        ShopCatalog c = catalog();
        assertEquals(250_000, c.item("hopper").orElseThrow().price(), "S6");
        assertEquals(3_000, c.item("bone_meal").orElseThrow().price(), "S7");
        assertEquals(4_000, c.item("flint_and_steel").orElseThrow().price(), "S4");
        assertEquals(15_000, c.item("fishing_rod").orElseThrow().price());
        assertEquals(6_000, c.item("shears").orElseThrow().price());
        assertEquals(1_500, c.item("bread").orElseThrow().price());
        assertEquals(1_500, c.worth("baked_potato").orElseThrow(), "removed in Shop v4: its price is block worth only");
        assertEquals(400, c.item("dirt").orElseThrow().price(), "unchanged prices stay");
        assertEquals(1, c.item("stone").orElseThrow().contains().get("cobblestone"));
    }

    @Test
    void cleanerCatalogLeavesOutWhatS3AndS6Remove() throws Exception {
        ShopCatalog c = catalog();
        for (String gone : List.of("ender_chest", "redstone_block", "stripped_oak_log", "red_dye", "red_wool", "red_carpet",
                "loom", "lectern", "smithing_table", "cartography_table", "fletching_table")) {
            assertTrue(c.item(gone).isEmpty(), gone + " is not sold");
        }
        assertTrue(c.item("white_wool").isPresent(), "white wool stays (Colors)");
        assertEquals(240_000, c.worth("redstone_block").orElseThrow(), "removed items keep their block worth");
        assertEquals(150_000, c.worth("ender_chest").orElseThrow());
        assertEquals(250_000, c.worth("hopper").orElseThrow(), "sold items: their shop price");
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
        // owner 7 Nov 2026 (plans/HOE_SHARDS_SPAWNERS.md): priced by what they earn, about 50 h payback
        assertEquals(5_000_000, catalog().item("spawner_pig").orElseThrow().price());
        assertEquals(12_500_000, catalog().item("spawner_zombie").orElseThrow().price());
        assertEquals(30_000_000, catalog().item("spawner_creeper").orElseThrow().price());
        assertEquals(75_000_000, catalog().item("spawner_blaze").orElseThrow().price());
        assertEquals(14, tab.items().size()); // owner 7 Nov 2026: 5 + 4 + 2 + 3
    }

    @Test
    void spawnersAreSkippedWithoutTheStackingModule() throws Exception {
        List<String> problems = new ArrayList<>();
        ShopCatalog c = ShopCatalog.load(bundled(), ShopCatalogTest::realItem, type -> false, pricing(), problems::add);
        assertTrue(c.category("spawners").orElseThrow().items().isEmpty());
        assertEquals(14, problems.size(), () -> String.join("\n", problems));
        assertTrue(c.category("spawners").orElseThrow().groups().isEmpty(), "skipped spawners leave their groups silently");
    }

    @Test
    void bundledShopSaysTheOwnerApprovesPrices() throws Exception {
        String text = Files.readString(Path.of(ShopCatalogTest.class.getClassLoader().getResource("shop.yml").toURI()));
        assertTrue(text.contains("THE OWNER APPROVES THESE PRICES BEFORE LAUNCH"));
        assertEquals(ShopCatalogMigration.VERSION, bundled().getInt("config-version"));
        assertEquals(7, ShopCatalogMigration.VERSION);
        assertEquals(4, v4().getInt("config-version"), "the frozen v4 file stays version 4");
        assertEquals(2, v2().getInt("config-version"), "the frozen v2 file stays version 2");
        assertEquals(3, v3().getInt("config-version"), "the frozen v3 file stays version 3");
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
        assertTrue(owner.contains("categories.building.items.spruce_log"), "new items join the bundled tab");
        assertFalse(owner.contains("categories.stone-earth.items.dirt"), "moved items are not added back");
        assertFalse(owner.contains("categories.decoration.items.lantern"), "items parked under worth-only stay parked");
        assertTrue(owner.contains("categories.essentials"));
        assertEquals("spawners", owner.getString("categories.spawners.kind"));
        assertFalse(ShopModule.addMissing(owner, bundled()), "nothing left to add");
    }

    // ------------------------------------------------------------------ v1 → v2 migration (GUI-3)

    @Test
    void untouchedV1FileBecomesTheBundledV2Catalog() throws Exception {
        ShopCatalogMigration.Result r = ShopCatalogMigration.migrate(v1(), v2(), v1()).orElseThrow();
        assertEquals(2, r.yaml().getInt("config-version"));
        List<String> problems = new ArrayList<>();
        ShopCatalog migrated = loadV2(r.yaml(), problems);
        assertTrue(problems.isEmpty(), () -> String.join("\n", problems));
        List<String> v2Problems = new ArrayList<>();
        ShopCatalog expected = loadV2(v2(), v2Problems);
        assertTrue(v2Problems.isEmpty(), () -> String.join("\n", v2Problems));
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
        ShopCatalogMigration.Result r = ShopCatalogMigration.migrate(live, v2(), v1()).orElseThrow();
        List<String> problems = new ArrayList<>();
        ShopCatalog c = loadV2(r.yaml(), problems);
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
        ShopCatalogMigration.Result r = ShopCatalogMigration.migrate(v1(), v2(), v1()).orElseThrow();
        YamlConfiguration again = new YamlConfiguration();
        again.loadFromString(r.yaml().saveToString());
        assertTrue(ShopCatalogMigration.migrate(again, v2(), v1()).isEmpty());
        assertTrue(ShopCatalogMigration.migrate(v2(), v2(), v1()).isEmpty(), "a fresh v2 file is left alone");
        assertTrue(ShopCatalogMigration.migrate(bundled(), v2(), v1()).isEmpty(), "a v3 file is left alone");
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
