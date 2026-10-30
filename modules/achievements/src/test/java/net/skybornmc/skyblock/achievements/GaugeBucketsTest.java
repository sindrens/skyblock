package net.skybornmc.skyblock.achievements;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** QUICK1: gauge polls are spread over the poll window by a stable per-player bucket. */
class GaugeBucketsTest {

    @Test
    void bucketIsStableAndInRange() {
        UUID id = UUID.randomUUID();
        int first = GaugeBuckets.bucket(id, 30);
        for (int i = 0; i < 10; i++) {
            assertEquals(first, GaugeBuckets.bucket(id, 30));
        }
        assertTrue(first >= 0 && first < 30);
    }

    @Test
    void oneBucketOrLessIsAlwaysZero() {
        UUID id = UUID.randomUUID();
        assertEquals(0, GaugeBuckets.bucket(id, 1));
        assertEquals(0, GaugeBuckets.bucket(id, 0));
        assertEquals(0, GaugeBuckets.bucket(null, 30));
    }

    @Test
    void playersSpreadOverAllBuckets() {
        int[] counts = new int[30];
        for (int i = 0; i < 3000; i++) {
            counts[GaugeBuckets.bucket(UUID.randomUUID(), 30)]++;
        }
        for (int count : counts) {
            // 100 expected per bucket; every bucket gets a fair share
            assertTrue(count > 40 && count < 180, "uneven bucket: " + count);
        }
    }
}
