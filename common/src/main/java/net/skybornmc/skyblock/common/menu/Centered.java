package net.skybornmc.skyblock.common.menu;

/**
 * Centred spread patterns for small menus (I9 section 2). Pure, unit-tested.
 * <pre>
 *  1 = 4          2 = 3 5          3 = 2 4 6          4 = 1 3 5 7          5 = 0 2 4 6 8
 *  6 = 1 2 3 5 6 7    7 = 1..7    8 = 0 1 2 3 5 6 7 8    9 = 0..8   (6-9: gap in the middle, never lopsided)
 * </pre>
 * Up to 8 buttons fit one row of a 3-row menu (row 1); 9 to 14 use two rows of a 4-row menu (row 1 gets the larger
 * half). Lists use the 7x4 grid of 6-row menus ({@link #gridSlot}), filled left to right (grids are not centred).
 */
public final class Centered {

    /** The content slots of a 6-row list: 10-16, 19-25, 28-34, 37-43 (same as {@link PagedMenu#CONTENT_SLOTS}). */
    private static final int[] GRID = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43};

    /** Most buttons {@link #layout} places (two rows of 7). */
    public static final int MAX_SMALL = 14;

    private Centered() {
    }

    /** A centred layout: the menu height and the slots in reading order (the n-th visible entry goes in the n-th slot). */
    public record Layout(int rows, int[] slots) {
    }

    /** Column numbers (0-8) of {@code count} buttons in one row. */
    public static int[] columns(int count) {
        return switch (count) {
            case 0 -> new int[0];
            case 1 -> new int[]{4};
            case 2 -> new int[]{3, 5};
            case 3 -> new int[]{2, 4, 6};
            case 4 -> new int[]{1, 3, 5, 7};
            case 5 -> new int[]{0, 2, 4, 6, 8};
            case 6 -> new int[]{1, 2, 3, 5, 6, 7};
            case 7 -> new int[]{1, 2, 3, 4, 5, 6, 7};
            case 8 -> new int[]{0, 1, 2, 3, 5, 6, 7, 8};
            case 9 -> new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8};
            default -> throw new IllegalArgumentException("A row holds at most 9 buttons, got " + count);
        };
    }

    /**
     * 0-8 buttons: 3 rows, one centred row at row 1. 9-14: 4 rows, rows 1 and 2 (row 1 gets {@code ceil(count / 2)}).
     * More than 14 throws: use the 6-row grid.
     */
    public static Layout layout(int count) {
        if (count < 0 || count > MAX_SMALL) {
            throw new IllegalArgumentException("Centered.layout takes 0.." + MAX_SMALL + " buttons, got " + count + " (use the 6-row grid)");
        }
        if (count <= 8) {
            return new Layout(3, row(9, columns(count)));
        }
        int first = (count + 1) / 2;
        int[] top = row(9, columns(first));
        int[] bottom = row(18, columns(count - first));
        int[] slots = new int[count];
        System.arraycopy(top, 0, slots, 0, top.length);
        System.arraycopy(bottom, 0, slots, top.length, bottom.length);
        return new Layout(4, slots);
    }

    /** Slot of the {@code index}-th entry (0-27) of the 7x4 list grid. */
    public static int gridSlot(int index) {
        if (index < 0 || index >= GRID.length) {
            throw new IllegalArgumentException("The list grid has " + GRID.length + " slots, got index " + index);
        }
        return GRID[index];
    }

    /** Number of slots in the list grid (28). */
    public static int gridSize() {
        return GRID.length;
    }

    private static int[] row(int start, int[] columns) {
        int[] out = new int[columns.length];
        for (int i = 0; i < columns.length; i++) {
            out[i] = start + columns[i];
        }
        return out;
    }
}
