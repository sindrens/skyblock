package net.skybornmc.skyblock.shop;

import net.skybornmc.skyblock.common.menu.Centered;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * The item grid of a shop tab (plan 4 Nov C §3 "more air", owner 3 Nov "no zigzag"). Each group starts its own row;
 * full rows hold 7, a group's last row is side by side and centred ({@link Centered#packed}, no holes). One empty row
 * separates two groups. A page uses screen rows 1-3 ({@link #ROWS}), so row 4 stays empty above the bottom bar; a group
 * that does not fit on the rest of a page starts the next page, a group longer than a page fills whole pages. Pure.
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
        List<Page> out = new ArrayList<>();
        Builder page = new Builder();
        for (ShopCatalog.Group group : groups) {
            List<ShopCatalog.Item> shown = new ArrayList<>();
            for (String id : group.items()) {
                ShopCatalog.Item item = enabledLookup.apply(id);
                if (item != null) {
                    shown.add(item);
                }
            }
            if (shown.isEmpty()) {
                continue;
            }
            int needed = (shown.size() + Centered.GRID_WIDTH - 1) / Centered.GRID_WIDTH;
            int row = page.empty() ? 1 : page.lastRow + 2; // one empty row between groups
            if (!page.empty() && row + needed - 1 > ROWS) {
                out.add(page.build());
                page = new Builder();
                row = 1;
            }
            for (int from = 0; from < shown.size(); from += Centered.GRID_WIDTH) {
                if (row > ROWS) {
                    out.add(page.build());
                    page = new Builder();
                    row = 1;
                }
                List<ShopCatalog.Item> chunk = shown.subList(from, Math.min(shown.size(), from + Centered.GRID_WIDTH));
                int[] columns = Centered.packed(chunk.size());
                for (int i = 0; i < chunk.size(); i++) {
                    page.add(chunk.get(i), 9 * row + columns[i]);
                }
                page.lastRow = row;
                row++;
            }
        }
        if (!page.empty()) {
            out.add(page.build());
        }
        return out;
    }

    private static final class Builder {
        final List<ShopCatalog.Item> items = new ArrayList<>();
        final List<Integer> slots = new ArrayList<>();
        int lastRow;

        boolean empty() {
            return items.isEmpty();
        }

        void add(ShopCatalog.Item item, int slot) {
            items.add(item);
            slots.add(slot);
        }

        Page build() {
            int[] s = new int[slots.size()];
            for (int i = 0; i < s.length; i++) {
                s[i] = slots.get(i);
            }
            return new Page(items, s, lastRow);
        }
    }
}
