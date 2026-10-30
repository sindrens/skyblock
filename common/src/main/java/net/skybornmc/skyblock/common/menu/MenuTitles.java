package net.skybornmc.skyblock.common.menu;

import java.util.List;

/**
 * Length rules for chest titles ({@code ▸ Island » Upgrades (2/5)}): Bedrock cuts long titles, so when the plain text is
 * longer than the limit the leading breadcrumbs are dropped first, then the last crumb is shortened with "..".
 */
final class MenuTitles {

    private MenuTitles() {
    }

    /**
     * @param crumbs    plain text of each breadcrumb
     * @param fixed     plain length of everything around the crumbs (marker, page suffix)
     * @param separator plain length of one separator between crumbs
     * @param max       maximum total length
     * @return index of the first crumb to keep
     */
    static int firstKept(List<String> crumbs, int fixed, int separator, int max) {
        for (int first = 0; first < crumbs.size(); first++) {
            if (length(crumbs, first, fixed, separator) <= max) {
                return first;
            }
        }
        return Math.max(0, crumbs.size() - 1);
    }

    static int length(List<String> crumbs, int first, int fixed, int separator) {
        int total = fixed;
        for (int i = first; i < crumbs.size(); i++) {
            total += crumbs.get(i).length() + (i > first ? separator : 0);
        }
        return total;
    }

    /** True if there is no visible crumb text at all (no crumbs, or only empty/blank ones). */
    static boolean blank(List<String> crumbs) {
        return crumbs.stream().allMatch(c -> c == null || c.isBlank());
    }

    /** Shortens {@code text} to {@code max} characters, ending with ".." when cut. */
    static String shorten(String text, int max) {
        if (text.length() <= max) {
            return text;
        }
        return max <= 2 ? text.substring(0, Math.max(0, max)) : text.substring(0, max - 2).stripTrailing() + "..";
    }
}
