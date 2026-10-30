package net.skybornmc.skyblock.npcs;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * PERF3: computes a value off the main thread and applies it on the main thread. Only the newest load is applied: a
 * load started later (or {@link #cancel()}, on disable) makes the older result stale, and a stale result is dropped
 * instead of applied. Pure (both executors are injected), unit-tested.
 *
 * @param <T> the computed value
 */
final class LatestLoad<T> {

    private final Executor background;
    private final Executor main;
    private final AtomicLong generation = new AtomicLong();

    LatestLoad(Executor background, Executor main) {
        this.background = background;
        this.main = main;
    }

    /**
     * Starts one load.
     *
     * @return completes on the main executor with {@code true} once applied, {@code false} when the result was stale,
     *         or exceptionally when computing or applying failed
     */
    CompletableFuture<Boolean> load(Supplier<T> compute, Consumer<T> apply) {
        long gen = generation.incrementAndGet();
        CompletableFuture<Boolean> done = new CompletableFuture<>();
        try {
            background.execute(() -> {
                T value;
                try {
                    value = compute.get();
                } catch (RuntimeException e) {
                    done.completeExceptionally(e);
                    return;
                }
                try {
                    main.execute(() -> {
                        if (generation.get() != gen) {
                            done.complete(false);
                            return;
                        }
                        try {
                            apply.accept(value);
                            done.complete(true);
                        } catch (RuntimeException e) {
                            done.completeExceptionally(e);
                        }
                    });
                } catch (RuntimeException e) {
                    done.completeExceptionally(e); // plugin disabled in between: nothing to apply to
                }
            });
        } catch (RuntimeException e) {
            done.completeExceptionally(e);
        }
        return done;
    }

    /** Makes every load still in flight stale. */
    void cancel() {
        generation.incrementAndGet();
    }
}
