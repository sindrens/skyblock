package net.skybornmc.skyblock.economy.sell;

import java.util.Arrays;

/**
 * Items sold per hour over the last 24 hours (rolling), immutable. Used for the adaptive impact (ECONOMY_BALANCE §8:
 * "recompute impact hourly from the rolling 24 h volume"). Stored as text {@code hour:count;hour:count} (hour = epoch hour).
 */
public final class VolumeWindow {

    static final int HOURS = 24;
    public static final VolumeWindow EMPTY = new VolumeWindow(new long[HOURS], new long[HOURS]);

    private final long[] hours;
    private final long[] counts;

    private VolumeWindow(long[] hours, long[] counts) {
        this.hours = hours;
        this.counts = counts;
    }

    public static long hourOf(long millis) {
        return Math.floorDiv(millis, 3_600_000L);
    }

    /** A window with {@code amount} more (negative to undo) in the hour of {@code now}; counts never go below 0. */
    public VolumeWindow add(long now, long amount) {
        long hour = hourOf(now);
        int i = (int) Math.floorMod(hour, (long) HOURS);
        long[] h = hours.clone();
        long[] c = counts.clone();
        if (h[i] != hour) {
            h[i] = hour;
            c[i] = 0;
        }
        c[i] = Math.max(0, saturatedAdd(c[i], amount));
        return new VolumeWindow(h, c);
    }

    /** Items sold in the 24 hours up to {@code now}. */
    public long total(long now) {
        long hour = hourOf(now);
        long sum = 0;
        for (int i = 0; i < HOURS; i++) {
            if (hours[i] > hour - HOURS && hours[i] <= hour) {
                sum = saturatedAdd(sum, counts[i]);
            }
        }
        return sum;
    }

    public String encode() {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < HOURS; i++) {
            if (counts[i] > 0) {
                if (!out.isEmpty()) {
                    out.append(';');
                }
                out.append(hours[i]).append(':').append(counts[i]);
            }
        }
        return out.toString();
    }

    /** Reads {@link #encode()} output; broken parts are skipped. */
    public static VolumeWindow decode(String text) {
        if (text == null || text.isBlank()) {
            return EMPTY;
        }
        long[] h = new long[HOURS];
        long[] c = new long[HOURS];
        for (String part : text.split(";")) {
            int colon = part.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            try {
                long hour = Long.parseLong(part.substring(0, colon).trim());
                long count = Long.parseLong(part.substring(colon + 1).trim());
                int i = (int) Math.floorMod(hour, (long) HOURS);
                if (count > 0 && hour >= h[i]) {
                    h[i] = hour;
                    c[i] = count;
                }
            } catch (NumberFormatException ignored) {
                // skipped
            }
        }
        return new VolumeWindow(h, c);
    }

    private static long saturatedAdd(long a, long b) {
        long r = a + b;
        if (((a ^ r) & (b ^ r)) < 0) {
            return b > 0 ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
        return r;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof VolumeWindow v && Arrays.equals(hours, v.hours) && Arrays.equals(counts, v.counts);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(hours) + Arrays.hashCode(counts);
    }
}
