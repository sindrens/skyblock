package net.skybornmc.skyblock.boosters;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** PERF10: the off-thread warm-up of the tick's pure code gives the same result every time, on any thread. */
class GlobalBoostersWarmUpTest {

    @Test
    void warmUpRunsThePureTickCodeAndIsStable() {
        // queue: promote 0, tick ends #1 and starts #2 (3), active 1 + queued 0 + percent 10 + all 1, active present 1,
        // time texts "2h 00m" + "1m 30s" + "5s" (14), progress 0.5 (5), save due (1), equal records (1) = 37
        assertEquals(37L, GlobalBoosters.warmUp());
        assertEquals(37L, GlobalBoosters.warmUp(), "no shared state");
    }

    @Test
    void warmUpWorksOffTheMainThread() {
        assertEquals(37L, CompletableFuture.supplyAsync(GlobalBoosters::warmUp).join());
    }
}
