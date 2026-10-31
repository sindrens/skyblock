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
        String text = raw == null ? "" : CODES.matcher(raw).replaceAll("").strip();
        if (text.isEmpty()) {
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
