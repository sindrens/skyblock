package net.skybornmc.skyblock.common.world;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkMarksTest {

    @Test
    void positionsInOneChunkNeverCollide() {
        Set<Integer> seen = new HashSet<>();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = -64; y < 320; y += 7) {
                    assertTrue(seen.add(ChunkMarks.pack(x, y, z)));
                }
            }
        }
        assertEquals(ChunkMarks.pack(3, 70, 5), ChunkMarks.pack(16 + 3, 70, -16 + 5), "chunk-relative");
        assertNotEquals(ChunkMarks.pack(0, 0, 0), ChunkMarks.pack(0, 1, 0));
    }

    @Test
    void addRemoveKeepTheSetSorted() {
        int[] s = new int[0];
        s = ChunkMarks.add(s, 5, 10);
        s = ChunkMarks.add(s, 1, 10);
        s = ChunkMarks.add(s, 9, 10);
        assertArrayEquals(new int[]{1, 5, 9}, s);
        assertSame(s, ChunkMarks.add(s, 5, 10), "already in");
        assertTrue(ChunkMarks.contains(s, 9));
        s = ChunkMarks.remove(s, 5);
        assertArrayEquals(new int[]{1, 9}, s);
        assertFalse(ChunkMarks.contains(s, 5));
        assertSame(s, ChunkMarks.remove(s, 7));
        assertSame(s, ChunkMarks.add(s, 4, 2), "full: not stored");
        assertFalse(ChunkMarks.contains(null, 1));
    }
}
