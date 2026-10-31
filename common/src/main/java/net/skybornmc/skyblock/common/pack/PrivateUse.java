package net.skybornmc.skyblock.common.pack;

/**
 * Player text must never carry private-use characters (U+E000–U+F8FF): the pack draws rank badges, currency glyphs,
 * menu backgrounds and spaces with them, so a typed one could fake a badge or break a layout (must-have 5).
 */
public final class PrivateUse {

    private PrivateUse() {
    }

    public static boolean isPrivateUse(int cp) {
        return cp >= 0xE000 && cp <= 0xF8FF;
    }

    /** True when {@code text} contains at least one private-use character. */
    public static boolean contains(CharSequence text) {
        if (text == null) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (isPrivateUse(text.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    /** {@code text} without private-use characters (null stays null; the same instance when nothing changes). */
    public static String strip(String text) {
        if (text == null || !contains(text)) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!isPrivateUse(c)) {
                out.append(c);
            }
        }
        return out.toString();
    }
}
