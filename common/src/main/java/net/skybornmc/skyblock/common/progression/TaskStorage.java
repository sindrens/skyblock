package net.skybornmc.skyblock.common.progression;

import net.skybornmc.skyblock.common.audit.Actor;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Hook for the island storage (STORE2): lets deliver tasks hand in items from the island's storage. Registered with
 * {@link TaskService#storage}; until then the challenge book shows "From island storage" as coming soon.
 *
 * <p>Atomic rule (PHASE-5 M5.3): the storage rows and the task progress change in <b>one</b> database transaction. The
 * storage removes up to {@code max} items and calls {@link Progress#apply} with the amount it took, on the same
 * connection, before committing; if that throws, the whole transaction (items included) rolls back.</p>
 */
public interface TaskStorage {

    /** Saves the progress of the items taken, inside the storage's transaction. Must only use the given connection. */
    @FunctionalInterface
    interface Progress {
        void apply(Connection connection, long taken) throws SQLException;
    }

    /**
     * Matching items in the island's storage (from a cache, main thread). {@code itemKey} is a material id
     * ({@code coal}) or {@code custom:<id>}; only plain items match a material id (no custom items, names or enchants).
     */
    long count(UUID islandId, String itemKey);

    /**
     * Takes up to {@code max} matching items and saves {@code progress} in the same transaction. Completes with the number
     * of items taken (0 when there were none, then {@code progress} is not called); completes exceptionally when nothing
     * was changed.
     */
    CompletableFuture<Long> take(UUID islandId, String itemKey, long max, Actor actor, Progress progress);
}
