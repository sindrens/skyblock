package net.skybornmc.skyblock.islands;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ObsidianBucketTest {

    @Test
    void cooldownThenHourlyCap() {
        Deque<Long> recent = new ArrayDeque<>();
        assertNull(ObsidianBucket.refusal(recent, 10_000, 2_000, 2));
        recent.addLast(10_000L);
        assertEquals("cooldown", ObsidianBucket.refusal(recent, 11_000, 2_000, 2));
        assertNull(ObsidianBucket.refusal(recent, 12_000, 2_000, 2));
        recent.addLast(12_000L);
        assertEquals("limit", ObsidianBucket.refusal(recent, 20_000, 2_000, 2));
    }

    @Test
    void oldUsesFallOutOfTheHour() {
        Deque<Long> recent = new ArrayDeque<>();
        recent.addLast(0L);
        recent.addLast(5_000L);
        assertNull(ObsidianBucket.refusal(recent, 3_600_000L + 1, 2_000, 2));
        assertEquals(1, recent.size());
    }

    @Test
    void zeroMeansNoCap() {
        Deque<Long> recent = new ArrayDeque<>();
        for (long t = 0; t < 50; t++) {
            recent.addLast(t * 10_000);
        }
        assertNull(ObsidianBucket.refusal(recent, 600_000, 2_000, 0));
    }
}
