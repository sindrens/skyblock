package net.skybornmc.skyblock.common.menu;

/**
 * Where the standard buttons sit, by menu size (I9 section 4: a minimal fixed bottom row). Pure, unit-tested.
 * <pre>
 *  Rows | Back | Action A | Action B | Close | Search | Previous | Next | Danger corner
 *   6   |  45  |    46    |    47    |  49   |   51   |    52    |  53  |   8
 *   4   |  27  |    28    |    29    |  31   |   -    |    34    |  35  |   8
 *   3   |  18  |    19    |    20    |  22   |   -    |    25    |  26  |   8
 *   5   |  36  |    37    |    38    |  40   |   -    |    43    |  44  |   8   (legacy, until migrated)
 *  1-2  |  no standard buttons
 * </pre>
 * Back is never replaced by Previous page, so Back works on every page. Search (51) is the auction house's only.
 * Slot 48 is free (the old {@code ?} button is gone); 50 (bar + 5) is Help where a menu has one ({@link #help});
 * 46, 47 and 51 are screen actions.
 */
public final class MenuLayout {

    /** No slot for this button at this size. */
    public static final int NONE = -1;

    private MenuLayout() {
    }

    /** First slot of the bottom row for menus with 4+ rows. */
    static int bar(int rows) {
        return (rows - 1) * 9;
    }

    public static int back(int rows) {
        return rows >= 4 && rows <= 6 ? bar(rows) : rows == 3 ? 18 : NONE;
    }

    /** First free screen action slot of the bottom row (shop: Sell). */
    public static int actionA(int rows) {
        return rows >= 4 && rows <= 6 ? bar(rows) + 1 : rows == 3 ? 19 : NONE;
    }

    /** Second free screen action slot of the bottom row (shop: Undo). */
    public static int actionB(int rows) {
        return rows >= 4 && rows <= 6 ? bar(rows) + 2 : rows == 3 ? 20 : NONE;
    }

    public static int close(int rows) {
        return rows >= 4 && rows <= 6 ? bar(rows) + 4 : rows == 3 ? 22 : NONE;
    }

    /**
     * Help (a plain book, plan 4 Nov C §3: the same slot on every menu that has one): the slot right of Close, 50 on 6
     * rows (the /menu hub's Help), 32 on 4, 23 on 3.
     */
    public static int help(int rows) {
        return rows >= 4 && rows <= 6 ? bar(rows) + 5 : rows == 3 ? 23 : NONE;
    }

    /** The auction house's Search button (6 rows only, I9 section 11). */
    public static int search(int rows) {
        return rows == 6 ? 51 : NONE;
    }

    /** Previous page; 3-row menus have it on 25 (FORMAT 3.1, MENUS-V3). */
    public static int previous(int rows) {
        return rows >= 4 && rows <= 6 ? bar(rows) + 7 : rows == 3 ? 25 : NONE;
    }

    /** Next page; 3-row menus have it on 26 (FORMAT 3.1, MENUS-V3). */
    public static int next(int rows) {
        return rows >= 4 && rows <= 6 ? bar(rows) + 8 : rows == 3 ? 26 : NONE;
    }

    /** The red danger tile corner (top right, far from Back, pages and the main action; I9 section 5). */
    public static int danger(int rows) {
        return rows >= 3 && rows <= 6 ? 8 : NONE;
    }

    /** Where the one "nothing here yet" item goes. */
    public static int empty(int rows) {
        return rows >= 5 ? 22 : rows >= 3 ? 13 : NONE;
    }

    /** True if {@code slot} holds a standard button at this size: Back, Close, Previous or Next. */
    public static boolean standard(int rows, int slot) {
        if (slot < 0) {
            return false;
        }
        return slot == back(rows) || slot == close(rows) || slot == previous(rows) || slot == next(rows);
    }
}
