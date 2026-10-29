package net.skybornmc.skyblock.staff;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Runs async writes for the same key one after another, in submit order (a quick freeze → unfreeze must never reach
 * the database as unfreeze → freeze). A failed write does not stop the next one. Keys with nothing pending are dropped.
 *
 * @param <K> key type (player UUID)
 */
final class SerialWrites<K> {

    private final Map<K, CompletableFuture<?>> tails = new ConcurrentHashMap<>();

    /** Starts {@code write} once every earlier write for {@code key} has finished (successfully or not). */
    <T> CompletableFuture<T> submit(K key, Supplier<CompletableFuture<T>> write) {
        CompletableFuture<T> result = new CompletableFuture<>();
        tails.compute(key, (k, previous) -> {
            CompletableFuture<?> before = previous == null ? CompletableFuture.completedFuture(null) : previous;
            before.handle((ignored, error) -> null).thenCompose(ignored -> {
                try {
                    return write.get();
                } catch (RuntimeException e) {
                    return CompletableFuture.<T>failedFuture(e);
                }
            }).whenComplete((value, error) -> {
                if (error != null) {
                    result.completeExceptionally(error);
                } else {
                    result.complete(value);
                }
            });
            return result;
        });
        result.whenComplete((value, error) -> tails.remove(key, result));
        return result;
    }

    /** Keys with a write still pending (for tests). */
    int pending() {
        return tails.size();
    }
}
