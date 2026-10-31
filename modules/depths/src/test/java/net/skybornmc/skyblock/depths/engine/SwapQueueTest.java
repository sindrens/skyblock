package net.skybornmc.skyblock.depths.engine;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SwapQueueTest {

    @Test
    void bedrockSwapIsSentInSlicesOf300() {
        // A full coal field (1,267 slots): 300 at once, then 300, 300, 300, 67 on the next ticks.
        assertArrayEquals(new int[]{300, 300, 300, 300, 67}, SwapQueue.batches(1267, 300));
        assertArrayEquals(new int[]{441}, SwapQueue.batches(441, Integer.MAX_VALUE)); // Java: one multi-block change
        assertArrayEquals(new int[0], SwapQueue.batches(0, 300));
        assertArrayEquals(new int[]{1, 1}, SwapQueue.batches(2, 0)); // never zero per tick
    }

    @Test
    void drainsInOrderAfterTheFirstBatch() {
        long[] keys = new long[1267];
        for (int i = 0; i < keys.length; i++) {
            keys[i] = i;
        }
        SwapQueue queue = new SwapQueue(keys, 300, 0L);
        assertEquals(967, queue.remaining());
        LongArrayList sent = new LongArrayList();
        int[] batches = SwapQueue.batches(1267, 300);
        for (int i = 1; i < batches.length; i++) {
            assertFalse(queue.done());
            assertEquals(batches[i], queue.drain(300, sent::add));
        }
        assertTrue(queue.done());
        assertEquals(4, queue.ticks());
        assertEquals(967, sent.size());
        assertEquals(300L, sent.getLong(0)); // continues after the first batch, nearest-first order kept
        assertEquals(1266L, sent.getLong(966));
        assertEquals(0, queue.drain(300, sent::add));
    }

    @Test
    void alreadySentIsClamped() {
        SwapQueue queue = new SwapQueue(new long[]{1, 2}, 5, 0L);
        assertTrue(queue.done());
        assertEquals(2, queue.total());
        assertEquals(2, new SwapQueue(new long[]{1, 2}, -1, 0L).remaining());
    }
}
