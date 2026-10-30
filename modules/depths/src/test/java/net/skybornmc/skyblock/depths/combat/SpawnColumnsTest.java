package net.skybornmc.skyblock.depths.combat;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** QUICK1 (P-10): a column is scanned once per cache life, not once per spawn attempt. */
class SpawnColumnsTest {

    @Test
    void columnIsScannedOnceWithinItsLife() {
        SpawnColumns cache = new SpawnColumns();
        AtomicInteger scans = new AtomicInteger();
        for (int i = 0; i < 5; i++) {
            assertEquals(64, cache.height(1, 10, -20, 1_000 + i, 60_000, () -> {
                scans.incrementAndGet();
                return 64;
            }));
        }
        assertEquals(1, scans.get());
    }

    @Test
    void noSpotIsCachedToo() {
        SpawnColumns cache = new SpawnColumns();
        AtomicInteger scans = new AtomicInteger();
        cache.height(1, 0, 0, 0, 60_000, () -> {
            scans.incrementAndGet();
            return SpawnColumns.NONE;
        });
        assertEquals(SpawnColumns.NONE, cache.height(1, 0, 0, 10, 60_000, () -> {
            scans.incrementAndGet();
            return 70;
        }));
        assertEquals(1, scans.get());
    }

    @Test
    void cacheStartsOverAfterItsLife() {
        SpawnColumns cache = new SpawnColumns();
        cache.height(1, 5, 5, 0, 1_000, () -> 64);
        assertEquals(80, cache.height(1, 5, 5, 1_000, 1_000, () -> 80));
    }

    @Test
    void zeroLifeNeverCaches() {
        SpawnColumns cache = new SpawnColumns();
        AtomicInteger scans = new AtomicInteger();
        cache.height(1, 5, 5, 0, 0, scans::incrementAndGet);
        cache.height(1, 5, 5, 0, 0, scans::incrementAndGet);
        assertEquals(2, scans.get());
        assertEquals(0, cache.size());
    }

    @Test
    void zonesAndColumnsGetDifferentKeys() {
        assertNotEquals(SpawnColumns.key(1, 10, 20), SpawnColumns.key(2, 10, 20));
        assertNotEquals(SpawnColumns.key(1, 10, 20), SpawnColumns.key(1, 20, 10));
        assertNotEquals(SpawnColumns.key(1, -1, 0), SpawnColumns.key(1, 0, -1));
    }

    @Test
    void clearForgetsEverything() {
        SpawnColumns cache = new SpawnColumns();
        cache.height(1, 5, 5, 0, 60_000, () -> 64);
        cache.clear();
        assertEquals(0, cache.size());
        assertEquals(70, cache.height(1, 5, 5, 1, 60_000, () -> 70));
    }
}
