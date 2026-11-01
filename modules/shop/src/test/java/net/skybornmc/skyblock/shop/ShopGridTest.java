package net.skybornmc.skyblock.shop;

import net.skybornmc.skyblock.common.menu.Centered;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** MENUS-V3 6.2: the grouped shop grid (whole groups per page, centred rows, even split). */
class ShopGridTest {

    private static ShopCatalog.Item item(String id) {
        return new ShopCatalog.Item(id, "t", 1, Map.of(), Map.of(), true);
    }

    private static Function<String, ShopCatalog.Item> all(List<ShopCatalog.Group> groups) {
        Map<String, ShopCatalog.Item> map = new LinkedHashMap<>();
        groups.forEach(g -> g.items().forEach(id -> map.put(id, item(id))));
        return map::get;
    }

    private static List<String> ids(String prefix, int n) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(prefix + i);
        }
        return out;
    }

    private static List<ShopGrid.Page> pagesOf(String tab) throws Exception {
        ShopCatalog.Category c = ShopCatalogTest.catalog().category(tab).orElseThrow();
        Map<String, ShopCatalog.Item> shown = new LinkedHashMap<>();
        c.enabledItems().forEach(i -> shown.put(i.id(), i));
        return ShopGrid.pages(c.displayGroups(), shown::get);
    }

    @Test
    void everyEnabledItemOfTheBundledShopIsPlacedExactlyOnce() throws Exception {
        for (ShopCatalog.Category c : ShopCatalogTest.catalog().categories()) {
            Set<String> placed = new HashSet<>();
            for (ShopGrid.Page page : pagesOf(c.id())) {
                assertEquals(page.items().size(), page.slots().length);
                assertTrue(page.rows() <= Centered.GRID_ROWS, c.id());
                Set<Integer> slots = new HashSet<>();
                for (int i = 0; i < page.slots().length; i++) {
                    int slot = page.slots()[i];
                    assertTrue(slot >= 10 && slot <= 43 && slot % 9 >= 1 && slot % 9 <= 7, c.id() + " slot " + slot);
                    assertTrue(slots.add(slot), c.id() + " slot used twice: " + slot);
                    assertTrue(placed.add(page.items().get(i).id()), c.id() + " placed twice: " + page.items().get(i).id());
                }
            }
            assertEquals(c.enabledItems().size(), placed.size(), c.id());
        }
    }

    @Test
    void farmingPageOneIsTheC5Mock() throws Exception {
        List<ShopGrid.Page> pages = pagesOf("farming");
        assertEquals(2, pages.size());
        assertArrayEquals(new int[]{11, 12, 13, 14, 15, 19, 20, 21, 23, 24, 25, 29, 30, 31, 32, 33, 38, 39, 41, 42},
                pages.get(0).slots(), "seeds row 1, crops row 2, saplings rows 3 and 4");
        assertEquals("wheat_seeds", pages.get(0).items().get(0).id());
        assertEquals("bone_meal", pages.get(0).items().get(4).id(), "helpers last");
    }

    @Test
    void colorsHasThreePagesOfWholeColourGroups() throws Exception {
        List<ShopGrid.Page> pages = pagesOf("colors");
        assertEquals(3, pages.size());
        assertEquals(4, pages.get(0).rows(), "concrete 6 + 5 + 5, extras 4");
        assertEquals(3, pages.get(1).rows(), "stained glass 6 + 5 + 5");
        assertTrue(pages.get(1).items().stream().allMatch(i -> i.id().endsWith("_stained_glass")));
        assertTrue(pages.get(2).items().get(0).id().endsWith("_terracotta"));
    }

    @Test
    void spawnersAreFourRowsOfFive() throws Exception {
        List<ShopGrid.Page> pages = pagesOf("spawners");
        assertEquals(1, pages.size());
        assertArrayEquals(Centered.grid(5, 5, 5, 5), pages.get(0).slots());
        assertEquals("spawner_pig", pages.get(0).items().get(0).id());
        assertEquals("spawner_blaze", pages.get(0).items().get(15).id());
    }

    @Test
    void aGroupThatDoesNotFitStartsTheNextPage() {
        List<ShopCatalog.Group> groups = List.of(
                new ShopCatalog.Group("a", ids("a", 16)),   // 3 rows
                new ShopCatalog.Group("b", ids("b", 9)),    // 2 rows: does not fit the 1 row left
                new ShopCatalog.Group("c", ids("c", 3)));   // 1 row: fits after b
        List<ShopGrid.Page> pages = ShopGrid.pages(groups, all(groups));
        assertEquals(2, pages.size());
        assertEquals(16, pages.get(0).items().size());
        assertArrayEquals(Centered.grid(6, 5, 5), pages.get(0).slots());
        assertEquals(12, pages.get(1).items().size());
        assertArrayEquals(Centered.grid(5, 4, 3), pages.get(1).slots());
    }

    @Test
    void aDisabledItemShortensItsGroup() {
        List<ShopCatalog.Group> groups = List.of(new ShopCatalog.Group("a", ids("a", 8)));
        Function<String, ShopCatalog.Item> lookup = id -> id.equals("a3") ? null : item(id);
        List<ShopGrid.Page> pages = ShopGrid.pages(groups, lookup);
        assertEquals(1, pages.size());
        assertEquals(7, pages.get(0).items().size());
        assertArrayEquals(Centered.grid(7), pages.get(0).slots(), "7 left = one full row, not 4 + 3");
    }

    @Test
    void aGroupOf29IsCutBy28() {
        List<ShopCatalog.Group> groups = List.of(new ShopCatalog.Group("a", ids("a", 29)));
        List<ShopGrid.Page> pages = ShopGrid.pages(groups, all(groups));
        assertEquals(2, pages.size());
        assertEquals(28, pages.get(0).items().size());
        assertEquals(4, pages.get(0).rows());
        assertEquals(1, pages.get(1).items().size());
        assertArrayEquals(new int[]{13}, pages.get(1).slots());
    }

    @Test
    void noGroupsGiveNoPages() {
        assertEquals(List.of(), ShopGrid.pages(List.of(), id -> null));
    }
}
