package net.skybornmc.skyblock.common.command;

import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses durations like {@code 30d}, {@code 2h30m}, {@code 1w2d}, {@code 45s}. */
public final class Durations {

    /** Longest accepted duration (10 years); longer input is almost always a typo. */
    public static final Duration MAX = Duration.ofDays(3650);
    private static final Pattern PART = Pattern.compile("(\\d+)([smhdw])");

    private Durations() {
    }

    /**
     * @throws IllegalArgumentException if the text is not a valid duration above 0 and at most {@link #MAX}
     */
    public static Duration parse(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("empty");
        }
        String text = input.trim().toLowerCase(Locale.ROOT);
        Matcher m = PART.matcher(text);
        int pos = 0;
        long seconds = 0;
        while (m.find()) {
            if (m.start() != pos) {
                throw new IllegalArgumentException("unexpected text");
            }
            long unit = switch (m.group(2)) {
                case "s" -> 1;
                case "m" -> 60;
                case "h" -> 3_600;
                case "d" -> 86_400;
                default -> 604_800;
            };
            try {
                seconds = Math.addExact(seconds, Math.multiplyExact(Long.parseLong(m.group(1)), unit));
            } catch (ArithmeticException | NumberFormatException e) {
                throw new IllegalArgumentException("too large");
            }
            pos = m.end();
        }
        if (pos == 0 || pos != text.length()) {
            throw new IllegalArgumentException("not a duration");
        }
        Duration d = Duration.ofSeconds(seconds);
        if (d.isZero()) {
            throw new IllegalArgumentException("must be above 0");
        }
        if (d.compareTo(MAX) > 0) {
            throw new IllegalArgumentException("too large");
        }
        return d;
    }
}
