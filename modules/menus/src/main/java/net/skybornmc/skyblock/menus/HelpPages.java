package net.skybornmc.skyblock.menus;

/** Paging of the Bedrock help-page popup (GUI-4c): buttons are [Next page] (when there is one), then [Back]. Pure. */
final class HelpPages {

    private HelpPages() {
    }

    /** True when page {@code index} (0-based) of {@code count} has a next page. */
    static boolean hasNext(int index, int count) {
        return index >= 0 && index + 1 < count;
    }

    /** True when the pressed button (0-based) is Next page. */
    static boolean pressedNext(int pressed, boolean hasNext) {
        return hasNext && pressed == 0;
    }
}
