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

/** Plan 4 Nov C §3: groups on their own rows (7 max, centred, no holes), an empty row between groups, rows 1-3. */
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
                assertTrue(page.rows() <= ShopGrid.ROWS, c.id());
                Set<Integer> slots = new HashSet<>();
                for (int i = 0; i < page.slots().length; i++) {
                    int slot = page.slots()[i];
                    assertTrue(slot >= 10 && slot <= 34 && slot % 9 >= 1 && slot % 9 <= 7, c.id() + " slot " + slot);
                    assertTrue(slots.add(slot), c.id() + " slot used twice: " + slot);
                    assertTrue(placed.add(page.items().get(i).id()), c.id() + " placed twice: " + page.items().get(i).id());
                }
            }
            assertEquals(c.enabledItems().size(), placed.size(), c.id());
        }
    }

    @Test
    void groupsGetTheirOwnCentredRowsWithAnEmptyRowBetween() {
        List<ShopCatalog.Group> groups = List.of(new ShopCatalog.Group("a", ids("a", 3)), new ShopCatalog.Group("b", ids("b", 6)));
        List<ShopGrid.Page> pages = ShopGrid.pages(groups, all(groups));
        assertEquals(1, pages.size());
        assertArrayEquals(new int[]{12, 13, 14, 28, 29, 30, 31, 32, 33}, pages.get(0).slots(),
                "3 centred on row 1, row 2 empty, 6 side by side on row 3");
        assertEquals("a0", pages.get(0).items().get(0).id(), "group order kept");
        assertEquals("b0", pages.get(0).items().get(3).id());
        assertEquals(3, pages.get(0).rows());
    }

    @Test
    void aGroupThatDoesNotFitStartsTheNextPage() {
        List<ShopCatalog.Group> groups = List.of(new ShopCatalog.Group("a", ids("a", 7)), new ShopCatalog.Group("b", ids("b", 12)));
        List<ShopGrid.Page> pages = ShopGrid.pages(groups, all(groups));
        assertEquals(2, pages.size(), "b needs 2 rows, only row 3 is left after the gap");
        assertArrayEquals(Centered.grid(7), pages.get(0).slots());
        assertEquals(12, pages.get(1).items().size());
        assertEquals(10, pages.get(1).slots()[0]);
        assertEquals(20, pages.get(1).slots()[7], "the last 5 are centred on row 2 (columns 2-6)");
    }

    @Test
    void aDisabledItemIsLeftOut() {
        List<ShopCatalog.Group> groups = List.of(new ShopCatalog.Group("a", ids("a", 8)));
        Function<String, ShopCatalog.Item> lookup = id -> id.equals("a3") ? null : item(id);
        List<ShopGrid.Page> pages = ShopGrid.pages(groups, lookup);
        assertEquals(1, pages.size());
        assertArrayEquals(Centered.grid(7), pages.get(0).slots(), "7 left = one full row");
    }

    @Test
    void aLongGroupFillsWholePagesOfThreeRows() {
        List<ShopCatalog.Group> groups = List.of(new ShopCatalog.Group("a", ids("a", 22)));
        List<ShopGrid.Page> pages = ShopGrid.pages(groups, all(groups));
        assertEquals(2, pages.size());
        assertEquals(21, pages.get(0).items().size());
        assertEquals(3, pages.get(0).rows());
        assertArrayEquals(new int[]{13}, pages.get(1).slots());
    }

    @Test
    void noGroupsGiveNoPages() {
        assertEquals(List.of(), ShopGrid.pages(List.of(), id -> null));
    }
}
