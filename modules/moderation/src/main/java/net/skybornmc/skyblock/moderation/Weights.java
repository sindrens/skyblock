package net.skybornmc.skyblock.moderation;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Warn weights (spec 12.7) as whole tenths, so sums are exact: 0.5 + 0.5 + 1 = 5 + 5 + 10 = 20 = 2.0. Never floating
 * point. A weight has at most one decimal and is between 0.1 and {@link #MAX}.
 */
final class Weights {

    /** Weight 1 (the default of every warn and of every row stored before P3). */
    static final int ONE = 10;
    /** Largest weight one warn may have (10.0). */
    static final int MAX = 100;

    private Weights() {
    }

    /**
     * Parses {@code 0.5}, {@code 1}, {@code 2}, {@code 1.5}.
     *
     * @throws IllegalArgumentException when it is not a number with at most one decimal between 0.1 and 10
     */
    static int parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("empty");
        }
        BigDecimal d;
        try {
            d = new BigDecimal(raw.strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("not a number");
        }
        return of(d);
    }

    /** Tenths of a decimal value (at most one decimal, 0.1 to 10). */
    static int of(BigDecimal d) {
        BigDecimal tenths = d.movePointRight(1);
        if (tenths.stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException("more than one decimal");
        }
        int v;
        try {
            v = tenths.intValueExact();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("too large");
        }
        if (v < 1 || v > MAX) {
            throw new IllegalArgumentException("out of range");
        }
        return v;
    }

    /** Tenths from a database value; {@code null} (rows from before P3) = 1. Bad values are clamped, never thrown. */
    static int fromDb(BigDecimal d) {
        if (d == null) {
            return ONE;
        }
        int v = d.movePointRight(1).setScale(0, RoundingMode.HALF_UP).intValue();
        return Math.max(1, Math.min(MAX, v));
    }

    static BigDecimal decimal(int tenths) {
        return BigDecimal.valueOf(tenths, 1);
    }

    /** {@code 15} → {@code "1.5"}, {@code 20} → {@code "2"}, {@code 0} → {@code "0"}. */
    static String text(int tenths) {
        return BigDecimal.valueOf(tenths, 1).stripTrailingZeros().toPlainString();
    }

    /** True when the weighted count reaches a rule's {@code at} (whole warnings): {@code sum >= at}. */
    static boolean reaches(int tenths, int at) {
        return tenths >= at * ONE;
    }
}
