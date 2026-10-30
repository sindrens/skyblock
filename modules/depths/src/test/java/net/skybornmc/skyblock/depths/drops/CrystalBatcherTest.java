package net.skybornmc.skyblock.depths.drops;

import net.skybornmc.skyblock.common.economy.TxResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrystalBatcherTest {

    private final AtomicInteger keyCounter = new AtomicInteger();
    private final CrystalBatcher batcher = new CrystalBatcher(Runnable::run, () -> "depths_drop:" + keyCounter.incrementAndGet(), 3);
    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();

    private record Call(UUID profile, long amount, String key) {
    }

    private final List<Call> calls = new ArrayList<>();
    private final List<String> settled = new ArrayList<>();

    private CrystalBatcher.Payer answering(TxResult.Status status) {
        return (p, amount, key) -> {
            calls.add(new Call(p, amount, key));
            return CompletableFuture.completedFuture(TxResult.of(status));
        };
    }

    private void flush(CrystalBatcher.Payer payer) {
        batcher.flush(payer, (p, amount, outcome, status) -> settled.add(p.equals(a) ? "a" : "b") );
    }

    @Test
    void manyBlocksOnePaymentPerProfile() {
        for (int i = 0; i < 50; i++) {
            batcher.add(a, 1);
        }
        batcher.add(b, 3);
        batcher.add(b, 0);
        flush(answering(TxResult.Status.SUCCESS));
        assertEquals(2, calls.size());
        Map<UUID, Long> paid = new HashMap<>();
        calls.forEach(c -> paid.put(c.profile(), c.amount()));
        assertEquals(Map.of(a, 50L, b, 3L), paid);
        assertTrue(batcher.idle());
        flush(answering(TxResult.Status.SUCCESS));
        assertEquals(2, calls.size(), "nothing open, nothing paid");
    }

    @Test
    void errorsRetryWithTheSameKeyAndNewCrystalsWait() {
        batcher.add(a, 10);
        flush(answering(TxResult.Status.ERROR));
        batcher.add(a, 5);
        assertEquals(15, batcher.pending(a));
        flush(answering(TxResult.Status.SUCCESS));
        assertEquals(2, calls.size(), "only the retry: one batch per profile at a time");
        assertEquals(calls.get(0).key(), calls.get(1).key());
        assertEquals(10, calls.get(1).amount());
        flush(answering(TxResult.Status.SUCCESS));
        assertEquals(3, calls.size());
        assertEquals(5, calls.get(2).amount());
        assertTrue(!calls.get(2).key().equals(calls.get(0).key()));
        assertTrue(batcher.idle());
    }

    @Test
    void duplicateMeansAnEarlierAttemptWentThrough() {
        batcher.add(a, 7);
        flush(answering(TxResult.Status.ERROR));
        List<CrystalBatcher.Outcome> outcomes = new ArrayList<>();
        batcher.flush(answering(TxResult.Status.DUPLICATE), (p, amount, o, s) -> outcomes.add(o));
        assertEquals(List.of(CrystalBatcher.Outcome.ALREADY_PAID), outcomes);
        assertTrue(batcher.idle());
    }

    @Test
    void droppedAfterTooManyErrorsOrInvalid() {
        batcher.add(a, 4);
        List<CrystalBatcher.Outcome> outcomes = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            batcher.flush(answering(TxResult.Status.ERROR), (p, amount, o, s) -> outcomes.add(o));
        }
        assertEquals(List.of(CrystalBatcher.Outcome.DROPPED), outcomes);
        assertTrue(batcher.idle());
        batcher.add(b, 2);
        batcher.flush(answering(TxResult.Status.OVERFLOW), (p, amount, o, s) -> outcomes.add(o));
        assertEquals(CrystalBatcher.Outcome.DROPPED, outcomes.get(1));
    }

    @Test
    void inFlightBatchIsNotSentTwice() {
        CompletableFuture<TxResult> slow = new CompletableFuture<>();
        batcher.add(a, 9);
        batcher.flush((p, amount, key) -> {
            calls.add(new Call(p, amount, key));
            return slow;
        }, (p, amount, o, s) -> settled.add("a"));
        batcher.add(a, 1);
        flush(answering(TxResult.Status.SUCCESS));
        assertEquals(1, calls.size(), "still waiting for the first answer");
        slow.complete(TxResult.of(TxResult.Status.SUCCESS));
        assertEquals(List.of("a"), settled);
        flush(answering(TxResult.Status.SUCCESS));
        assertEquals(2, calls.size());
        assertEquals(1, calls.get(1).amount());
    }

    @Test
    void payerExceptionsCountAsErrors() {
        batcher.add(a, 3);
        batcher.flush((p, amount, key) -> {
            throw new IllegalStateException("db down");
        }, (p, amount, o, s) -> settled.add("x"));
        assertEquals(3, batcher.pending(a));
        flush(answering(TxResult.Status.SUCCESS));
        assertEquals(1, calls.size());
        assertTrue(batcher.idle());
    }
}
