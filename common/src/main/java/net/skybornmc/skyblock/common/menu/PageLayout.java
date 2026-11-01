package net.skybornmc.skyblock.common.menu;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * How a {@link PagedMenu} splits its entries into pages and where each entry goes (MENUS-V3 section 3). Pure. The
 * default {@link #grid()} is the classic 7x4 list (28 per page, left to right); the shop's grouped grid and the
 * podium boards ({@link PodiumPages}) supply their own.
 */
public interface PageLayout<T> {

    /** One page: its entries and the slot of each (same order, same length). */
    record Page<T>(List<T> entries, int[] slots) {
        public Page {
            entries = List.copyOf(entries);
            slots = slots.clone();
            if (entries.size() != slots.length) {
                throw new IllegalArgumentException("a page needs one slot per entry: " + entries.size() + " entries, " + slots.length + " slots");
            }
        }
    }

    /** The pages of {@code entries}; at least one (an empty page for an empty list). */
    List<Page<T>> pages(List<T> entries);

    /** Today's behaviour: 28 entries per page on {@link PagedMenu#CONTENT_SLOTS}, left to right. */
    static <T> PageLayout<T> grid() {
        return entries -> {
            List<Page<T>> out = new ArrayList<>();
            int per = PagedMenu.CONTENT_SLOTS.length;
            for (int from = 0; from < entries.size(); from += per) {
                List<T> chunk = entries.subList(from, Math.min(entries.size(), from + per));
                out.add(new Page<>(chunk, Arrays.copyOf(PagedMenu.CONTENT_SLOTS, chunk.size())));
            }
            if (out.isEmpty()) {
                out.add(new Page<>(List.of(), new int[0]));
            }
            return out;
        };
    }
}
