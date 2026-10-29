package net.skybornmc.skyblock.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Player-facing number formatting: 950, 1.2k, 3.4M, 5B, 1.1T (see STANDARDS.md §1.3). */
public final class NumberFormatter {

    private static final String[] SUFFIXES = {"", "k", "M", "B", "T", "Q"};

    private NumberFormatter() {
    }

    public static String compact(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return "?";
        }
        boolean negative = value < 0;
        double abs = Math.abs(value);
        int index = 0;
        while (abs >= 1000 && index < SUFFIXES.length - 1) {
            abs /= 1000;
            index++;
        }
        BigDecimal rounded = BigDecimal.valueOf(abs).setScale(index == 0 ? 0 : 1, RoundingMode.DOWN).stripTrailingZeros();
        return (negative ? "-" : "") + rounded.toPlainString() + SUFFIXES[index];
    }
}
