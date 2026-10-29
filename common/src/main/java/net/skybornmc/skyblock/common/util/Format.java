package net.skybornmc.skyblock.common.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Duration;
import java.util.Locale;

/** Player-facing number and time formatting, used everywhere (STANDARDS §1.3, UI_STYLE §1.3). */
public final class Format {

    private static final ThreadLocal<DecimalFormat> GROUPED =
            ThreadLocal.withInitial(() -> new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.ENGLISH)));

    private Format() {
    }

    /** {@code 1234567} → {@code "1,234,567"}. */
    public static String number(long value) {
        return GROUPED.get().format(value);
    }

    /** {@code 1234567} → {@code "1.2M"} (rounded down, never shows more than you have). */
    public static String compact(double value) {
        return NumberFormatter.compact(value);
    }

    /** Money: exact with commas below 100k ({@code "$12,500"}), compact above ({@code "$1.2M"}). */
    public static String money(long value) {
        return (value < 0 ? "-$" : "$") + (Math.abs(value) < 100_000 ? number(Math.abs(value)) : compact(Math.abs(value)));
    }

    /** {@code "2h 15m"}, {@code "3d 4h"}, {@code "45s"}; at most the two largest units. */
    public static String duration(Duration duration) {
        long seconds = Math.max(0, duration.toSeconds());
        long days = seconds / 86_400;
        long hours = seconds % 86_400 / 3_600;
        long minutes = seconds % 3_600 / 60;
        long secs = seconds % 60;
        if (days > 0) {
            return days + "d" + (hours > 0 ? " " + hours + "h" : "");
        }
        if (hours > 0) {
            return hours + "h" + (minutes > 0 ? " " + minutes + "m" : "");
        }
        if (minutes > 0) {
            return minutes + "m" + (secs > 0 ? " " + secs + "s" : "");
        }
        return secs + "s";
    }

    /**
     * Parses player amounts with suffixes: {@code "1500"}, {@code "1.5k"}, {@code "2M"}, {@code "3b"}, {@code "1,000"}.
     *
     * @throws IllegalArgumentException if it is not a valid positive amount or too large
     */
    public static long parseAmount(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("empty amount");
        }
        String text = input.trim().replace(",", "").replace("_", "").toLowerCase(Locale.ROOT);
        long multiplier = 1;
        char last = text.charAt(text.length() - 1);
        switch (last) {
            case 'k' -> multiplier = 1_000L;
            case 'm' -> multiplier = 1_000_000L;
            case 'b' -> multiplier = 1_000_000_000L;
            case 't' -> multiplier = 1_000_000_000_000L;
            case 'q' -> multiplier = 1_000_000_000_000_000L;
            default -> {
            }
        }
        if (multiplier != 1) {
            text = text.substring(0, text.length() - 1);
        }
        try {
            java.math.BigDecimal value = new java.math.BigDecimal(text).multiply(java.math.BigDecimal.valueOf(multiplier));
            if (value.signum() <= 0) {
                throw new IllegalArgumentException("amount must be above 0");
            }
            if (value.stripTrailingZeros().scale() > 0) {
                throw new IllegalArgumentException("amount must be a whole number");
            }
            return value.longValueExact();
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + input + "' is not an amount (examples: 1500, 1.5k, 2M)");
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("amount is too large");
        }
    }
}
