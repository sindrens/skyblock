package net.skybornmc.skyblock.common.hook;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Turns LuckPerms' burst of {@code UserDataRecalculateEvent}s / {@code NodeMutateEvent}s into few clean notifications
 * per player.
 *
 * <p>Why: {@code lp user X parent set mod} is several node changes (remove every parent, add the new one) and each
 * fires its own event, asynchronously, while the command is still running (and LuckPerms saves to the database in
 * between). Reacting to the first event can read the half-done state (no parent = "default"). So every burst gives one
 * notification on the next tick (fast path) and one "settle" notification {@value #SETTLE_TICKS} ticks after the
 * <b>last</b> event of the burst (FIXES1: the settle used to be timed from the first event, so a slow command whose last
 * step came after it was only seen by the fast path).</p>
 *
 * <p>Thread-safe: {@link #changed} is called from LuckPerms' event threads; the scheduler runs tasks on the main
 * thread.</p>
 */
final class RankChangeDebouncer {

    /** Runs {@code task} on the main thread after {@code delayTicks} (0 = next tick); {@link #now} = current tick. */
    interface TickScheduler {
        void later(long delayTicks, Runnable task);

        long now();
    }

    /** Ticks after the last change for the second ("settle") notification. 10 ticks = 0.5 s. */
    static final long SETTLE_TICKS = 10L;

    private final TickScheduler scheduler;
    private final Consumer<UUID> notify;
    private final Set<UUID> fast = ConcurrentHashMap.newKeySet();
    private final Set<UUID> settling = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> lastChange = new ConcurrentHashMap<>();

    RankChangeDebouncer(TickScheduler scheduler, Consumer<UUID> notify) {
        this.scheduler = scheduler;
        this.notify = notify;
    }

    /** A player's rank data was recalculated (any thread). */
    void changed(UUID player) {
        lastChange.put(player, scheduler.now());
        if (fast.add(player)) {
            scheduler.later(0L, () -> {
                fast.remove(player);
                notify.accept(player);
            });
        }
        if (settling.add(player)) {
            scheduleSettle(player, SETTLE_TICKS);
        }
    }

    /** Waits until {@link #SETTLE_TICKS} passed since the last change, then notifies once. */
    private void scheduleSettle(UUID player, long delay) {
        scheduler.later(delay, () -> {
            Long last = lastChange.get(player);
            long since = last == null ? SETTLE_TICKS : scheduler.now() - last;
            if (since < SETTLE_TICKS) {
                scheduleSettle(player, SETTLE_TICKS - since); // a later change came in: settle after that one
                return;
            }
            settling.remove(player);
            if (last != null) {
                lastChange.remove(player, last); // keeps a newer change (it scheduled its own settle)
            }
            notify.accept(player);
        });
    }
}
