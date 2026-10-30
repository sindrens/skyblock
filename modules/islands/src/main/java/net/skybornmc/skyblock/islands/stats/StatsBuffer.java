package net.skybornmc.skyblock.islands.stats;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Counters that are not in the database yet (M2.6). {@link #add} is O(1); {@link #drain} hands the whole buffer to a
 * flush and starts a new one; a failed flush puts its counters back with {@link #mergeBack} (the write was one
 * transaction, so nothing of it reached the database and nothing is counted twice). Thread-safe (short, uncontended
 * locks; recording happens on the main thread).
 */
public final class StatsBuffer {

    private Map<StatKey, Long> pending = new HashMap<>();

    /** Adds a positive amount; zero/negative amounts are ignored. Saturates at {@link Long#MAX_VALUE}. */
    public synchronized void add(StatKey key, long amount) {
        if (amount <= 0) {
            return;
        }
        pending.merge(key, amount, StatsBuffer::saturatedAdd);
    }

    /** Takes every pending counter out (for a flush). */
    public synchronized Map<StatKey, Long> drain() {
        if (pending.isEmpty()) {
            return Map.of();
        }
        Map<StatKey, Long> out = pending;
        pending = new HashMap<>();
        return out;
    }

    /** Puts the counters of a failed flush back (added to anything recorded meanwhile). */
    public synchronized void mergeBack(Map<StatKey, Long> failed) {
        failed.forEach((key, amount) -> add(key, amount));
    }

    /** A copy of the pending counters of one island (for reads). */
    public synchronized Map<StatKey, Long> copyOf(UUID island) {
        Map<StatKey, Long> out = new HashMap<>();
        pending.forEach((key, amount) -> {
            if (key.island().equals(island)) {
                out.put(key, amount);
            }
        });
        return out;
    }

    /** Drops the pending counters of one island (admin reset). Returns how many counters were dropped. */
    public synchronized int discard(UUID island) {
        int before = pending.size();
        pending.keySet().removeIf(key -> key.island().equals(island));
        return before - pending.size();
    }

    public synchronized int size() {
        return pending.size();
    }

    public synchronized boolean isEmpty() {
        return pending.isEmpty();
    }

    static long saturatedAdd(long a, long b) {
        long r = a + b;
        return ((a ^ r) & (b ^ r)) < 0 ? Long.MAX_VALUE : r;
    }
}
