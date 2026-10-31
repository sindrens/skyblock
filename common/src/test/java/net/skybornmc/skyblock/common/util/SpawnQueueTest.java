package net.skybornmc.skyblock.common.util;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpawnQueueTest {

    private static final long MS = 1_000_000L;

    @Test
    void spreadsWorkOverTicksByCount() {
        SpawnQueue<String> q = new SpawnQueue<>();
        for (int i = 0; i < 20; i++) {
            q.add("s" + i);
        }
        List<String> spawned = new ArrayList<>();
        assertEquals(8, q.drain(8, 1000 * MS, () -> 0L, spawned::add));
        assertEquals(12, q.size());
        assertEquals(8, q.drain(8, 1000 * MS, () -> 0L, spawned::add));
        assertEquals(4, q.drain(8, 1000 * MS, () -> 0L, spawned::add));
        assertTrue(q.isEmpty());
        assertEquals(20, spawned.size());
        assertEquals("s0", spawned.getFirst(), "queue order is kept");
    }

    @Test
    void stopsWhenTheTimeBudgetIsUsedButAlwaysDoesOne() {
        SpawnQueue<String> q = new SpawnQueue<>();
        q.add("a");
        q.add("b");
        q.add("c");
        AtomicLong clock = new AtomicLong();
        // every spawn "takes" 3 ms; budget 2 ms: exactly one per tick
        assertEquals(1, q.drain(8, 2 * MS, clock::get, s -> {
            clock.addAndGet(3 * MS);
            return true;
        }));
        assertEquals(2, q.size());
        // 1 ms each with a 2 ms budget: two per tick
        assertEquals(2, q.drain(8, 2 * MS, clock::get, s -> {
            clock.addAndGet(MS);
            return true;
        }));
        assertTrue(q.isEmpty());
    }

    @Test
    void skippedItemsDoNotUseTheBudget() {
        SpawnQueue<String> q = new SpawnQueue<>();
        q.add("gone1");
        q.add("gone2");
        q.add("x");
        q.add("y");
        List<String> spawned = new ArrayList<>();
        assertEquals(1, q.drain(1, 1000 * MS, () -> 0L, s -> s.startsWith("gone") ? false : spawned.add(s)));
        assertEquals(List.of("x"), spawned);
        assertEquals(1, q.size());
    }

    @Test
    void anItemIsQueuedOnceUntilHandled() {
        SpawnQueue<String> q = new SpawnQueue<>();
        String npc = "npc";
        assertTrue(q.add(npc));
        assertFalse(q.add(npc), "a chunk loading twice must not spawn doubles");
        assertEquals(1, q.size());
        q.drain(8, MS, () -> 0L, s -> false);
        assertTrue(q.add(npc), "after it was handled (here: skipped, chunk gone) it can be queued again");
        q.clear();
        assertTrue(q.isEmpty());
        assertTrue(q.add(npc));
    }

    @Test
    void doesNotStartASpawnThatWouldEndPastTheBudget() {
        SpawnQueue<String> q = new SpawnQueue<>();
        for (int i = 0; i < 5; i++) {
            q.add("s" + i);
        }
        AtomicLong clock = new AtomicLong();
        // 0.8 ms each, 2 ms budget: after two (1.6 ms) a third would end at 2.4 ms, so it waits for the next tick
        assertEquals(2, q.drain(8, 2 * MS, clock::get, s -> {
            clock.addAndGet(800_000L);
            return true;
        }));
        assertEquals(3, q.size());
    }

    @Test
    void aBatchIsReadInOrderAfterSingleAdds() {
        SpawnQueue<String> q = new SpawnQueue<>();
        q.addAll(List.of("a", "b", "c"));
        q.addAll(List.of());
        q.addAll(List.of("d"));
        q.add("chunk-load");
        assertEquals(5, q.size());
        assertFalse(q.isEmpty());
        List<String> seen = new ArrayList<>();
        assertEquals(2, q.drain(2, 1000 * MS, () -> 0L, seen::add));
        assertEquals(List.of("chunk-load", "a"), seen, "single adds (chunk loads, live texts) go first");
        assertEquals(3, q.size());
        q.drain(8, 1000 * MS, () -> 0L, seen::add);
        assertEquals(List.of("chunk-load", "a", "b", "c", "d"), seen);
        assertTrue(q.isEmpty());
        assertEquals(0, q.size());
    }

    @Test
    void clearDropsBatches() {
        SpawnQueue<String> q = new SpawnQueue<>();
        q.addAll(List.of("a", "b"));
        q.drain(1, 1000 * MS, () -> 0L, s -> true);
        q.clear();
        assertTrue(q.isEmpty());
        q.addAll(List.of("x"));
        List<String> seen = new ArrayList<>();
        q.drain(8, 1000 * MS, () -> 0L, seen::add);
        assertEquals(List.of("x"), seen, "a cleared batch is not read on from its old position");
    }

    @Test
    void skippingStopsOnceTheBudgetHasPassed() {
        SpawnQueue<String> q = new SpawnQueue<>();
        List<String> unloaded = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            unloaded.add("far" + i);
        }
        q.addAll(List.copyOf(unloaded));
        AtomicLong clock = new AtomicLong();
        // every loaded-chunk check "takes" 0.1 ms; budget 2 ms: 20 checks per tick, none of them spawns
        assertEquals(0, q.drain(8, 2 * MS, clock::get, s -> {
            clock.addAndGet(100_000L);
            return false;
        }));
        assertEquals(80, q.size(), "every skipped item left the queue, so it still moves");
    }

    @Test
    void aSetInABatchAndQueuedByAChunkLoadSpawnsOnce() {
        SpawnQueue<String> q = new SpawnQueue<>();
        String set = "npc";
        q.addAll(List.of(set));
        q.add(set); // its chunk loaded before the batch got to it
        java.util.Set<String> spawned = new java.util.HashSet<>();
        int work = q.drain(8, 1000 * MS, () -> 0L, spawned::add); // add returns false when already spawned
        assertEquals(1, work);
        assertEquals(java.util.Set.of(set), spawned);
        assertTrue(q.isEmpty());
    }
}
