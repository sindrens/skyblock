package net.skybornmc.skyblock.common.util;

import java.util.UUID;

/**
 * Spreads per-player (or per-island) periodic work over the ticks of its period (PERF2, docs/planning/PERFORMANCE.md
 * P-12): a task that used to handle everybody every {@code period} ticks runs every tick instead and handles only the
 * entries whose bucket is due, so each entry is still handled exactly once per period but the cost is split into
 * {@code period} small slices instead of one spike. Pure, unit-tested.
 */
public final class Stagger {

    private Stagger() {
    }

    /** Whether the entry with this hash is handled on {@code tick} when every entry is handled once per {@code period}. */
    public static boolean due(int hash, long tick, int period) {
        if (period <= 1) {
            return true;
        }
        return Math.floorMod(spread(hash), period) == Math.floorMod(tick, period);
    }

    /** {@link #due(int, long, int)} for a UUID. */
    public static boolean due(UUID id, long tick, int period) {
        return id == null || due(id.hashCode(), tick, period);
    }

    /** Mixes the hash so that ids with similar low bits still land in different buckets. */
    static int spread(int hash) {
        int h = hash * 0x9E3779B9;
        return h ^ (h >>> 16);
    }
}
