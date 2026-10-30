package net.skybornmc.skyblock.achievements;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Island achievement progress of one island in memory (AC3, main thread only). Every island of the season is loaded at
 * start (the island level and the island XP boost need the points of offline islands too). Before {@link #load} the
 * values are what happened since start; loading merges them with the stored values, like {@link ProfileProgress}.
 *
 * <p>Saving: counters as deltas, gauges as the highest value, so saves can run in any order and a failed save is retried.</p>
 */
final class IslandProgress {

    /** One value to write: a delta for counters, the best value for gauges. */
    record Row(IslandMetric metric, long value) {
    }

    private static final int SIZE = IslandMetric.values().length;

    private final UUID islandId;
    private final long[] values = new long[SIZE];
    private final long[] unsaved = new long[SIZE];
    private final boolean[] gaugeDirty = new boolean[SIZE];
    private final Map<String, Integer> unlocked = new HashMap<>();
    private boolean loaded;
    private int points;

    IslandProgress(UUID islandId) {
        this.islandId = islandId;
    }

    UUID islandId() {
        return islandId;
    }

    boolean loaded() {
        return loaded;
    }

    long value(IslandMetric metric) {
        return values[metric.ordinal()];
    }

    int unlocked(String achievement) {
        return unlocked.getOrDefault(achievement, 0);
    }

    Map<String, Integer> unlockedView() {
        return Collections.unmodifiableMap(unlocked);
    }

    void markUnlocked(String achievement, int tier) {
        unlocked.merge(achievement, tier, Math::max);
    }

    /** Island points (cached; {@link #points(AchievementRegistry, int[])} recomputes them). */
    int points() {
        return points;
    }

    void points(AchievementRegistry registry, int[] perTier) {
        points = BoostMath.islandPoints(registry, unlocked, perTier);
    }

    /** Counter: adds (ignored when not positive). Gauge: keeps the higher value. True if the value went up. */
    boolean apply(IslandMetric metric, long amount) {
        int i = metric.ordinal();
        if (metric.counter()) {
            if (amount <= 0) {
                return false;
            }
            values[i] = ProgressMath.add(values[i], amount);
            unsaved[i] = ProgressMath.add(unsaved[i], amount);
            return true;
        }
        if (amount <= values[i]) {
            return false;
        }
        values[i] = amount;
        gaugeDirty[i] = true;
        return true;
    }

    /** Merges stored values and unlocks into what was earned before the load finished. */
    void load(Map<IslandMetric, Long> stored, Map<String, Integer> storedUnlocks) {
        for (Map.Entry<IslandMetric, Long> e : stored.entrySet()) {
            int i = e.getKey().ordinal();
            long s = Math.max(0, e.getValue());
            if (e.getKey().counter()) {
                values[i] = ProgressMath.add(values[i], s);
            } else if (s >= values[i]) {
                values[i] = s;
                gaugeDirty[i] = false;
            }
        }
        storedUnlocks.forEach(this::markUnlocked);
        loaded = true;
    }

    /** Takes everything not saved yet (and clears it). Give it back with {@link #restore} when the write fails. */
    List<Row> drain() {
        List<Row> rows = new ArrayList<>();
        IslandMetric[] metrics = IslandMetric.values();
        for (int i = 0; i < SIZE; i++) {
            if (metrics[i].counter()) {
                if (unsaved[i] != 0) {
                    rows.add(new Row(metrics[i], unsaved[i]));
                    unsaved[i] = 0;
                }
            } else if (gaugeDirty[i]) {
                rows.add(new Row(metrics[i], values[i]));
                gaugeDirty[i] = false;
            }
        }
        return rows;
    }

    void restore(List<Row> rows) {
        for (Row row : rows) {
            int i = row.metric().ordinal();
            if (row.metric().counter()) {
                unsaved[i] = ProgressMath.add(unsaved[i], row.value());
            } else {
                gaugeDirty[i] = true;
            }
        }
    }
}
