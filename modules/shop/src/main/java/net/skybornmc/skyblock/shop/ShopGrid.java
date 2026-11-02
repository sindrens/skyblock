package net.skybornmc.skyblock.shop;

import net.skybornmc.skyblock.common.menu.Centered;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * The item grid of a shop tab. Owner 3 Nov 2026 ("no zigzag, clean, more space"): the tab's items in group order fill
 * full rows of 7 left to right; only the very last row of a page is centred. A page has {@link #ROWS} rows (screen rows
 * 1-3), so row 4 stays empty as breathing room above the bottom bar. Pure.
 */
final class ShopGrid {

    /** Item rows per page (screen rows 1-3 of a 6-row menu). */
    static final int ROWS = 3;
    /** Most items of one page (7 x 3). */
    static final int PAGE = Centered.GRID_WIDTH * ROWS;

    /** One page: the items, their slots (same order) and the rows used. */
    record Page(List<ShopCatalog.Item> items, int[] slots, int rows) {
        Page {
            items = List.copyOf(items);
            slots = slots.clone();
        }
    }

    private ShopGrid() {
    }

    /**
     * @param groups        the tab's groups in display order ({@link ShopCatalog.Category#displayGroups()})
     * @param enabledLookup the item of an id when it is shown now (enabled, sold), else null (it is left out)
     */
    static List<Page> pages(List<ShopCatalog.Group> groups, Function<String, ShopCatalog.Item> enabledLookup) {
        List<ShopCatalog.Item> shown = new ArrayList<>();
        for (ShopCatalog.Group group : groups) {
            for (String id : group.items()) {
                ShopCatalog.Item item = enabledLookup.apply(id);
                if (item != null) {
                    shown.add(item);
                }
            }
        }
        List<Page> out = new ArrayList<>();
        for (int from = 0; from < shown.size(); from += PAGE) {
            List<ShopCatalog.Item> items = shown.subList(from, Math.min(shown.size(), from + PAGE));
            out.add(page(items));
        }
        return out;
    }

    /** Full rows of 7, the remainder centred on the last row. */
    static Page page(List<ShopCatalog.Item> items) {
        int n = items.size();
        int full = n / Centered.GRID_WIDTH;
        int rest = n % Centered.GRID_WIDTH;
        int[] counts = new int[full + (rest > 0 ? 1 : 0)];
        java.util.Arrays.fill(counts, 0, full, Centered.GRID_WIDTH);
        if (rest > 0) {
            counts[full] = rest;
        }
        return new Page(items, Centered.grid(counts), counts.length);
    }
}
