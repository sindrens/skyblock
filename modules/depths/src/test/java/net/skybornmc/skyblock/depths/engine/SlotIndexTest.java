package net.skybornmc.skyblock.depths.engine;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlotIndexTest {

    /** The mine tier-1 ore field of zones.yml: x -140..-101, y 97..99, z -28..11 (3 x 3 chunks). */
    private static SlotIndex field() {
        return new SlotIndex(-140, 97, -28, -101, 99, 11);
    }

    @Test
    void coversOnlyOverlappingChunks() {
        SlotIndex index = field();
        assertTrue(index.covers(-140 >> 4, -28 >> 4));
        assertTrue(index.covers(-101 >> 4, 11 >> 4));
        assertTrue(index.covers(-7, 0));
        assertFalse(index.covers(-6, 0));
        assertFalse(index.covers(-9, 0) && index.covers(-10, 0));
        assertEquals(9, index.chunkKeys().length);
    }

    @Test
    void scansAChunkOnceAndOnlyInsideTheBox() {
        SlotIndex index = field();
        AtomicInteger probes = new AtomicInteger();
        Set<Long> slots = new HashSet<>();
        slots.add(BlockKeys.block(-120, 98, 0));
        slots.add(BlockKeys.block(-119, 97, 5));
        slots.add(BlockKeys.block(-120, 101, 0)); // above the box: never a slot
        SlotIndex.Probe probe = (x, y, z) -> {
            probes.incrementAndGet();
            return slots.contains(BlockKeys.block(x, y, z));
        };
        long[] found = index.slots(-120 >> 4, 0, probe);
        assertEquals(2, found.length);
        int first = probes.get();
        assertEquals(16 * 12 * 3, first); // x -128..-113 inside -140..-101, z 0..11, y 97..99
        assertArrayEquals(found, index.slots(-120 >> 4, 0, probe));
        assertEquals(first, probes.get()); // lazy: cached
        assertEquals(1, index.scans());
        assertTrue(index.contains(BlockKeys.block(-120, 98, 0), probe));
        assertFalse(index.contains(BlockKeys.block(-121, 98, 0), probe));
        assertFalse(index.contains(BlockKeys.block(-120, 101, 0), probe));
        assertEquals(0, index.slots(5, 5, probe).length); // outside: no scan
        assertEquals(1, index.scans());
    }

    @Test
    void invalidateRescansAfterABuilderEdit() {
        SlotIndex index = field();
        Set<Long> slots = new HashSet<>();
        SlotIndex.Probe probe = (x, y, z) -> slots.contains(BlockKeys.block(x, y, z));
        assertEquals(0, index.slots(-8, 0, probe).length);
        slots.add(BlockKeys.block(-125, 99, 3));
        assertEquals(0, index.slots(-8, 0, probe).length); // still cached
        index.invalidate(-8, 0);
        assertEquals(1, index.slots(-8, 0, probe).length);
        assertEquals(2, index.scans());
        index.clear();
        assertEquals(0, index.indexed());
    }

    @Test
    void keysAreSortedForBinarySearch() {
        SlotIndex index = field();
        SlotIndex.Probe all = (x, y, z) -> (x + z) % 3 == 0;
        long[] found = index.slots(-8, -1, all);
        for (int i = 1; i < found.length; i++) {
            assertTrue(found[i - 1] < found[i]);
        }
        for (long key : found) {
            assertTrue(index.contains(key, all));
        }
    }
}
