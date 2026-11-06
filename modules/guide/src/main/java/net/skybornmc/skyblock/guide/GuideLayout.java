package net.skybornmc.skyblock.guide;

import net.skybornmc.skyblock.common.menu.Centered;

/**
 * Slots of Kindle's menu (plan 4 Nov C §2): each group is one row, centred with an empty column between its buttons
 * ({@link Centered#columns}: 1 = 4, 2 = 3 5, 3 = 2 4 6, 4 = 1 3 5 7). Pure, unit-tested.
 */
final class GuideLayout {

    /** Most buttons one group row holds while keeping a gap between them. */
    static final int MAX_PER_ROW = 4;

    private GuideLayout() {
    }

    /** Slots of {@code count} buttons on screen row {@code row} (1-4 of a 6-row menu); more than 4 are cut. */
    static int[] row(int row, int count) {
        if (row < 1 || row > 4) {
            throw new IllegalArgumentException("Kindle's groups use rows 1-4, got " + row);
        }
        int[] columns = Centered.columns(Math.max(0, Math.min(MAX_PER_ROW, count)));
        int[] out = new int[columns.length];
        for (int i = 0; i < columns.length; i++) {
            out[i] = 9 * row + columns[i];
        }
        return out;
    }
}
