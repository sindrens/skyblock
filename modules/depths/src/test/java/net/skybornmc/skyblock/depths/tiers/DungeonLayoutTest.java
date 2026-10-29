package net.skybornmc.skyblock.depths.tiers;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DungeonLayoutTest {

    @Test
    void twelveMineTiersAreTwoSymmetricRowsOfSix() {
        assertArrayEquals(new int[]{19, 20, 21, 23, 24, 25, 28, 29, 30, 32, 33, 34}, DungeonLayout.slots(12));
    }

    @Test
    void elevenAndTen() {
        assertArrayEquals(new int[]{19, 20, 21, 23, 24, 25, 29, 30, 31, 32, 33}, DungeonLayout.slots(11));
        assertArrayEquals(new int[]{20, 21, 22, 23, 24, 29, 30, 31, 32, 33}, DungeonLayout.slots(10));
    }

    @Test
    void smallCounts() {
        assertEquals(0, DungeonLayout.slots(0).length);
        assertArrayEquals(new int[]{22}, DungeonLayout.slots(1));
        assertArrayEquals(new int[]{21, 23}, DungeonLayout.slots(2));
        assertArrayEquals(new int[]{19, 20, 21, 22, 23, 24, 25}, DungeonLayout.slots(7));
    }

    @Test
    void everySlotIsInsideTheFrameAndUnique() {
        for (int n = 0; n <= 40; n++) {
            int[] slots = DungeonLayout.slots(n);
            assertEquals(Math.min(n, DungeonLayout.MAX), slots.length);
            Set<Integer> seen = new HashSet<>();
            for (int slot : slots) {
                int row = slot / 9;
                int col = slot % 9;
                assertTrue(row >= 1 && row <= 4 && col >= 1 && col <= 7, n + ": slot " + slot + " inside the frame");
                assertTrue(seen.add(slot), n + ": slot " + slot + " used once");
            }
            int[] sorted = slots.clone();
            Arrays.sort(sorted);
            assertArrayEquals(sorted, slots, n + ": tier order reads left to right, top to bottom");
        }
    }
}
