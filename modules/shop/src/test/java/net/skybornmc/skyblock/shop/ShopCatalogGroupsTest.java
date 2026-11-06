package net.skybornmc.skyblock.shop;

import net.skybornmc.skyblock.common.menu.Centered;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** MENUS-V3 6.2: shop.yml {@code groups:} and the category order. */
class ShopCatalogGroupsTest {

    static final List<String> ORDER = List.of("building", "nature", "farming", "mob-drops", "colors", "decoration",
            "essentials", "redstone", "spawners");

    @Test
    void categoryOrderIsFivePlusFour() throws Exception {
        assertEquals(ORDER, ShopCatalogTest.catalog().categories().stream().map(ShopCatalog.Category::id).toList());
    }

    @Test
    void everyEnabledItemIsInExactlyOneDisplayGroup() throws Exception {
        for (ShopCatalog.Category c : ShopCatalogTest.catalog().categories()) {
            Set<String> ids = new HashSet<>();
            c.items().forEach(i -> ids.add(i.id()));
            Set<String> seen = new HashSet<>();
            for (ShopCatalog.Group g : c.displayGroups()) {
                for (String id : g.items()) {
                    assertTrue(ids.contains(id), c.id() + "." + g.id() + ": " + id + " is an item of the tab");
                    assertTrue(seen.add(id), c.id() + ": " + id + " in two groups");
                }
            }
            for (ShopCatalog.Item i : c.enabledItems()) {
                assertTrue(seen.contains(i.id()), c.id() + ": " + i.id() + " in no group");
            }
            assertTrue(c.groups().size() >= 2, c.id() + " has its own groups");
        }
    }

    @Test
    void groupPricesNeverFallExceptBoneMeal() throws Exception {
        for (ShopCatalog.Category c : ShopCatalogTest.catalog().categories()) {
            for (ShopCatalog.Group g : c.groups()) {
                long last = 0;
                for (String id : g.items()) {
                    if (id.equals("bone_meal")) {
                        continue;
                    }
                    long price = c.items().stream().filter(i -> i.id().equals(id)).findFirst().orElseThrow().price();
                    assertTrue(price >= last, c.id() + "." + g.id() + ": " + id + " $" + price + " after $" + last);
                    last = price;
                }
            }
        }
    }

    @Test
    void spawnerGroupsAreTheFourTiers() throws Exception {
        ShopCatalog.Category tab = ShopCatalogTest.catalog().category("spawners").orElseThrow();
        assertEquals(List.of("tier1", "tier2", "tier3", "tier4"), tab.groups().stream().map(ShopCatalog.Group::id).toList());
        assertEquals(List.of(5, 4, 2, 3), tab.groups().stream().map(g -> g.items().size()).toList(),
                "owner 7 Nov 2026: one mob per loot (no copies)");
        assertEquals(List.of("spawner_pig", "spawner_cow", "spawner_chicken", "spawner_sheep", "spawner_rabbit"),
                tab.groups().get(0).items());
    }

    @Test
    void unknownRepeatedAndReservedIdsAreReportedAndSkipped() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                categories:
                  a:
                    icon: stone
                    groups:
                      one: [dirt, nonsense, stone]
                      two: [dirt, sand]
                      empty: []
                      more: [gravel]
                    items:
                      stone: 5
                      dirt: 4
                      sand: 4
                      gravel: 4
                      clay: 12
                """);
        List<String> problems = new ArrayList<>();
        ShopCatalog.Category c = ShopCatalogTest.load(yaml, problems).category("a").orElseThrow();
        assertEquals(3, problems.size(), () -> String.join("\n", problems));
        assertEquals(List.of(new ShopCatalog.Group("one", List.of("dirt", "stone")), new ShopCatalog.Group("two", List.of("sand"))),
                c.groups());
        assertEquals(List.of("gravel", "clay"), c.displayGroups().get(2).items(), "ungrouped items form the last group");
        assertEquals(ShopCatalog.MORE, c.displayGroups().get(2).id());
    }

    @Test
    void aTabWithoutGroupsIsOneGroupInFileOrder() {
        ShopCatalog.Item a = new ShopCatalog.Item("a", "t", 1, Map.of(), Map.of(), true);
        ShopCatalog.Item b = new ShopCatalog.Item("b", "t", 1, Map.of(), Map.of(), true);
        ShopCatalog.Category c = new ShopCatalog.Category("t", "stone", 0, ShopCatalog.Kind.ITEM, List.of(a, b));
        assertEquals(List.of(new ShopCatalog.Group(ShopCatalog.MORE, List.of("a", "b"))), c.displayGroups());
    }

    // ------------------------------------------------------------------ counts of the ECON result (ECON-TABLES D)

    @Test
    void econResultCounts() throws Exception {
        ShopCatalog c = ShopCatalogTest.catalog();
        assertEquals(329, c.size()); // + 19 workstations (5 Nov 2026), + 34 back and 8 mob drops (batch 9, Q-42)
        int[] expected = {85, 57, 20, 27, 52, 17, 29, 28, 14};
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], c.categories().get(i).items().size(), c.categories().get(i).id());
        }
    }

    @Test
    void econResultPageCounts() throws Exception {
        List<ShopCatalog.Category> tabs = ShopCatalogTest.catalog().categories();
        for (ShopCatalog.Category c : tabs) {
            Map<String, ShopCatalog.Item> shown = new LinkedHashMap<>();
            c.enabledItems().forEach(it -> shown.put(it.id(), it));
            // Plan 4 Nov C §3: groups start their own row with an empty row between them, so a tab needs at least
            // ceil(n / 21) pages and never more than one page per group row.
            int pages = ShopGrid.pages(c.displayGroups(), shown::get).size();
            int groupRows = c.displayGroups().stream().mapToInt(g -> (g.items().size() + 6) / 7).sum();
            assertTrue(pages >= (c.enabledItems().size() + ShopGrid.PAGE - 1) / ShopGrid.PAGE, c.id());
            assertTrue(pages <= Math.max(1, groupRows), c.id());
            for (ShopCatalog.Group g : c.groups()) {
                assertEquals(Centered.split(g.items().size()).length, (g.items().size() + 6) / 7, c.id() + "." + g.id());
            }
            assertTrue(c.displayGroups().stream().noneMatch(g -> g.id().equals(ShopCatalog.MORE)), c.id() + ": no ungrouped item");
        }
    }
}
