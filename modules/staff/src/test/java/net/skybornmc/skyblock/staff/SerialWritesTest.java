package net.skybornmc.skyblock.staff;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SerialWritesTest {

    @Test
    void sameKeyRunsInSubmitOrderEvenIfTheFirstIsSlow() {
        SerialWrites<UUID> writes = new SerialWrites<>();
        UUID player = UUID.randomUUID();
        List<String> log = new ArrayList<>();
        CompletableFuture<Void> slowFreeze = new CompletableFuture<>();
        writes.submit(player, () -> {
            log.add("freeze-start");
            return slowFreeze.thenRun(() -> log.add("freeze-done"));
        });
        CompletableFuture<Void> unfreeze = writes.submit(player, () -> {
            log.add("unfreeze");
            return CompletableFuture.completedFuture(null);
        });
        assertEquals(List.of("freeze-start"), log, "unfreeze must wait for the freeze write");
        slowFreeze.complete(null);
        unfreeze.join();
        assertEquals(List.of("freeze-start", "freeze-done", "unfreeze"), log);
        assertEquals(0, writes.pending());
    }

    @Test
    void failureDoesNotBlockTheNextWrite() {
        SerialWrites<UUID> writes = new SerialWrites<>();
        UUID player = UUID.randomUUID();
        CompletableFuture<Void> first = writes.submit(player, () -> CompletableFuture.failedFuture(new IllegalStateException("db down")));
        CompletableFuture<String> second = writes.submit(player, () -> CompletableFuture.completedFuture("ok"));
        assertTrue(first.isCompletedExceptionally());
        assertEquals("ok", second.join());
        CompletableFuture<Void> thrown = writes.submit(player, () -> {
            throw new IllegalStateException("boom");
        });
        assertTrue(thrown.isCompletedExceptionally());
        assertEquals("ok", writes.submit(player, () -> CompletableFuture.completedFuture("ok")).join());
    }

    @Test
    void differentKeysDoNotWait() {
        SerialWrites<UUID> writes = new SerialWrites<>();
        CompletableFuture<Void> blocked = new CompletableFuture<>();
        writes.submit(UUID.randomUUID(), () -> blocked);
        CompletableFuture<String> other = writes.submit(UUID.randomUUID(), () -> CompletableFuture.completedFuture("free"));
        assertEquals("free", other.getNow(null));
        blocked.complete(null);
    }
}
