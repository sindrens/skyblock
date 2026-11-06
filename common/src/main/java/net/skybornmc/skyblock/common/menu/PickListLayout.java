package net.skybornmc.skyblock.common.menu;

/**
 * Size and slots of a {@link PickListMenu} (small pick lists replace forward-only cyclers). Pure, unit-tested.
 * <pre>
 *  up to  7 choices: 3 rows (choices side by side, centred on row 1; 18 Back, 22 Close)
 *  up to 14 choices: 4 rows (two centred rows, Centered's split; bottom row on row 3)
 *  more:             6 rows, 28 per page (the 7x4 grid, left to right; 52/53 pages)
 * </pre>
 * 5-row menus are not used (I9 section 2).
 */
public final class PickListLayout {

    /** Choices per row on the 3-row list (columns 1-7). */
    public static final int PER_ROW = 7;
    /** Choices per page on the 6-row list. */
    public static final int PER_PAGE = 28;

    private PickListLayout() {
    }

    /** Rows of the list for {@code count} choices: 3, 4 or 6. */
    public static int rows(int count) {
        int n = Math.max(0, count);
        return n <= PER_ROW ? 3 : n <= 2 * PER_ROW ? 4 : 6;
    }

    /** Choices shown on one page of a list of {@code count} choices. */
    public static int perPage(int count) {
        return rows(count) == 6 ? PER_PAGE : Math.max(0, count);
    }

    /** Slot of the {@code index}-th of {@code shown} choices, for a list sized for {@code shown} choices. */
    public static int slot(int index, int shown) {
        return slot(index, shown, rows(shown));
    }

    /**
     * Slot of the {@code index}-th choice of a page that shows {@code shown} choices (both 0-based) in a list of
     * {@code rows} rows.
     *
     * @throws IllegalArgumentException if {@code index} is outside {@code 0 .. shown-1} or the page does not fit
     */
    public static int slot(int index, int shown, int rows) {
        if (shown > PER_PAGE || index < 0 || index >= shown) {
            throw new IllegalArgumentException("Choice " + index + " of " + shown);
        }
        if (rows == 6) {
            return Centered.gridSlot(index);
        }
        if (rows == 4) {
            if (shown > 2 * PER_ROW) {
                throw new IllegalArgumentException(shown + " choices do not fit 4 rows");
            }
            int first = (shown + 1) / 2;
            if (index < first) {
                return 9 + Centered.columns(first)[index];
            }
            return 18 + Centered.columns(shown - first)[index - first];
        }
        if (shown > PER_ROW) {
            throw new IllegalArgumentException(shown + " choices do not fit 3 rows");
        }
        int offset = (PER_ROW - shown) / 2;
        return 9 + 1 + offset + index;
    }
}
