package net.skybornmc.skyblock.common.menu;

/**
 * Where the standard buttons sit, by menu size (docs/design/GUI_DESIGN.md §1). Pure, unit-tested.
 * <pre>
 *  6 rows: 45 Back · 46/47 actions · 48 ? · 49 Close · 50 Main menu · 51 action · 52 Previous · 53 Next
 *  5 rows: the same on row 4 (36 … 44)
 *  4 rows: the same on row 3 (27 Back, 30 ?, 31 Close, 32 Main menu, 34/35 pages)
 *  3 rows: 18 Back · 22 Close (no ?, no Main menu, no pages)
 *  1-2 rows: no standard buttons
 * </pre>
 * Back is never replaced by Previous page, so Back works on every page.
 */
public final class MenuLayout {

    /** No slot for this button at this size. */
    public static final int NONE = -1;

    private MenuLayout() {
    }

    /** First slot of the bottom bar for menus with 4+ rows. */
    static int bar(int rows) {
        return (rows - 1) * 9;
    }

    public static int back(int rows) {
        return rows >= 4 ? bar(rows) : rows == 3 ? 18 : NONE;
    }

    /** The "?" (what is this) button. */
    public static int info(int rows) {
        return rows >= 4 ? bar(rows) + 3 : NONE;
    }

    public static int close(int rows) {
        return rows >= 4 ? bar(rows) + 4 : rows == 3 ? 22 : NONE;
    }

    /** Main menu (on the main menu itself: Help & guides). */
    public static int mainMenu(int rows) {
        return rows >= 4 ? bar(rows) + 5 : NONE;
    }

    public static int previous(int rows) {
        return rows >= 4 ? bar(rows) + 7 : NONE;
    }

    public static int next(int rows) {
        return rows >= 4 ? bar(rows) + 8 : NONE;
    }

    /** Where the one "nothing here yet" item goes. */
    public static int empty(int rows) {
        return rows >= 5 ? 22 : rows >= 3 ? 13 : NONE;
    }

    /** True if {@code slot} holds a standard button at this size (the free action slots 46, 47 and 51 are not). */
    public static boolean standard(int rows, int slot) {
        if (slot < 0) {
            return false;
        }
        return slot == back(rows) || slot == info(rows) || slot == close(rows) || slot == mainMenu(rows)
                || slot == previous(rows) || slot == next(rows);
    }
}
