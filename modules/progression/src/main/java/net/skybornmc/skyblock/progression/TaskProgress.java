package net.skybornmc.skyblock.progression;

import net.skybornmc.skyblock.progression.TaskCatalog.TaskDef;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.ToLongFunction;

/**
 * Task progress of every island in memory (PHASE-5 M5.3). Pure and main-thread only; unit-tested.
 *
 * <ul>
 *   <li><b>Shared counters:</b> one counter per island and counter key ({@code mine:coal_ore}); every member's action adds
 *       to it and every task reading it sees it, from the island's creation on (owner P5-9).</li>
 *   <li><b>Exactly once:</b> a task that reaches its goal moves to "completing" and is handed out once; only
 *       {@link #finish} with success marks it done. A failed save puts it back so it is tried again.</li>
 *   <li><b>Deliveries:</b> {@link #reserve} holds room for items being handed in, so two members delivering at the same
 *       time can never hand in more than the goal.</li>
 * </ul>
 */
final class TaskProgress {

    /** One island's state. */
    static final class Island {
        final Map<String, Long> counters = new HashMap<>();
        final Map<String, Long> reserved = new HashMap<>();
        final Set<String> done = new HashSet<>();
        final Set<String> completing = new HashSet<>();

        long counter(String key) {
            return key == null ? 0 : counters.getOrDefault(key, 0L);
        }
    }

    private final Map<UUID, Island> islands = new HashMap<>();
    private TaskCatalog catalog;

    TaskProgress(TaskCatalog catalog) {
        this.catalog = catalog;
    }

    void catalog(TaskCatalog catalog) {
        this.catalog = catalog;
    }

    TaskCatalog catalog() {
        return catalog;
    }

    Island island(UUID islandId) {
        return islands.computeIfAbsent(islandId, k -> new Island());
    }

    Island peek(UUID islandId) {
        return islandId == null ? null : islands.get(islandId);
    }

    Set<UUID> islandIds() {
        return islands.keySet();
    }

    void forget(UUID islandId) {
        islands.remove(islandId);
    }

    /** Adds stored values (load) on top of what was counted meanwhile. */
    void addStored(UUID islandId, String key, long amount) {
        if (islandId != null && key != null && amount > 0) {
            island(islandId).counters.merge(key, amount, TaskProgress::saturatedAdd);
        }
    }

    void markDone(UUID islandId, String taskId) {
        Island s = island(islandId);
        s.done.add(taskId);
        s.completing.remove(taskId);
    }

    /** Adds to a shared counter. Returns false (and changes nothing) for untracked keys or non-positive amounts. */
    boolean add(UUID islandId, String key, long amount) {
        if (islandId == null || key == null || amount <= 0 || !catalog.tracks(key)) {
            return false;
        }
        island(islandId).counters.merge(key, amount, TaskProgress::saturatedAdd);
        return true;
    }

    long counter(UUID islandId, String key) {
        Island s = peek(islandId);
        return s == null ? 0 : s.counter(key);
    }

    boolean done(UUID islandId, String taskId) {
        Island s = peek(islandId);
        return s != null && s.done.contains(taskId);
    }

    boolean completing(UUID islandId, String taskId) {
        Island s = peek(islandId);
        return s != null && s.completing.contains(taskId);
    }

    int doneCount(UUID islandId) {
        Island s = peek(islandId);
        if (s == null) {
            return 0;
        }
        int n = 0;
        for (TaskDef def : catalog.all()) {
            if (s.done.contains(def.id())) {
                n++;
            }
        }
        return n;
    }

    /** Counted progress of a counter task, 0 to its amount. */
    long counted(UUID islandId, TaskDef def) {
        if (done(islandId, def.id())) {
            return def.info().amount();
        }
        return Math.min(def.info().amount(), counter(islandId, def.info().counterKey()));
    }

    /**
     * Counter tasks reading {@code key} that just reached their goal: moved to "completing" and returned once.
     */
    List<TaskDef> readyFor(UUID islandId, String key) {
        Island s = peek(islandId);
        if (s == null) {
            return List.of();
        }
        List<TaskDef> out = new ArrayList<>();
        for (TaskDef def : catalog.byCounter(key)) {
            claimIfReady(s, def, s.counter(key), out);
        }
        return out;
    }

    /**
     * Every task of the island that is ready now (counter tasks from their counter, reach tasks from {@code reach}, the
     * live value). Used by the periodic check, after a load and after a catalog change. Claims them like
     * {@link #readyFor}.
     */
    List<TaskDef> readyAll(UUID islandId, ToLongFunction<TaskDef> reach) {
        Island s = island(islandId);
        List<TaskDef> out = new ArrayList<>();
        for (TaskDef def : catalog.all()) {
            if (settled(s, def)) {
                continue; // PERF5: done or completing tasks are never claimed, so their live value is not read
            }
            String key = def.info().counterKey();
            long value = key != null ? s.counter(key) : reach == null ? 0 : reach.applyAsLong(def);
            claimIfReady(s, def, value, out);
        }
        return out;
    }

    /**
     * PERF5: only the reach tasks (skill, pillar, island level) of the island that are ready now, claimed like
     * {@link #readyFor}. Counter tasks complete the moment they are counted ({@link #readyFor}, {@link #release}), so
     * the periodic check reads only these once an island had its full check ({@link #readyAll}) after a load, a catalog
     * change or a failed save. Done and completing tasks are skipped before their live value is read.
     */
    List<TaskDef> readyReach(UUID islandId, ToLongFunction<TaskDef> reach) {
        Island s = peek(islandId);
        List<TaskDef> out = new ArrayList<>();
        for (TaskDef def : catalog.reach()) {
            if (s != null && settled(s, def)) {
                continue;
            }
            long value = reach == null ? 0 : reach.applyAsLong(def);
            if (value >= def.info().amount()) {
                claimIfReady(s == null ? island(islandId) : s, def, value, out);
            }
        }
        return out;
    }

    private static boolean settled(Island s, TaskDef def) {
        return s.done.contains(def.id()) || s.completing.contains(def.id());
    }

    private static void claimIfReady(Island s, TaskDef def, long value, List<TaskDef> out) {
        if (value >= def.info().amount() && !s.done.contains(def.id()) && s.completing.add(def.id())) {
            out.add(def);
        }
    }

    /**
     * Result of a completion save: success marks the task done, failure releases it to be tried again later.
     *
     * @return the unlock keys of the task that are now complete (success only)
     */
    List<String> finish(UUID islandId, TaskDef def, boolean success) {
        Island s = island(islandId);
        s.completing.remove(def.id());
        if (!success) {
            return List.of();
        }
        s.done.add(def.id());
        List<String> complete = new ArrayList<>();
        for (String key : def.info().unlocks()) {
            if (unlocked(islandId, key)) {
                complete.add(key);
            }
        }
        return complete;
    }

    /** True when every task listing the key is done (true when none lists it). */
    boolean unlocked(UUID islandId, String unlockKey) {
        Island s = peek(islandId);
        for (TaskDef def : catalog.byUnlock(unlockKey)) {
            if (s == null || !s.done.contains(def.id())) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ deliveries

    /** Items the task still takes: goal − delivered − reserved (0 when done or completing). */
    long room(UUID islandId, TaskDef def) {
        Island s = peek(islandId);
        if (s != null && (s.done.contains(def.id()) || s.completing.contains(def.id()))) {
            return 0;
        }
        String key = def.info().counterKey();
        long have = s == null ? 0 : s.counter(key) + s.reserved.getOrDefault(key, 0L);
        return Math.max(0, def.info().amount() - have);
    }

    /** Reserves up to {@code offered} items of room for a hand-in; returns the amount reserved (maybe 0). */
    long reserve(UUID islandId, TaskDef def, long offered) {
        long take = Math.min(Math.max(0, offered), room(islandId, def));
        if (take > 0) {
            island(islandId).reserved.merge(def.info().counterKey(), take, Long::sum);
        }
        return take;
    }

    /**
     * Ends a reservation. {@code committed} = items whose progress was saved (added to the counter); the rest of the
     * reservation is freed. Returns the tasks that became ready (claimed, see {@link #readyFor}).
     */
    List<TaskDef> release(UUID islandId, TaskDef def, long reserved, long committed) {
        Island s = island(islandId);
        String key = def.info().counterKey();
        long left = s.reserved.getOrDefault(key, 0L) - Math.max(0, reserved);
        if (left > 0) {
            s.reserved.put(key, left);
        } else {
            s.reserved.remove(key);
        }
        if (committed <= 0) {
            return List.of();
        }
        s.counters.merge(key, committed, TaskProgress::saturatedAdd);
        return readyFor(islandId, key);
    }

    static long saturatedAdd(long a, long b) {
        long r = a + b;
        return ((a ^ r) & (b ^ r)) < 0 ? Long.MAX_VALUE : r;
    }
}
