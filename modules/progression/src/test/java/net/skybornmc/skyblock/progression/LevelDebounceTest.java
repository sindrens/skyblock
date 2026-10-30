package net.skybornmc.skyblock.progression;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Debounced level recalculation (PHASE-5 M5.2). */
class LevelDebounceTest {

    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();

    @Test
    void waitsForMoreChanges() {
        LevelDebounce d = new LevelDebounce();
        d.request(a, 0, 5_000, 30_000);
        assertEquals(List.of(), d.drainDue(4_999, 10));
        d.request(a, 4_000, 5_000, 30_000); // pushed back
        assertEquals(List.of(), d.drainDue(8_999, 10));
        assertEquals(List.of(a), d.drainDue(9_000, 10));
        assertFalse(d.isPending(a));
    }

    @Test
    void constantChangesStillRecalculateAfterMaxWait() {
        LevelDebounce d = new LevelDebounce();
        for (long t = 0; t < 40_000; t += 1_000) {
            d.request(a, t, 5_000, 30_000);
            if (t == 29_000) {
                break;
            }
        }
        assertEquals(List.of(a), d.drainDue(30_000, 10), "never later than max-wait after the first change");
    }

    @Test
    void manyChangesOneRecalculation() {
        LevelDebounce d = new LevelDebounce();
        for (int i = 0; i < 100; i++) {
            d.request(a, i, 5_000, 30_000);
        }
        assertEquals(1, d.size());
        assertEquals(List.of(a), d.drainDue(100_000, 10));
        assertEquals(List.of(), d.drainDue(100_000, 10));
    }

    @Test
    void limitPerDrainAndOldestFirst() {
        LevelDebounce d = new LevelDebounce();
        d.request(a, 0, 0, 0);
        d.request(b, 1, 0, 0);
        assertEquals(List.of(a), d.drainDue(10, 1));
        assertTrue(d.isPending(b));
        d.forget(b);
        assertEquals(List.of(), d.drainDue(10, 5));
    }
}
