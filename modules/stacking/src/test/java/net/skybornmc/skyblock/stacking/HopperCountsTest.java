package net.skybornmc.skyblock.stacking;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** F13: per-chunk hopper counts kept by events (no scans). */
class HopperCountsTest {

    @Test
    void keyMatchesPaperPackingAndRoundTrips() {
        for (int[] xz : new int[][]{{0, 0}, {1, -1}, {-30_000, 29_999}, {Integer.MIN_VALUE, Integer.MAX_VALUE}}) {
            long key = HopperCounts.key(xz[0], xz[1]);
            assertEquals(org.bukkit.Chunk.getChunkKey(xz[0], xz[1]), key);
            assertEquals(xz[0], HopperCounts.chunkX(key));
            assertEquals(xz[1], HopperCounts.chunkZ(key));
        }
    }

    @Test
    void placeAndBreakKeepTheCount() {
        HopperCounts counts = new HopperCounts();
        long k = HopperCounts.key(3, -4);
        assertEquals(0, counts.get("islands", k));
        for (int i = 0; i < 65; i++) {
            counts.add("islands", k, 1);
        }
        assertEquals(65, counts.get("islands", k));
        assertEquals(64, counts.add("islands", k, -1));
        assertEquals(0, counts.get("other", k), "worlds are separate");
    }

    @Test
    void neverNegativeAndEmptyChunksHoldNoEntry() {
        HopperCounts counts = new HopperCounts();
        long k = HopperCounts.key(0, 0);
        assertEquals(0, counts.add("islands", k, -1), "a break the cache missed (world edit) never goes below 0");
        assertEquals(0, counts.chunks());
        counts.add("islands", k, 1);
        assertEquals(1, counts.chunks());
        counts.add("islands", k, -1);
        assertEquals(0, counts.chunks(), "a chunk back at 0 frees its entry");
    }

    @Test
    void loadSetsAndUnloadForgets() {
        HopperCounts counts = new HopperCounts();
        long a = HopperCounts.key(1, 1);
        long b = HopperCounts.key(2, 2);
        counts.set("islands", a, 70);
        counts.set("islands", b, 0);
        assertEquals(70, counts.get("islands", a));
        assertEquals(1, counts.chunks());
        counts.unload("islands", a);
        assertEquals(0, counts.get("islands", a));
        counts.set("islands", b, 5);
        counts.unloadWorld("islands");
        assertEquals(0, counts.chunks());
    }

    @Test
    void busiestRespectsTheFilter() {
        HopperCounts counts = new HopperCounts();
        counts.set("islands", HopperCounts.key(0, 0), 10);
        counts.set("islands", HopperCounts.key(1, 0), 80);
        counts.set("islands", HopperCounts.key(50, 50), 500);
        Optional<HopperCounts.Hotspot> inside = counts.busiest("islands", (x, z) -> x >= 0 && x <= 1 && z == 0);
        assertTrue(inside.isPresent());
        assertEquals(new HopperCounts.Hotspot(1, 0, 80), inside.get());
        assertFalse(counts.busiest("islands", (x, z) -> x < 0).isPresent());
        assertFalse(counts.busiest("nether", (x, z) -> true).isPresent());
    }
}
