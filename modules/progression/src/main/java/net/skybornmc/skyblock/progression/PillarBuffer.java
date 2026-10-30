package net.skybornmc.skyblock.progression;

import net.skybornmc.skyblock.common.progression.Pillar;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Pillar XP not written yet (write-behind, like the island stats of M2.6). Deltas per island and pillar, and per island,
 * pillar and week for the weekly board (M5.7). A flush drains everything; a failed write merges it back, so nothing is
 * lost and nothing is counted twice (the store adds deltas). Thread-safe (drained on a database thread).
 */
final class PillarBuffer {

    record Key(UUID island, Pillar pillar) {
    }

    record WeekKey(UUID island, Pillar pillar, int weekStart) {
    }

    /** Weekly gain: XP and levels. */
    record Week(double xp, int levels) {
        Week plus(Week other) {
            return new Week(xp + other.xp, levels + other.levels);
        }
    }

    /** One drained batch. {@code levels} is the pillar level at drain time (stored for queries). */
    record Batch(Map<Key, Double> xp, Map<Key, Integer> levels, Map<WeekKey, Week> weekly) {
        boolean isEmpty() {
            return xp.isEmpty() && weekly.isEmpty();
        }
    }

    private Map<Key, Double> xp = new HashMap<>();
    private Map<WeekKey, Week> weekly = new HashMap<>();

    synchronized void add(UUID island, Pillar pillar, int weekStart, double amount, int levelsGained) {
        if (island == null || pillar == null || !(amount >= 0) || Double.isInfinite(amount) || (amount == 0 && levelsGained <= 0)) {
            return;
        }
        if (amount > 0) {
            xp.merge(new Key(island, pillar), amount, Double::sum);
        }
        weekly.merge(new WeekKey(island, pillar, weekStart), new Week(amount, Math.max(0, levelsGained)), Week::plus);
    }

    /** Takes everything out; {@code levelOf} gives the current level of a key for the stored level column. */
    synchronized Batch drain(java.util.function.ToIntFunction<Key> levelOf) {
        Map<Key, Double> x = xp;
        Map<WeekKey, Week> w = weekly;
        xp = new HashMap<>();
        weekly = new HashMap<>();
        Map<Key, Integer> levels = new HashMap<>();
        for (Key k : x.keySet()) {
            levels.put(k, levelOf.applyAsInt(k));
        }
        return new Batch(x, levels, w);
    }

    /** Puts a batch whose write failed back (adds to what came in since). */
    synchronized void mergeBack(Batch batch) {
        batch.xp().forEach((k, v) -> xp.merge(k, v, Double::sum));
        batch.weekly().forEach((k, v) -> weekly.merge(k, v, Week::plus));
    }

    synchronized boolean isEmpty() {
        return xp.isEmpty() && weekly.isEmpty();
    }

    synchronized int size() {
        return xp.size() + weekly.size();
    }
}
