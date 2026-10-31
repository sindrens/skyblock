package net.skybornmc.skyblock.menus;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Short-lived cache of the player leaderboards on the {@code /menu → Top} screen (richest players, top skills; GUI-4c).
 * Those boards read the database ({@code EconomyService#top}, {@code SkillService#top}), so every screen opening and tab
 * click within {@code ttlMillis} reuses the same load instead of querying again (300 players opening /menu at once cost
 * one query per board). A failed load is forgotten at once, so the next opening tries again.
 *
 * @param <V> the loaded value
 */
final class TopCache<V> {

    private record Entry<V>(CompletableFuture<V> future, long loadedAt) {
    }

    private final Map<String, Entry<V>> entries = new ConcurrentHashMap<>();
    private final LongSupplier clock;

    TopCache(LongSupplier clock) {
        this.clock = clock;
    }

    /** The cached value of {@code key} while younger than {@code ttlMillis} (also while still loading), else a new load. */
    CompletableFuture<V> get(String key, long ttlMillis, Supplier<CompletableFuture<V>> loader) {
        long now = clock.getAsLong();
        Entry<V> entry = entries.compute(key, (k, old) -> {
            if (old != null && !old.future().isCompletedExceptionally() && now - old.loadedAt() < ttlMillis) {
                return old;
            }
            CompletableFuture<V> future;
            try {
                future = loader.get();
            } catch (RuntimeException e) {
                future = CompletableFuture.failedFuture(e);
            }
            return new Entry<>(future == null ? CompletableFuture.failedFuture(new IllegalStateException("no loader result")) : future, now);
        });
        CompletableFuture<V> future = entry.future();
        future.whenComplete((value, error) -> {
            if (error != null) {
                entries.remove(key, entry);
            }
        });
        return future;
    }

    /** Forgets everything (module reload). */
    void clear() {
        entries.clear();
    }
}
