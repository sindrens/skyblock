package net.skybornmc.skyblock.production;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Generator lag guard (PHASE-7 M7.2): at most {@code max} custom rolls per island per wall-clock second; above it the
 * generator forms the plain block. One small counter per island, O(1). Main thread only; unit-tested.
 */
final class RollLimiter {

    private static final class Window {
        long second;
        int used;
    }

    private final Map<UUID, Window> windows = new HashMap<>();
    private volatile int max;

    RollLimiter(int max) {
        this.max = Math.max(0, max);
    }

    void max(int max) {
        this.max = Math.max(0, max);
    }

    /** True (and counted) when the island may roll once more in this second. */
    boolean tryRoll(UUID island, long nowMs) {
        long second = Math.floorDiv(nowMs, 1000L);
        Window w = windows.computeIfAbsent(island, k -> new Window());
        if (w.second != second) {
            w.second = second;
            w.used = 0;
        }
        if (w.used >= max) {
            return false;
        }
        w.used++;
        return true;
    }

    /** Drops counters older than the current second (call now and then so removed islands don't stay). */
    void prune(long nowMs) {
        long second = Math.floorDiv(nowMs, 1000L);
        windows.values().removeIf(w -> w.second < second);
    }

    void forget(UUID island) {
        windows.remove(island);
    }

    int tracked() {
        return windows.size();
    }
}
