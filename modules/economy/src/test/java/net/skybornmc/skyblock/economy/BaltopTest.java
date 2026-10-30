package net.skybornmc.skyblock.economy;

import net.skybornmc.skyblock.common.economy.Currency;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** /baltop cache: short max age, one query in flight per currency, the viewer's own line made live. */
class BaltopTest {

    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();
    private final UUID me = UUID.randomUUID();

    private final AtomicLong clock = new AtomicLong(1_000_000);
    private final List<CompletableFuture<List<EconomyStore.TopEntry>>> queries = new ArrayList<>();
    private final Baltop baltop = new Baltop((currency, size) -> {
        CompletableFuture<List<EconomyStore.TopEntry>> f = new CompletableFuture<>();
        queries.add(f);
        return f;
    }, clock::get);

    private List<EconomyStore.TopEntry> list() {
        return List.of(new EconomyStore.TopEntry(a, "Alex", 500), new EconomyStore.TopEntry(b, "Bea", 300),
                new EconomyStore.TopEntry(me, "Me", 100));
    }

    @Test
    void onlyOneQueryInFlightPerCurrency() {
        assertNotNull(baltop.refreshIfOlder(Currency.MONEY, 30_000, 10));
        assertNull(baltop.refreshIfOlder(Currency.MONEY, 30_000, 10), "spamming /baltop must not stack queries");
        assertTrue(baltop.refreshing(Currency.MONEY));
        assertEquals(1, queries.size());
        queries.getFirst().complete(list());
        assertFalse(baltop.refreshing(Currency.MONEY));
        assertEquals(3, baltop.get(Currency.MONEY).entries().size());
    }

    /** SMALL2: the background check now runs on the async scheduler while /baltop runs on the main thread. */
    @Test
    void refreshFromManyThreadsAtOnceStillStartsOneQuery() throws Exception {
        java.util.concurrent.atomic.AtomicInteger started = new java.util.concurrent.atomic.AtomicInteger();
        CompletableFuture<List<EconomyStore.TopEntry>> pending = new CompletableFuture<>();
        Baltop shared = new Baltop((currency, size) -> {
            started.incrementAndGet();
            return pending;
        }, clock::get);
        java.util.concurrent.CountDownLatch go = new java.util.concurrent.CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            Thread t = new Thread(() -> {
                try {
                    go.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                shared.refreshIfOlder(Currency.MONEY, 30_000, 10);
            });
            t.start();
            threads.add(t);
        }
        go.countDown();
        for (Thread t : threads) {
            t.join(5_000);
        }
        assertEquals(1, started.get());
        pending.complete(list());
        assertFalse(shared.refreshing(Currency.MONEY));
        assertEquals(3, shared.get(Currency.MONEY).entries().size());
    }

    @Test
    void refreshesOnlyWhenOlderThanTheInterval() {
        baltop.refreshIfOlder(Currency.MONEY, 30_000, 10);
        queries.getFirst().complete(list());
        clock.addAndGet(29_000);
        assertNull(baltop.refreshIfOlder(Currency.MONEY, 30_000, 10), "29 s old: still fresh");
        clock.addAndGet(1_000);
        assertNotNull(baltop.refreshIfOlder(Currency.MONEY, 30_000, 10), "30 s old: read again");
    }

    @Test
    void failedQueryKeepsTheOldListAndAllowsARetry() {
        baltop.refreshIfOlder(Currency.MONEY, 0, 10);
        queries.getFirst().complete(list());
        baltop.refreshIfOlder(Currency.MONEY, 0, 10);
        queries.get(1).completeExceptionally(new RuntimeException("db down"));
        assertEquals(3, baltop.get(Currency.MONEY).entries().size());
        assertFalse(baltop.refreshing(Currency.MONEY));
        assertNotNull(baltop.refreshIfOlder(Currency.MONEY, 0, 10));
    }

    @Test
    void viewerLineUsesTheLiveBalance() {
        Baltop.Snapshot cached = new Baltop.Snapshot(list(), 0);
        Baltop.Snapshot live = cached.withLive(me, "Me", 400, 10);
        assertEquals(2, live.rank(me), "earned 300 since the last read: now above Bea");
        assertEquals(400, live.entries().get(1).balance());
        assertEquals(3, live.entries().size());
    }

    @Test
    void viewerEntersOrLeavesTheTop() {
        Baltop.Snapshot top2 = new Baltop.Snapshot(List.of(new EconomyStore.TopEntry(a, "Alex", 500),
                new EconomyStore.TopEntry(b, "Bea", 300)), 0);
        Baltop.Snapshot entered = top2.withLive(me, "Me", 1_000, 2);
        assertEquals(1, entered.rank(me));
        assertEquals(2, entered.entries().size(), "still only the top 2");
        assertEquals(0, entered.rank(b));

        Baltop.Snapshot cached = new Baltop.Snapshot(list(), 0);
        assertEquals(0, cached.withLive(me, "Me", 0, 10).rank(me), "spent everything: not listed");
        assertEquals(0, cached.withLive(me, "Me", 50, 2).rank(me), "below the top 2");
    }

    @Test
    void topThreeGetTheirOwnLineKeys() {
        assertEquals("baltop.line-top1", EconomyModule.baltopKey(1, false));
        assertEquals("baltop.line-top3", EconomyModule.baltopKey(3, false));
        assertEquals("baltop.line", EconomyModule.baltopKey(4, false));
        assertEquals("baltop.line-you", EconomyModule.baltopKey(1, true));
    }
}
