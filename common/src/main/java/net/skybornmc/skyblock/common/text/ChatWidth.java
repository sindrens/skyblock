package net.skybornmc.skyblock.common.text;

/**
 * Pixel widths of text in the Minecraft Java default font (chat at the default scale), for centering lines in chat
 * ({@link ChatCard}). Pure, unit-tested.
 *
 * <p>Each value is the glyph's advance: its pixel width plus the 1 px gap after it. Bold adds 1 px to every character
 * (spaces too). Letters and digits are 6 px except the narrow ones ({@code i l t f k I}); characters past Latin-1 (✦,
 * ◆ ...) use {@link #WIDE}. The chat box is {@link #CHAT_WIDTH} px wide at the default chat width setting. The Bedrock
 * font has other widths, so Bedrock lines are never centred.</p>
 */
public final class ChatWidth {

    /** Width of the chat box in pixels (default chat width, scale 1). */
    public static final int CHAT_WIDTH = 320;
    /** Advance of a normal (not bold) space. */
    public static final int SPACE = 4;
    /** Advance used for characters past Latin-1 (symbols from the unicode font pages). */
    static final int WIDE = 8;
    /** Advance of every other character (most letters, digits and signs). */
    static final int NORMAL = 6;

    private ChatWidth() {
    }

    /** Advance of one character in pixels, including the gap after it; bold adds 1. */
    public static int advance(int codePoint, boolean bold) {
        return base(codePoint) + (bold ? 1 : 0);
    }

    private static int base(int cp) {
        return switch (cp) {
            case ' ' -> SPACE;
            case '!', '\'', ',', '.', ':', ';', 'i', '|' -> 2;
            case '`', 'l' -> 3;
            case '"', '(', ')', '*', 'I', '[', ']', 't', '{', '}' -> 4;
            case '<', '>', 'f', 'k' -> 5;
            case '@', '~' -> 7;
            default -> cp < 0x20 ? 0 : cp > 0xFF ? WIDE : NORMAL;
        };
    }

    /** Width of {@code text} in pixels, all in one style (bold or not). */
    public static int width(String text, boolean bold) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            total += advance(cp, bold);
            i += Character.charCount(cp);
        }
        return total;
    }

    /**
     * Number of normal spaces to put before a line of {@code lineWidth} px so it sits in the middle of a chat box of
     * {@code chatWidth} px (rounded to the nearest space; 0 when the line is as wide as the box or wider).
     */
    public static int padSpaces(int lineWidth, int chatWidth) {
        int free = chatWidth - lineWidth;
        if (free <= 0) {
            return 0;
        }
        return (int) Math.round(free / 2.0 / SPACE);
    }

    /** {@link #padSpaces} spaces as a string, for the default {@link #CHAT_WIDTH}. */
    public static String pad(int lineWidth) {
        return " ".repeat(padSpaces(lineWidth, CHAT_WIDTH));
    }
}
