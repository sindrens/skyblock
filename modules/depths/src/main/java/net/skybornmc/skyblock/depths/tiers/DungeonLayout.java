package net.skybornmc.skyblock.depths.tiers;

/**
 * Where the tier items of {@code /dungeon} go (UI_STYLE §5: content inside the frame, rows 2-5, columns 2-8). Tiers
 * are split over as few rows as possible, the rows are spread evenly and centred vertically, and each row is centred
 * horizontally; an even row leaves the middle column free so it stays symmetric (12 tiers → two rows of 6).
 */
public final class DungeonLayout {

    /** Content rows (rows 2-5 of a 6-row menu). */
    public static final int ROWS = 4;
    /** Content columns (columns 2-8). */
    public static final int COLUMNS = 7;
    /** Most tiers one tab can show. */
    public static final int MAX = ROWS * COLUMNS;

    private DungeonLayout() {
    }

    /** Slots of {@code count} tier items in tier order (at most {@link #MAX}; more are cut off). */
    public static int[] slots(int count) {
        int n = Math.max(0, Math.min(count, MAX));
        int[] out = new int[n];
        if (n == 0) {
            return out;
        }
        int rows = Math.min(ROWS, (n + COLUMNS - 1) / COLUMNS);
        int base = n / rows;
        int extra = n % rows;
        int firstRow = 1 + (ROWS - rows) / 2;
        int i = 0;
        for (int r = 0; r < rows; r++) {
            int k = base + (r < extra ? 1 : 0);
            for (int col : columns(k)) {
                out[i++] = (firstRow + r) * 9 + 1 + col;
            }
        }
        return out;
    }

    /** Columns (0-6) of {@code k} items centred in one row. */
    static int[] columns(int k) {
        int[] cols = new int[k];
        if (k % 2 == 1 || k >= COLUMNS) {
            int offset = (COLUMNS - k) / 2;
            for (int i = 0; i < k; i++) {
                cols[i] = offset + i;
            }
            return cols;
        }
        int half = k / 2;
        for (int i = 0; i < half; i++) {
            cols[i] = 3 - half + i;
            cols[half + i] = 4 + i;
        }
        return cols;
    }
}
