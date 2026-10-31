package net.skybornmc.skyblock.rewards.votifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Connections per address in a fixed window (pure, clock passed in; thread-safe). A vote site sends one connection per
 * vote; anything above the limit is closed at once without reading. Memory stays bounded: old windows are dropped once
 * many addresses were seen.
 */
public final class IpRateLimiter {

    private static final int PRUNE_AT = 4_096;

    private record Window(long start, int count) {
    }

    private final Map<String, Window> windows = new HashMap<>();
    private volatile int max;
    private volatile long windowMillis;

    public IpRateLimiter(int max, long windowMillis) {
        configure(max, windowMillis);
    }

    public void configure(int max, long windowMillis) {
        this.max = Math.max(1, max);
        this.windowMillis = Math.max(1, windowMillis);
    }

    /** True when one more connection from {@code address} is allowed now (and counts it). */
    public synchronized boolean allow(String address, long now) {
        if (windows.size() >= PRUNE_AT) {
            windows.values().removeIf(w -> now - w.start() >= windowMillis);
            if (windows.size() >= PRUNE_AT) {
                windows.clear(); // a flood from thousands of addresses: start counting again rather than grow without end
            }
        }
        Window w = windows.get(address);
        if (w == null || now - w.start() >= windowMillis || now < w.start()) {
            windows.put(address, new Window(now, 1));
            return true;
        }
        if (w.count() >= max) {
            return false;
        }
        windows.put(address, new Window(w.start(), w.count() + 1));
        return true;
    }

    synchronized int tracked() {
        return windows.size();
    }
}
