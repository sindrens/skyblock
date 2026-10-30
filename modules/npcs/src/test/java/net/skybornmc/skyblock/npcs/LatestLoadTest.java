package net.skybornmc.skyblock.npcs;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LatestLoadTest {

    /** Runs queued tasks only when asked, like a thread pool or the next server tick. */
    static final class Manual implements Executor {
        final Queue<Runnable> tasks = new ArrayDeque<>();
        final String name;
        final AtomicReference<String> current;

        Manual(String name, AtomicReference<String> current) {
            this.name = name;
            this.current = current;
        }

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }

        void runAll() {
            Runnable r;
            while ((r = tasks.poll()) != null) {
                current.set(name);
                try {
                    r.run();
                } finally {
                    current.set(null);
                }
            }
        }
    }

    @Test
    void computesInTheBackgroundAndAppliesOnMain() {
        AtomicReference<String> thread = new AtomicReference<>();
        Manual background = new Manual("background", thread);
        Manual main = new Manual("main", thread);
        LatestLoad<String> load = new LatestLoad<>(background, main);
        List<String> log = new ArrayList<>();
        CompletableFuture<Boolean> done = load.load(() -> {
            log.add("compute@" + thread.get());
            return "plan";
        }, v -> log.add("apply " + v + "@" + thread.get()));
        assertTrue(log.isEmpty(), "nothing runs on the calling thread");
        background.runAll();
        assertEquals(List.of("compute@background"), log);
        assertFalse(done.isDone(), "not applied until the main thread runs");
        main.runAll();
        assertEquals(List.of("compute@background", "apply plan@main"), log);
        assertTrue(done.join());
    }

    @Test
    void anOlderResultIsDroppedWhenANewerLoadStarted() {
        AtomicReference<String> thread = new AtomicReference<>();
        Manual background = new Manual("background", thread);
        Manual main = new Manual("main", thread);
        LatestLoad<String> load = new LatestLoad<>(background, main);
        List<String> applied = new ArrayList<>();
        CompletableFuture<Boolean> first = load.load(() -> "old", applied::add);
        CompletableFuture<Boolean> second = load.load(() -> "new", applied::add);
        background.runAll();
        main.runAll();
        assertEquals(List.of("new"), applied);
        assertFalse(first.join());
        assertTrue(second.join());
    }

    @Test
    void cancelDropsLoadsInFlight() {
        AtomicReference<String> thread = new AtomicReference<>();
        Manual background = new Manual("background", thread);
        Manual main = new Manual("main", thread);
        LatestLoad<String> load = new LatestLoad<>(background, main);
        AtomicReference<String> applied = new AtomicReference<>();
        CompletableFuture<Boolean> done = load.load(() -> "x", applied::set);
        background.runAll();
        load.cancel(); // module disabled before the main thread got to it
        main.runAll();
        assertNull(applied.get());
        assertFalse(done.join());
    }

    @Test
    void failuresCompleteExceptionallyAndApplyNothing() {
        LatestLoad<String> load = new LatestLoad<>(Runnable::run, Runnable::run);
        AtomicReference<String> applied = new AtomicReference<>();
        CompletableFuture<Boolean> done = load.load(() -> {
            throw new IllegalStateException("broken");
        }, applied::set);
        assertTrue(done.isCompletedExceptionally());
        assertNull(applied.get());

        CompletableFuture<Boolean> applyFails = load.load(() -> "x", v -> {
            throw new IllegalStateException("apply");
        });
        assertTrue(applyFails.isCompletedExceptionally());

        LatestLoad<String> disabled = new LatestLoad<>(Runnable::run, r -> {
            throw new IllegalStateException("plugin disabled");
        });
        assertTrue(disabled.load(() -> "x", applied::set).isCompletedExceptionally());
        assertNull(applied.get());
    }
}
