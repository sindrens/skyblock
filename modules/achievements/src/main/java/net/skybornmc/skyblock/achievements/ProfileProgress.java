package net.skybornmc.skyblock.achievements;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Achievement progress of one profile in memory (main thread only). Before {@link #load} the values are what happened
 * since the profile came online (counter deltas, best gauge values); loading merges them with the stored values, so
 * nothing earned while the database answered is lost.
 *
 * <p>Saving: counters are saved as <b>deltas</b> ({@code amount = amount + ?}) and gauges as the <b>highest</b> value
 * ({@code GREATEST(amount, ?)}), so saves can run in any order and a failed save is simply retried.</p>
 */
final class ProfileProgress {

    /** One value to write. {@code value} is a delta for counters and the best value for gauges. */
    record Row(Metric metric, long value) {
    }

    private static final int SIZE = Metric.values().length;

    private final UUID key;
    private final UUID owner;
    private final long[] values = new long[SIZE];
    private final long[] unsaved = new long[SIZE];
    private final boolean[] gaugeDirty = new boolean[SIZE];
    private final Map<String, Integer> unlocked = new HashMap<>();
    private boolean loaded;
    private boolean loading;

    ProfileProgress(UUID key, UUID owner) {
        this.key = key;
        this.owner = owner;
    }

    /** The profile key (progress is stored under it). */
    UUID key() {
        return key;
    }

    /** The account the profile belongs to (titles are granted to it). */
    UUID owner() {
        return owner;
    }

    boolean loaded() {
        return loaded;
    }

    boolean loading() {
        return loading;
    }

    void loading(boolean loading) {
        this.loading = loading;
    }

    long value(Metric metric) {
        return values[metric.ordinal()];
    }

    /** Highest tier unlocked of an achievement (0 = none). */
    int unlocked(String achievement) {
        return unlocked.getOrDefault(achievement, 0);
    }

    Map<String, Integer> unlockedView() {
        return Collections.unmodifiableMap(unlocked);
    }

    void markUnlocked(String achievement, int tier) {
        unlocked.merge(achievement, tier, Math::max);
    }

    /**
     * Counter: adds {@code amount} (ignored when not positive). Gauge: keeps the higher value.
     *
     * @return true if the value went up
     */
    boolean apply(Metric metric, long amount) {
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

    /** Merges the stored values and unlocks into what was earned while loading. */
    void load(Map<Metric, Long> stored, Map<String, Integer> storedUnlocks) {
        for (Map.Entry<Metric, Long> e : stored.entrySet()) {
            int i = e.getKey().ordinal();
            long s = Math.max(0, e.getValue());
            if (e.getKey().counter()) {
                values[i] = ProgressMath.add(values[i], s);
            } else if (s >= values[i]) {
                values[i] = s;
                gaugeDirty[i] = false; // the stored value is already as high
            }
        }
        storedUnlocks.forEach(this::markUnlocked);
        loaded = true;
        loading = false;
    }

    boolean dirty() {
        for (int i = 0; i < SIZE; i++) {
            if (unsaved[i] != 0 || gaugeDirty[i]) {
                return true;
            }
        }
        return false;
    }

    /** Takes everything not saved yet (and clears it). Give it back with {@link #restore} when the write fails. */
    List<Row> drain() {
        List<Row> rows = new ArrayList<>();
        Metric[] metrics = Metric.values();
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

    /** A failed write: the rows are saved again next time (deltas added back, gauges marked again). */
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
