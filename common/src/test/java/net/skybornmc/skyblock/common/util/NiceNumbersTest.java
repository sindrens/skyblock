package net.skybornmc.skyblock.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NiceNumbersTest {

    @Test
    void table() {
        assertEquals(7, NiceNumbers.nice(7));
        assertEquals(25, NiceNumbers.nice(27));
        assertEquals(35, NiceNumbers.nice(36));
        assertEquals(50, NiceNumbers.nice(49));
        assertEquals(120, NiceNumbers.nice(123));
        assertEquals(170, NiceNumbers.nice(166));
        assertEquals(300, NiceNumbers.nice(298));
        assertEquals(400, NiceNumbers.nice(402));
        assertEquals(550, NiceNumbers.nice(543));
        assertEquals(1_000, NiceNumbers.nice(980));
        assertEquals(4_000, NiceNumbers.nice(3_817));
        assertEquals(15_000, NiceNumbers.nice(14_850));
        assertEquals(250, NiceNumbers.nice(250));
    }

    @Test
    void idempotentAndSeriesIncrease() {
        for (long v = 0; v < 200_000; v += 37) {
            long n = NiceNumbers.nice(v);
            assertEquals(n, NiceNumbers.nice(n), "nice(" + v + ")");
            assertTrue(NiceNumbers.next(n) > n);
        }
        assertArrayEquals(new long[]{20, 25, 30, 35}, NiceNumbers.series(new long[]{20, 22, 26, 31}));
    }
}
