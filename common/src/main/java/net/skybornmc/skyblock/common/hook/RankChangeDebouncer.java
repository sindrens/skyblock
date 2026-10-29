package net.skybornmc.skyblock.common.hook;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Turns LuckPerms' burst of {@code UserDataRecalculateEvent}s into two clean notifications per player.
 *
 * <p>Why: {@code lp user X parent set mod} is several node changes (remove every parent, add the new one) and each
 * fires its own recalculation event, asynchronously, while the command is still running. Reacting to the first event
 * can read the half-done state (no parent = "default") and nothing would correct it until the next periodic refresh.
 * So every burst gives one notification on the next tick (fast path) and one "settle" notification a few ticks later
 * that always sees the final state. Both are coalesced per player, so 10 events still mean 2 refreshes.</p>
 *
 * <p>Thread-safe: {@link #changed} is called from LuckPerms' event threads; the scheduler runs tasks on the main
 * thread.</p>
 */
final class RankChangeDebouncer {

    /** Runs {@code task} on the main thread after {@code delayTicks} (0 = next tick). */
    @FunctionalInterface
    interface TickScheduler {
        void later(long delayTicks, Runnable task);
    }

    /** Ticks after a change for the second ("settle") notification. 10 ticks = 0.5 s, well inside the 1 s target. */
    static final long SETTLE_TICKS = 10L;

    private final TickScheduler scheduler;
    private final Consumer<UUID> notify;
    private final Set<UUID> fast = ConcurrentHashMap.newKeySet();
    private final Set<UUID> settle = ConcurrentHashMap.newKeySet();

    RankChangeDebouncer(TickScheduler scheduler, Consumer<UUID> notify) {
        this.scheduler = scheduler;
        this.notify = notify;
    }

    /** A player's rank data was recalculated (any thread). */
    void changed(UUID player) {
        if (fast.add(player)) {
            scheduler.later(0L, () -> {
                fast.remove(player);
                notify.accept(player);
            });
        }
        if (settle.add(player)) {
            scheduler.later(SETTLE_TICKS, () -> {
                settle.remove(player);
                notify.accept(player);
            });
        }
    }
}
