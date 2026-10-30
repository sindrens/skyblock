package net.skybornmc.skyblock.stacking;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Packing of crop positions stored in chunk data (owner S25: crops grow without light). */
class CropPositionsTest {

    @Test
    void packRoundTrip() {
        int minY = -64;
        for (int[] p : new int[][]{{0, -64, 0}, {15, 319, 15}, {7, 64, 3}, {3, 0, 12}}) {
            int packed = CropPositions.pack(p[0], p[1], p[2], minY);
            assertEquals(p[0], CropPositions.x(packed));
            assertEquals(p[1], CropPositions.y(packed, minY));
            assertEquals(p[2], CropPositions.z(packed));
        }
    }

    @Test
    void addRemoveAndDirtyFlag() {
        CropPositions set = new CropPositions(null);
        assertTrue(set.isEmpty());
        assertFalse(set.dirty());
        assertTrue(set.add(5));
        assertFalse(set.add(5));
        for (int i = 10; i < 30; i++) {
            set.add(i);
        }
        assertEquals(21, set.size());
        assertTrue(set.dirty());
        int[] stored = set.snapshot();
        assertFalse(set.dirty());
        assertEquals(21, stored.length);
        assertTrue(set.remove(5));
        assertFalse(set.remove(5));
        assertEquals(20, set.size());
        assertTrue(set.dirty());
    }

    @Test
    void loadsStoredDataAndDropsDuplicates() {
        CropPositions set = new CropPositions(new int[]{3, 1, 3, 2, 1});
        assertEquals(3, set.size());
        assertTrue(set.dirty());
        int[] values = set.snapshot();
        Arrays.sort(values);
        assertArrayEquals(new int[]{1, 2, 3}, values);
        CropPositions clean = new CropPositions(new int[]{4, 9});
        assertFalse(clean.dirty());
        assertEquals(2, clean.size());
    }
}
