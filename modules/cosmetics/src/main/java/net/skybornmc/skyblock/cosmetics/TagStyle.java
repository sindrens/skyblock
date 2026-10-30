package net.skybornmc.skyblock.cosmetics;

/**
 * How a tag is shown after a name: {@code Pouched (Phoenix)}. The tag's own display (its colors) goes inside the
 * configurable {@code tag.format} ({@code cosmetics.yml}, default muted parentheses).
 *
 * <p>Older tags were written with their own brackets ({@code <c:brand>[<grad:phoenix>Phoenix</grad>]}); those outer
 * brackets are removed ({@link #unbracket}) so a tag never shows as {@code ([Phoenix])}.</p>
 */
final class TagStyle {

    static final String PLACEHOLDER = "<tag>";

    private TagStyle() {
    }

    /**
     * Removes one pair of outer square brackets from a MiniMessage (or {@code &}-coded) display when its visible text
     * starts with {@code [} and ends with {@code ]}. Tags ({@code <...>}) and color codes are kept.
     */
    static String unbracket(String raw) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }
        int open = firstVisible(raw);
        int close = lastVisible(raw);
        if (open < 0 || close <= open || raw.charAt(open) != '[' || raw.charAt(close) != ']') {
            return raw;
        }
        return raw.substring(0, open) + raw.substring(open + 1, close) + raw.substring(close + 1);
    }

    /** Raw MiniMessage of the wrapped tag (for item lore built from strings). Empty format = the tag alone. */
    static String wrapRaw(String format, String inner) {
        if (format == null || format.isBlank() || !format.contains(PLACEHOLDER)) {
            return inner;
        }
        return format.replace(PLACEHOLDER, inner);
    }

    /** Index of the first visible character (outside {@code <tags>} and {@code &x}/{@code §x} codes), or -1. */
    private static int firstVisible(String s) {
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '<') {
                int end = s.indexOf('>', i);
                if (end < 0) {
                    return i;
                }
                i = end + 1;
            } else if ((c == '&' || c == '§') && i + 1 < s.length() && isCode(s, i + 1)) {
                i += s.charAt(i + 1) == '#' ? 8 : 2;
            } else if (Character.isWhitespace(c)) {
                i++;
            } else {
                return i;
            }
        }
        return -1;
    }

    /** Index of the last visible character (outside {@code <tags>}), or -1. */
    private static int lastVisible(String s) {
        int i = s.length() - 1;
        while (i >= 0) {
            char c = s.charAt(i);
            if (c == '>') {
                int start = s.lastIndexOf('<', i);
                if (start < 0) {
                    return i;
                }
                i = start - 1;
            } else if (Character.isWhitespace(c)) {
                i--;
            } else {
                return i;
            }
        }
        return -1;
    }

    private static boolean isCode(String s, int i) {
        char c = Character.toLowerCase(s.charAt(i));
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'k' && c <= 'o') || c == 'r'
                || (c == '#' && i + 7 <= s.length());
    }
}
