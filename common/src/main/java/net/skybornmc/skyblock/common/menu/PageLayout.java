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
        return grid(PagedMenu.CONTENT_SLOTS.length);
    }

    /** The grid with only its first {@code perPage} slots (whole rows of 7; a compact {@link PagedMenu}). */
    static <T> PageLayout<T> grid(int perPage) {
        int per = Math.max(1, Math.min(PagedMenu.CONTENT_SLOTS.length, perPage));
        return entries -> {
            List<Page<T>> out = new ArrayList<>();
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

    /**
     * Full rows of 7 with each page's last row centred ({@link Centered#grid}), {@code perPage} per page (whole rows).
     * Compact {@link PagedMenu}s use it so short lists look symmetrical (owner 3 Nov 2026).
     */
    static <T> PageLayout<T> centered(int perPage) {
        int per = Math.max(Centered.GRID_WIDTH, Math.min(PagedMenu.CONTENT_SLOTS.length, perPage));
        return entries -> {
            List<Page<T>> out = new ArrayList<>();
            for (int from = 0; from < entries.size(); from += per) {
                List<T> chunk = entries.subList(from, Math.min(entries.size(), from + per));
                int full = chunk.size() / Centered.GRID_WIDTH;
                int rest = chunk.size() % Centered.GRID_WIDTH;
                int[] counts = new int[full + (rest > 0 ? 1 : 0)];
                Arrays.fill(counts, 0, full, Centered.GRID_WIDTH);
                if (rest > 0) {
                    counts[full] = rest;
                }
                out.add(new Page<>(chunk, Centered.grid(counts)));
            }
            if (out.isEmpty()) {
                out.add(new Page<>(List.of(), new int[0]));
            }
            return out;
        };
    }
}
