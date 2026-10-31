package net.skybornmc.skyblock.progression;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** PERF7: level applies are queued and drained within the tick budget; the load warm-up is side-effect free. */
class LevelApplyQueueTest {

    @Test
    void alwaysRunsTheFirstThenStopsAtTheBudget() {
        Queue<Runnable> q = new ConcurrentLinkedQueue<>();
        AtomicLong clock = new AtomicLong();
        List<Integer> ran = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            int n = i;
            q.add(() -> {
                ran.add(n);
                clock.addAndGet(3_000_000L); // each apply takes 3 ms
            });
        }
        // 2 ms budget: the first always runs, it already used the budget, so nothing more this tick
        assertEquals(1, LevelManager.drain(q, clock::get, 2_000_000L, e -> { }));
        assertEquals(List.of(0), ran);
        // 7 ms budget: after one 3 ms, after two 6 ms (< 7, a third starts), after three 9 ms: stop
        assertEquals(3, LevelManager.drain(q, clock::get, 7_000_000L, e -> { }));
        assertEquals(List.of(0, 1, 2, 3), ran);
        assertEquals(1, q.size());
    }

    @Test
    void emptyQueueRunsNothingAndCheapWorkDrainsAll() {
        Queue<Runnable> q = new ConcurrentLinkedQueue<>();
        assertEquals(0, LevelManager.drain(q, () -> 0L, 1L, e -> { }));
        List<Integer> ran = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            int n = i;
            q.add(() -> ran.add(n));
        }
        assertEquals(10, LevelManager.drain(q, () -> 0L, 1L, e -> { }));
        assertEquals(10, ran.size());
    }

    @Test
    void aFailingApplyIsReportedAndTheNextStillRuns() {
        Queue<Runnable> q = new ConcurrentLinkedQueue<>();
        List<String> errors = new ArrayList<>();
        List<Integer> ran = new ArrayList<>();
        q.add(() -> {
            throw new IllegalStateException("boom");
        });
        q.add(() -> ran.add(2));
        assertEquals(2, LevelManager.drain(q, () -> 0L, 1_000L, e -> errors.add(e.getMessage())));
        assertEquals(List.of("boom"), errors);
        assertEquals(List.of(2), ran);
    }

    @Test
    void warmUpNeverThrows() {
        assertDoesNotThrow(() -> LevelManager.warmUp(LevelMath.Weights.of(new LevelConfig()), ZoneId.of("Europe/Oslo")));
    }
}
