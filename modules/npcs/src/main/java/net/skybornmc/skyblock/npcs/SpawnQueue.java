package net.skybornmc.skyblock.npcs;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/**
 * PERF3: sets waiting to be spawned, handled a few per tick instead of all at once (the start-up spawn of every NPC,
 * hologram and leaderboard in the loaded chunks took over a second in one tick on update101). An item is queued at most
 * once at a time. PERF9: a whole placement is handed over as one batch ({@link #addAll}) in O(1); its items are taken
 * from it lazily while draining (after the items queued one by one), so queueing every point costs nothing in the apply
 * tick. Pure, unit-tested. Main thread.
 *
 * @param <T> the queued item
 */
final class SpawnQueue<T> {

    private final ArrayDeque<T> queue = new ArrayDeque<>();
    private final Set<T> queued = Collections.newSetFromMap(new IdentityHashMap<>());
    /** PERF9: whole lists handed over at once, read in order from {@link #batchPos}. */
    private final ArrayDeque<List<T>> batches = new ArrayDeque<>();
    private int batchPos;
    private int batchRemaining;

    /** Queues an item; false when it is already waiting. */
    boolean add(T item) {
        if (!queued.add(item)) {
            return false;
        }
        queue.add(item);
        return true;
    }

    /**
     * PERF9: queues a whole list in O(1) (it is read lazily, so it must not change afterwards: pass an immutable list).
     * An item of a batch is not deduplicated against single adds; the handler must skip items that need no work (the
     * spawn handler skips sets that are already spawned, the removal handler sets without entities).
     */
    void addAll(List<T> batch) {
        if (!batch.isEmpty()) {
            batches.add(batch);
            batchRemaining += batch.size();
        }
    }

    boolean isEmpty() {
        return queue.isEmpty() && batchRemaining == 0;
    }

    int size() {
        return queue.size() + batchRemaining;
    }

    void clear() {
        queue.clear();
        queued.clear();
        batches.clear();
        batchPos = 0;
        batchRemaining = 0;
    }

    /** The next item: single adds first, then the batches in order; null when empty. */
    private T poll() {
        T item = queue.poll();
        if (item != null) {
            queued.remove(item);
            return item;
        }
        while (!batches.isEmpty()) {
            List<T> batch = batches.peekFirst();
            if (batchPos >= batch.size()) {
                batches.pollFirst();
                batchPos = 0;
                continue;
            }
            T next = batch.get(batchPos++);
            batchRemaining--;
            if (batchPos >= batch.size()) {
                batches.pollFirst();
                batchPos = 0;
            }
            return next;
        }
        return null;
    }

    /**
     * Takes items in queue order and hands them to {@code handle}, which returns true when it did real work (spawned)
     * and false when the item was skipped (no longer wanted, chunk gone). Stops after {@code max} items with work or
     * once {@code budgetNanos} have passed; at least one item with work is always handled, so the queue always moves.
     * PERF4: it also stops when the last spawn of this tick says the next one would end past the budget
     * ({@code elapsed + last cost > budget}), so a tick overshoots by at most one spawn's cost over the budget.
     * PERF9: skipped items don't count as work, but a tick also stops once the budget has passed while skipping (a whole
     * placement is queued, most of it in unloaded chunks); every skipped item leaves the queue, so it always moves.
     *
     * @return the number of items with work
     */
    int drain(int max, long budgetNanos, LongSupplier clock, Predicate<T> handle) {
        long start = clock.getAsLong();
        long before = start;
        int done = 0;
        T item;
        while ((item = poll()) != null) {
            if (!handle.test(item)) {
                before = clock.getAsLong();
                if (before - start >= budgetNanos) {
                    break;
                }
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
