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

    /** Widest row of the 7-wide item grid (columns 1-7). */
    public static final int GRID_WIDTH = 7;
    /** Most rows of the item grid (screen rows 1-4 of a 6-row menu). */
    public static final int GRID_ROWS = 4;

    /**
     * Row sizes of a group of {@code n} items in the 7-wide grid (MENUS-V3 D5): {@code ceil(n / 7)} rows, sizes as equal
     * as possible, larger rows first ({@code 16 = 6 + 5 + 5}, {@code 9 = 5 + 4}, {@code 12 = 6 + 6}). {@code n <= 7}
     * gives {@code {n}}; 0 gives an empty array.
     */
    public static int[] split(int n) {
        if (n <= 0) {
            return new int[0];
        }
        int rows = (n + GRID_WIDTH - 1) / GRID_WIDTH;
        int base = n / rows;
        int extra = n % rows;
        int[] out = new int[rows];
        for (int i = 0; i < rows; i++) {
            out[i] = base + (i < extra ? 1 : 0);
        }
        return out;
    }

    /**
     * Columns (1-7, screen columns of a 9-wide row) of a row of {@code count} items in the 7-wide grid (FORMAT 8): odd
     * counts are contiguous and centred, even counts are split around the centre column.
     * <pre>1 {4}  2 {3,5}  3 {3,4,5}  4 {2,3,5,6}  5 {2..6}  6 {1,2,3,5,6,7}  7 {1..7}</pre>
     */
    public static int[] gridColumns(int count) {
        return switch (count) {
            case 0 -> new int[0];
            case 1 -> new int[]{4};
            case 2 -> new int[]{3, 5};
            case 3 -> new int[]{3, 4, 5};
            case 4 -> new int[]{2, 3, 5, 6};
            case 5 -> new int[]{2, 3, 4, 5, 6};
            case 6 -> new int[]{1, 2, 3, 5, 6, 7};
            case 7 -> new int[]{1, 2, 3, 4, 5, 6, 7};
            default -> throw new IllegalArgumentException("A grid row holds at most " + GRID_WIDTH + " items, got " + count);
        };
    }

    /** Slots of sequential grid rows starting at screen row 1, one row per count ({@link #gridColumns} each). */
    public static int[] grid(int... rowCounts) {
        return gridFrom(1, rowCounts);
    }

    /**
     * Slots of sequential rows starting at screen row {@code firstRow} ({@code 9 * row + column}). Throws for more than
     * {@link #GRID_ROWS} rows, a row past screen row 4, or more than 7 items in a row.
     */
    public static int[] gridFrom(int firstRow, int... rowCounts) {
        if (rowCounts.length > GRID_ROWS || firstRow < 1 || firstRow + rowCounts.length - 1 > GRID_ROWS) {
            throw new IllegalArgumentException("The item grid has rows 1-" + GRID_ROWS + "; got " + rowCounts.length
                    + " rows from row " + firstRow);
        }
        int total = 0;
        for (int c : rowCounts) {
            total += c;
        }
        int[] out = new int[total];
        int i = 0;
        for (int r = 0; r < rowCounts.length; r++) {
            for (int col : gridColumns(rowCounts[r])) {
                out[i++] = 9 * (firstRow + r) + col;
            }
        }
        return out;
    }

    /**
     * Columns of {@code count} items side by side, centred, without holes (plan 4 Nov C §3, owner 3 Nov "no zigzag"):
     * the first column is {@code (9 - count) / 2}, so 7 = 1..7, 5 = 2..6, 4 = 2..5, 1 = 4. At most 7 (columns 1-7).
     */
    public static int[] packed(int count) {
        if (count < 0 || count > GRID_WIDTH) {
            throw new IllegalArgumentException("A row holds at most " + GRID_WIDTH + " items, got " + count);
        }
        int start = (9 - count) / 2;
        int[] out = new int[count];
        for (int i = 0; i < count; i++) {
            out[i] = start + i;
        }
        return out;
    }

    /**
     * Columns of one airy row (plan 4 Nov C §3, "more air"): up to 4 items keep an empty column between them
     * ({@link #columns}: 1 3 5 7), 5 to 7 sit side by side, centred ({@link #packed}). Never more than 7 in a row.
     */
    public static int[] airyColumns(int count) {
        return count <= 4 ? columns(count) : packed(count);
    }

    /**
     * An airy layout of {@code count} (0-14) equal buttons (plan 4 Nov C §3): rows of at most 7, sizes as equal as
     * possible ({@link #split}), each centred ({@link #airyColumns}). One row = row 1 of a 3-row menu; two rows = rows 1
     * and 3 of a 6-row menu, with an empty row between them and above the bottom bar.
     * <pre>
     *   9 = . . x x x x x . .   (row 1)       4 = . x . x . x . x .   (row 1, 3 rows)
     *       . . . . . . . . .
     *       . x . x . x . x .   (row 3, 6 rows)
     * </pre>
     */
    public static Layout airy(int count) {
        if (count < 0 || count > MAX_SMALL) {
            throw new IllegalArgumentException("Centered.airy takes 0.." + MAX_SMALL + " buttons, got " + count);
        }
        int[] sizes = split(count);
        if (sizes.length <= 1) {
            return new Layout(3, row(9, airyColumns(count)));
        }
        int[] top = row(9, airyColumns(sizes[0]));
        int[] bottom = row(27, airyColumns(sizes[1]));
        int[] slots = new int[count];
        System.arraycopy(top, 0, slots, 0, top.length);
        System.arraycopy(bottom, 0, slots, top.length, bottom.length);
        return new Layout(6, slots);
    }

    private static int[] row(int start, int[] columns) {
        int[] out = new int[columns.length];
        for (int i = 0; i < columns.length; i++) {
            out[i] = start + columns[i];
        }
        return out;
    }
}
