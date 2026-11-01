package net.skybornmc.skyblock.shop;

import net.skybornmc.skyblock.common.menu.Centered;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * The grouped item grid of a shop tab (MENUS-V3 6.2, owner V3-Q14 a "one group per row, centred, cautious and
 * simple"): every group starts on a new row; a group longer than 7 is split evenly ({@link Centered#split}: 16 = 6 + 5 +
 * 5); a group longer than 28 is cut into pieces of 28; a page holds whole groups up to 4 rows and a group that does not
 * fit the rows left starts the next page. No captions, no gap rows, no label tiles. Pure.
 */
final class ShopGrid {

    /** Most items of one page (7 x 4). */
    static final int PAGE = Centered.GRID_WIDTH * Centered.GRID_ROWS;

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
     * @param enabledLookup the item of an id when it is shown now (enabled, sold), else null (it leaves its group)
     */
    static List<Page> pages(List<ShopCatalog.Group> groups, Function<String, ShopCatalog.Item> enabledLookup) {
        List<List<ShopCatalog.Item>> pieces = new ArrayList<>();
        for (ShopCatalog.Group group : groups) {
            List<ShopCatalog.Item> shown = new ArrayList<>();
            for (String id : group.items()) {
                ShopCatalog.Item item = enabledLookup.apply(id);
                if (item != null) {
                    shown.add(item);
                }
            }
            for (int from = 0; from < shown.size(); from += PAGE) {
                pieces.add(shown.subList(from, Math.min(shown.size(), from + PAGE)));
            }
        }
        List<Page> out = new ArrayList<>();
        List<ShopCatalog.Item> items = new ArrayList<>();
        List<Integer> sizes = new ArrayList<>();
        int rows = 0;
        for (List<ShopCatalog.Item> piece : pieces) {
            int[] split = Centered.split(piece.size());
            if (rows + split.length > Centered.GRID_ROWS && rows > 0) {
                out.add(page(items, sizes));
                items = new ArrayList<>();
                sizes = new ArrayList<>();
                rows = 0;
            }
            items.addAll(piece);
            for (int size : split) {
                sizes.add(size);
            }
            rows += split.length;
        }
        if (!items.isEmpty()) {
            out.add(page(items, sizes));
        }
        return out;
    }

    private static Page page(List<ShopCatalog.Item> items, List<Integer> sizes) {
        int[] counts = sizes.stream().mapToInt(Integer::intValue).toArray();
        return new Page(items, Centered.grid(counts), counts.length);
    }
}
