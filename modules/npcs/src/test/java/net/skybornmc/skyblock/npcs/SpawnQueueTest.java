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
    void neighbourGridFindsTheNearestInRange() {
        NearestGrid<String> grid = new NearestGrid<>(6);
        assertTrue(grid.isEmpty());
        assertEquals(null, grid.nearest(0, 0, 0, 6));
        grid.add("far", 100, 64, 100);
        grid.add("near", 3, 65, 0);
        grid.add("edge", 0, 64, -6);
        grid.add("other-cell", -5.9, 64, 0.5);
        assertEquals("near", grid.nearest(0, 64, 0, 6), "3.2 blocks beats 5.9 and 6");
        assertEquals("edge", grid.nearest(0, 64, -4, 2), "exactly on the radius counts (old d <= r²)");
        assertEquals(null, grid.nearest(50, 64, 50, 6));
        assertEquals("far", grid.nearest(95, 64, 97, 6), "across a cell border");
        assertEquals(null, grid.nearest(0, 80, 0, 6), "3D distance, not only horizontal");
    }
}
