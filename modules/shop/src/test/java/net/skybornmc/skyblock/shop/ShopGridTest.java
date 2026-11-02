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

/** Owner 3 Nov 2026: the shop grid fills full rows of 7, only a page's last row is centred, 3 rows per page. */
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
    void fullRowsFirstAndOnlyTheLastRowIsCentred() {
        List<ShopCatalog.Group> groups = List.of(new ShopCatalog.Group("a", ids("a", 3)), new ShopCatalog.Group("b", ids("b", 6)));
        List<ShopGrid.Page> pages = ShopGrid.pages(groups, all(groups));
        assertEquals(1, pages.size());
        assertArrayEquals(Centered.grid(7, 2), pages.get(0).slots(), "3 + 6 = one full row and 2 centred, no zigzag");
        assertEquals("a0", pages.get(0).items().get(0).id(), "group order kept");
        assertEquals("b0", pages.get(0).items().get(3).id());
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
    void aPageHoldsThreeRows() {
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
