package net.skybornmc.skyblock.npcs;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/**
 * PERF3: sets waiting to be spawned, handled a few per tick instead of all at once (the start-up spawn of every NPC,
 * hologram and leaderboard in the loaded chunks took over a second in one tick on update101). An item is queued at most
 * once at a time. Pure, unit-tested. Main thread.
 *
 * @param <T> the queued item
 */
final class SpawnQueue<T> {

    private final ArrayDeque<T> queue = new ArrayDeque<>();
    private final Set<T> queued = Collections.newSetFromMap(new IdentityHashMap<>());

    /** Queues an item; false when it is already waiting. */
    boolean add(T item) {
        if (!queued.add(item)) {
            return false;
        }
        queue.add(item);
        return true;
    }

    boolean isEmpty() {
        return queue.isEmpty();
    }

    int size() {
        return queue.size();
    }

    void clear() {
        queue.clear();
        queued.clear();
    }

    /**
     * Takes items in queue order and hands them to {@code handle}, which returns true when it did real work (spawned)
     * and false when the item was skipped (no longer wanted, chunk gone). Stops after {@code max} items with work or
     * once {@code budgetNanos} have passed; at least one item with work is always handled, so the queue always moves.
     * PERF4: it also stops when the last spawn of this tick says the next one would end past the budget
     * ({@code elapsed + last cost > budget}), so a tick overshoots by at most one spawn's cost over the budget.
     *
     * @return the number of items with work
     */
    int drain(int max, long budgetNanos, LongSupplier clock, Predicate<T> handle) {
        long start = clock.getAsLong();
        long before = start;
        int done = 0;
        while (!queue.isEmpty()) {
            T item = queue.poll();
            queued.remove(item);
            if (!handle.test(item)) {
                before = clock.getAsLong();
                continue;
            }
            done++;
            long now = clock.getAsLong();
            long cost = now - before;
            before = now;
            long elapsed = now - start;
            if (done >= Math.max(1, max) || elapsed >= budgetNanos || elapsed + cost > budgetNanos) {
                break;
            }
        }
        return done;
    }
}
