package net.skybornmc.skyblock.progression;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * PERF5: what the periodic task check does for an island. Pure and main-thread only; unit-tested.
 *
 * <ul>
 *   <li><b>Spread:</b> the check runs every second for one slice of the islands ({@link #inSlice}), so every island
 *       with an online member is still checked once per {@code check-seconds}, but never all of them in one tick.</li>
 *   <li><b>Full or reach-only:</b> counter tasks complete the moment they are counted, so an island needs the full
 *       check (every task) only once after the load, after a catalog change (reload) and after a failed completion save
 *       ({@link #needsFull}); every later check reads only the reach tasks (skill, pillar and island level).</li>
 * </ul>
 */
final class TaskCheckPlan {

    private int generation;
    private final Map<UUID, Integer> fullChecked = new HashMap<>();

    /** Load finished or the catalog changed: every island needs one full check again. */
    void invalidateAll() {
        generation++;
        fullChecked.clear();
    }

    /** A completion save failed on this island: the next check is a full one (the task is claimed again). */
    void invalidate(UUID islandId) {
        if (islandId != null) {
            fullChecked.remove(islandId);
        }
    }

    /** True when the island has not had its full check since the last load, catalog change or failed save. */
    boolean needsFull(UUID islandId) {
        Integer g = fullChecked.get(islandId);
        return g == null || g != generation;
    }

    /** The island just had its full check. */
    void fullDone(UUID islandId) {
        if (islandId != null) {
            fullChecked.put(islandId, generation);
        }
    }

    void forget(UUID islandId) {
        fullChecked.remove(islandId);
    }

    int tracked() {
        return fullChecked.size();
    }

    /**
     * True when the island is checked in this second: islands are spread over {@code slices} seconds by their id, so
     * each one is checked exactly once every {@code slices} seconds.
     */
    static boolean inSlice(UUID islandId, long second, int slices) {
        int n = Math.max(1, slices);
        return Math.floorMod(islandId.hashCode(), n) == Math.floorMod(second, n);
    }
}
