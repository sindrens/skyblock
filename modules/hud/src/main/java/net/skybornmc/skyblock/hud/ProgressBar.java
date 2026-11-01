package net.skybornmc.skyblock.hud;

import java.util.OptionalInt;

/** The scoreboard progress line (G7-R1-01), pure: how many dots are filled and how a placeholder becomes a percent. */
final class ProgressBar {

    private ProgressBar() {
    }

    /** Filled dots, rounded down so the bar is never fuller than the number (46 % of 10 = 4). */
    static int filled(int percent, int length) {
        int p = Math.max(0, Math.min(100, percent));
        return Math.max(0, p * Math.max(0, length) / 100);
    }

    /** {@code 46}, {@code 46%}, {@code " 46 "} or {@code 46.7} (decimals cut) as a percent clamped to 0..100; anything else empty. */
    static OptionalInt percent(String text) {
        if (text == null) {
            return OptionalInt.empty();
        }
        String t = text.strip();
        if (t.endsWith("%")) {
            t = t.substring(0, t.length() - 1).strip();
        }
        if (t.isEmpty()) {
            return OptionalInt.empty();
        }
        try {
            double value = Double.parseDouble(t);
            if (Double.isNaN(value) || t.chars().anyMatch(c -> c != '.' && c != '-' && (c < '0' || c > '9'))) {
                return OptionalInt.empty();
            }
            return OptionalInt.of((int) Math.max(0, Math.min(100, value)));
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
    }

    /** {@code count} copies of {@code dot} (an empty string for 0 or less). */
    static String repeat(String dot, int count) {
        return count <= 0 || dot == null ? "" : dot.repeat(count);
    }
}
