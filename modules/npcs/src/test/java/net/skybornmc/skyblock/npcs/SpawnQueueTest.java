package net.skybornmc.skyblock.npcs;

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
}
