package net.skybornmc.skyblock.common.menu;

/**
 * Size and slots of a {@link PickListMenu} (GUI_DESIGN §5: forward-only cyclers become a small pick list). Pure,
 * unit-tested.
 * <pre>
 *  up to  7 choices: 3 rows (choices centred on row 1, 18 Back, 22 Close)
 *  up to 14 choices: 4 rows (rows 1-2, bottom bar on row 3)
 *  up to 21 choices: 5 rows (rows 1-3, bottom bar on row 4)
 *  more:             6 rows, 28 per page (rows 1-4, 52/53 pages)
 * </pre>
 * Each content row holds 7 choices (columns 1-7); a last row that is not full is centred.
 */
public final class PickListLayout {

    /** Choices per content row (columns 1-7 inside the frame). */
    public static final int PER_ROW = 7;
    /** Choices per page on the 6-row list. */
    public static final int PER_PAGE = 28;

    private PickListLayout() {
    }

    /** Rows of the list for {@code count} choices (at least 3, at most 6). */
    public static int rows(int count) {
        int contentRows = Math.max(1, (Math.max(0, count) + PER_ROW - 1) / PER_ROW);
        return contentRows >= 4 ? 6 : contentRows + 2;
    }

    /** Choices shown on one page of a list of {@code count} choices. */
    public static int perPage(int count) {
        return rows(count) == 6 ? PER_PAGE : Math.max(0, count);
    }

    /**
     * Slot of the {@code index}-th choice of a page that shows {@code shown} choices (both 0-based).
     *
     * @throws IllegalArgumentException if {@code index} is outside {@code 0 .. shown-1} or {@code shown} > 28
     */
    public static int slot(int index, int shown) {
        if (shown > PER_PAGE || index < 0 || index >= shown) {
            throw new IllegalArgumentException("Choice " + index + " of " + shown);
        }
        int row = index / PER_ROW;
        int col = index % PER_ROW;
        int inRow = Math.min(PER_ROW, shown - row * PER_ROW);
        int offset = (PER_ROW - inRow) / 2;
        return (row + 1) * 9 + 1 + offset + col;
    }
}
