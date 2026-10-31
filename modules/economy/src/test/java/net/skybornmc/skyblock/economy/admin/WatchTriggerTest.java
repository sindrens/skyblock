package net.skybornmc.skyblock.economy.admin;

import net.skybornmc.skyblock.common.economy.Currency;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PERF11: the off-main-thread trigger of the economy watch. */
class WatchTriggerTest {

    @Test
    void startsOnlyWhenOnWithAJob() {
        assertFalse(WatchTrigger.shouldStart(true, null));
        assertFalse(WatchTrigger.shouldStart(false, null));
    }

    @Test
    void onlyAlertsNeedTheMainThread() {
        assertFalse(WatchTrigger.needsMain(null));
        assertFalse(WatchTrigger.needsMain(new WatchJob.Result(120, 55, List.of(), Map.of())));
        WatchAlert alert = new WatchAlert(1, 0, 0, Rule.BIG_TRANSFER, AlertDraft.Severity.HIGH, Currency.MONEY, WatchTestRows.A,
                null, 1_000, 10, 1, List.of(5L), WatchAlert.State.OPEN, null, null);
        assertTrue(WatchTrigger.needsMain(new WatchJob.Result(1, 5, List.of(alert), Map.of())));
    }

    @Test
    void failureWarningIsRateLimited() {
        WatchTrigger trigger = new WatchTrigger(600_000L);
        long now = 1_800_000_000_000L;
        assertTrue(trigger.warnDue(now), "the first failure warns");
        assertFalse(trigger.warnDue(now + 1));
        assertFalse(trigger.warnDue(now + 600_000L), "exactly the limit is still quiet (same as before)");
        assertTrue(trigger.warnDue(now + 600_001L));
    }

    @Test
    void failureWarningOncePerWindowFromManyThreads() throws Exception {
        WatchTrigger trigger = new WatchTrigger(600_000L);
        long now = 1_800_000_000_000L;
        AtomicInteger warned = new AtomicInteger();
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            for (int i = 0; i < 64; i++) {
                pool.execute(() -> {
                    try {
                        go.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    if (trigger.warnDue(now)) {
                        warned.incrementAndGet();
                    }
                });
            }
            go.countDown();
        } finally {
            pool.shutdown();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }
        assertEquals(1, warned.get());
    }
}
