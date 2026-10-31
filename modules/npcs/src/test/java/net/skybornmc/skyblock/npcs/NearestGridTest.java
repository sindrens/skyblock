package net.skybornmc.skyblock.npcs;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PERF4: the nearest-player grid (was part of SpawnQueueTest until SpawnQueue moved to common in PERF10). */
class NearestGridTest {

    @Test
    void neighbourGridFindsTheNearestInRange() {
        NearestGrid<String> grid = new NearestGrid<>(6);
        assertTrue(grid.isEmpty());
        assertEquals(null, grid.nearest(0, 0, 0, 6));
        grid.add("far", 100, 64, 100);
        grid.add("near", 3, 65, 0);
        grid.add("edge", 0, 64, -6);
        grid.add("other-cell", -5.9, 64, 0.5);
        assertEquals("near", grid.nearest(0, 64, 0, 6), "3.2 blocks beats 5.9 and 6");
        assertEquals("edge", grid.nearest(0, 64, -4, 2), "exactly on the radius counts (old d <= r²)");
        assertEquals(null, grid.nearest(50, 64, 50, 6));
        assertEquals("far", grid.nearest(95, 64, 97, 6), "across a cell border");
        assertEquals(null, grid.nearest(0, 80, 0, 6), "3D distance, not only horizontal");
    }
}
