package net.skybornmc.skyblock.depths.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class BlockKeysTest {

    @Test
    void roundTripsIncludingNegatives() {
        int[][] cases = {{0, 0, 0}, {1, 2, 3}, {-1, -64, -1}, {29_999_999, 2047, -29_999_999}, {-30_000_000, -2048, 30_000_000},
                {15, 319, 16}, {-17, 100, 33}};
        for (int[] c : cases) {
            long key = BlockKeys.block(c[0], c[1], c[2]);
            assertEquals(c[0], BlockKeys.x(key));
            assertEquals(c[1], BlockKeys.y(key));
            assertEquals(c[2], BlockKeys.z(key));
        }
    }

    @Test
    void neighboursHaveDistinctKeys() {
        long base = BlockKeys.block(10, 64, 10);
        assertNotEquals(base, BlockKeys.block(11, 64, 10));
        assertNotEquals(base, BlockKeys.block(10, 65, 10));
        assertNotEquals(base, BlockKeys.block(10, 64, 11));
    }

    @Test
    void chunkKeyMatchesBukkitFormula() {
        assertEquals((long) 3 & 0xffffffffL | ((long) -2 & 0xffffffffL) << 32, BlockKeys.chunk(3, -2));
        assertEquals(BlockKeys.chunk(-1, -1), BlockKeys.chunkOf(BlockKeys.block(-1, 70, -16)));
        assertEquals(BlockKeys.chunk(0, 1), BlockKeys.chunkOf(BlockKeys.block(15, 70, 16)));
    }
}
