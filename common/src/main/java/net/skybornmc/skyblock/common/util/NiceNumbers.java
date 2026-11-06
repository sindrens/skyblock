package net.skybornmc.skyblock.common.util;

/**
 * Owner 3 Nov 2026 ("make rewards less weird, even numbers ... all"): one rounding rule for every generated reward and
 * target, so players see 25, 120, 550, 4,000 instead of 27, 123, 543, 3,817. Pure.
 *
 * <ul>
 *   <li>below 20: as is;</li>
 *   <li>20 to 99: the nearest multiple of 5;</li>
 *   <li>100 and up: two significant digits, the leading pair snapped to a multiple of 5 when it is above 30
 *       (123 → 120, 166 → 170, 298 → 300, 543 → 550, 980 → 1,000, 3,817 → 4,000).</li>
 * </ul>
 */
public final class NiceNumbers {

    private NiceNumbers() {
    }

    public static long nice(long value) {
        if (value < 0) {
            return -nice(-value);
        }
        if (value < 20) {
            return value;
        }
        if (value < 100) {
            return Math.round(value / 5.0) * 5;
        }
        long scale = 1;
        while (value / scale >= 100) {
            scale *= 10;
        }
        long lead = Math.round((double) value / scale); // 10..100
        if (lead > 30) {
            lead = Math.round(lead / 5.0) * 5;
        }
        return lead * scale;
    }

    /** {@link #nice(long)} for a double (rounded first). */
    public static long nice(double value) {
        return nice(Math.round(value));
    }

    /** True when {@code nice(value) == value}. */
    public static boolean isNice(long value) {
        return nice(value) == value;
    }

    /** The next nice number above {@code value} (keeps a rounded series strictly increasing). */
    public static long next(long value) {
        long step = value < 20 ? 1 : value < 100 ? 5 : stepOf(value);
        long n = nice(value + step);
        return n > value ? n : nice(value + 2 * step);
    }

    private static long stepOf(long value) {
        long scale = 1;
        while (value / scale >= 100) {
            scale *= 10;
        }
        return (value / scale) >= 30 ? 5 * scale : scale; // from 30 on the leading pair moves in fives (300 -> 350)
    }

    /** Rounds a series and keeps it strictly increasing (a value that would not exceed the previous one moves up a step). */
    public static long[] series(long[] values) {
        long[] out = new long[values.length];
        long prev = Long.MIN_VALUE;
        for (int i = 0; i < values.length; i++) {
            long n = nice(values[i]);
            if (prev != Long.MIN_VALUE && n <= prev) {
                n = next(prev);
            }
            out[i] = n;
            prev = n;
        }
        return out;
    }
}
