package net.skybornmc.skyblock.common.menu;

import java.util.regex.Pattern;

/**
 * Pure rules for typed text from a chat prompt or a Bedrock form ({@link MenuService#input}), unit-tested: colour codes
 * are removed (a form answer can contain § codes), the text is trimmed and checked against the length limit.
 */
public final class TypedText {

    private static final Pattern CODES = Pattern.compile("[§&][0-9a-fk-orx]", Pattern.CASE_INSENSITIVE);

    /** What the typed text is. */
    public enum Check {
        OK, EMPTY, TOO_LONG
    }

    /** The cleaned text and its check. */
    public record Result(String text, Check check) {
    }

    private TypedText() {
    }

    /** Removes colour codes and outer spaces, then checks {@code 1..maxLength} characters. */
    public static Result clean(String raw, int maxLength) {
        return clean(raw, maxLength, false);
    }

    /**
     * {@code keepCodes} (GUI-4c: staff texts such as display formats, tag displays and config values, where {@code &}
     * colour codes are wanted): the text is kept exactly as typed (codes and outer spaces: a format may end with a space),
     * only a {@code §} becomes {@code &} (the formats use {@code &}); an all-blank text is still empty. Otherwise as
     * {@link #clean(String, int)}.
     */
    public static Result clean(String raw, int maxLength, boolean keepCodes) {
        String input = raw == null ? "" : net.skybornmc.skyblock.common.pack.PrivateUse.strip(raw); // pack glyphs can't be typed
        String text = keepCodes ? input.replace('§', '&') : CODES.matcher(input).replaceAll("").strip();
        if (text.isBlank()) {
            return new Result(text, Check.EMPTY);
        }
        if (text.length() > Math.max(1, maxLength)) {
            return new Result(text, Check.TOO_LONG);
        }
        return new Result(text, Check.OK);
    }

    /** Bedrock players get a form when forms are on, they are on Bedrock and Floodgate's form API is there. */
    public static boolean useForm(boolean enabled, boolean bedrock, boolean available) {
        return enabled && bedrock && available;
    }
}
