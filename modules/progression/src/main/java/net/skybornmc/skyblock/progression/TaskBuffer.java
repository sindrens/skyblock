package net.skybornmc.skyblock.progression;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Task counter deltas not written yet (write-behind, like the pillar XP). A flush drains everything; a failed write
 * merges it back, so nothing is lost and nothing is counted twice (the store adds deltas). Deliveries are never buffered:
 * they are saved at once, together with the item removal. Thread-safe.
 */
final class TaskBuffer {

    record Key(UUID island, String counter) {
    }

    private Map<Key, Long> deltas = new HashMap<>();

    synchronized void add(UUID island, String counter, long amount) {
        if (island == null || counter == null || amount <= 0) {
            return;
        }
        deltas.merge(new Key(island, counter), amount, TaskProgress::saturatedAdd);
    }

    synchronized Map<Key, Long> drain() {
        Map<Key, Long> out = deltas;
        deltas = new HashMap<>();
        return out;
    }

    synchronized void mergeBack(Map<Key, Long> batch) {
        batch.forEach((k, v) -> deltas.merge(k, v, TaskProgress::saturatedAdd));
    }

    synchronized boolean isEmpty() {
        return deltas.isEmpty();
    }

    /** Drops what an island has not written yet (the island was removed). */
    synchronized void forget(UUID island) {
        deltas.keySet().removeIf(k -> k.island().equals(island));
    }
}
