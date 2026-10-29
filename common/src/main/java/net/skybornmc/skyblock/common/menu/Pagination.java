package net.skybornmc.skyblock.common.menu;

/** Page math for paged lists. Pages are 0-based; an empty list still has one (empty) page. */
public record Pagination(int totalItems, int perPage) {

    public Pagination {
        if (perPage <= 0) {
            throw new IllegalArgumentException("perPage must be > 0");
        }
        totalItems = Math.max(0, totalItems);
    }

    public int pages() {
        return Math.max(1, (totalItems + perPage - 1) / perPage);
    }

    /** Clamps {@code page} into {@code 0 .. pages()-1}. */
    public int clamp(int page) {
        return Math.max(0, Math.min(page, pages() - 1));
    }

    /** First item index (inclusive) of a page. */
    public int from(int page) {
        return clamp(page) * perPage;
    }

    /** Last item index (exclusive) of a page. */
    public int to(int page) {
        return Math.min(totalItems, from(page) + perPage);
    }

    public boolean hasPrevious(int page) {
        return clamp(page) > 0;
    }

    public boolean hasNext(int page) {
        return clamp(page) < pages() - 1;
    }
}
