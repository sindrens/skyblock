package net.skybornmc.skyblock.islands;

import net.skybornmc.skyblock.common.island.IslandService.Bounds;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Clearing slices: cover the area exactly once, stay in the slot, small rectangles. */
class ClearPlanTest {

    private static Set<Long> covered(ClearPlan plan) {
        Set<Long> chunks = new HashSet<>();
        for (ClearPlan.Slice slice : plan.slices()) {
            assertTrue(slice.minChunkX() <= slice.maxChunkX());
            for (int cx = slice.minChunkX(); cx <= slice.maxChunkX(); cx++) {
                assertTrue(chunks.add(IslandGrid.key(cx, slice.chunkZ())), "chunk " + cx + "," + slice.chunkZ() + " cleared twice");
            }
        }
        return chunks;
    }

    @Test
    void coversTheMaxIslandPlusExtraRingsExactlyOnce() {
        ClearPlan plan = ClearPlan.of(0, 0, 3, 15, 1, 4);
        assertEquals(17, plan.widthChunks(), "max 15 + one ring on each side");
        Set<Long> chunks = covered(plan);
        assertEquals(17 * 17, chunks.size());
        assertEquals(plan.chunks(), chunks.size());
        for (int cx = plan.minChunkX(); cx < plan.minChunkX() + 17; cx++) {
            for (int cz = plan.minChunkZ(); cz < plan.minChunkZ() + 17; cz++) {
                assertTrue(chunks.contains(IslandGrid.key(cx, cz)));
            }
        }
    }

    @Test
    void containsTheBiggestIslandOfThatSlot() {
        for (int[] grid : new int[][]{{0, 0}, {-1, 2}, {3, -4}}) {
            ClearPlan plan = ClearPlan.of(grid[0], grid[1], 5, 15, 0, 3);
            Bounds max = IslandGrid.islandBounds("islands", grid[0], grid[1], 15);
            assertEquals(max.minX() >> 4, plan.minChunkX());
            assertEquals(max.minZ() >> 4, plan.minChunkZ());
            assertEquals(15, plan.widthChunks());
            assertEquals(15 * 15, covered(plan).size());
            Bounds slot = IslandGrid.slotBounds("islands", grid[0], grid[1]);
            for (ClearPlan.Slice slice : plan.slices()) {
                assertTrue(slot.contains(slice.minBlockX(), slice.minBlockZ()) && slot.contains(slice.maxBlockX(), slice.maxBlockZ()),
                        "slices stay inside the slot");
            }
        }
    }

    @Test
    void anIslandBiggerThanTheMaxIsStillCleared() {
        ClearPlan plan = ClearPlan.of(2, 2, 21, 15, 0, 8);
        assertEquals(21, plan.widthChunks(), "staff may have set a size above today's max");
    }

    @Test
    void neverLeavesTheSlot() {
        ClearPlan plan = ClearPlan.of(1, -1, 31, 31, 8, 5);
        assertEquals(ClearPlan.SLOT_CHUNKS, plan.widthChunks(), "clamped to the 32 chunks of a slot");
        assertEquals(32 * 32, covered(plan).size());
        assertEquals(32, plan.minChunkX());
        assertEquals(-32, plan.minChunkZ());
    }

    @Test
    void slicesAreShortStripsOfOneRow() {
        ClearPlan plan = ClearPlan.of(0, 0, 3, 15, 1, 4);
        for (ClearPlan.Slice slice : plan.slices()) {
            assertTrue(slice.chunks() >= 1 && slice.chunks() <= 4);
            assertEquals(slice.minChunkX() * 16, slice.minBlockX());
            assertEquals(slice.maxChunkX() * 16 + 15, slice.maxBlockX());
            assertEquals(slice.chunkZ() * 16 + 15, slice.maxBlockZ());
        }
        assertEquals(17 * 5, plan.slices().size(), "17 rows of 4+4+4+4+1 chunks");
        assertEquals(17 * 17, ClearPlan.of(0, 0, 3, 15, 1, 1).slices().size(), "one chunk per slice");
        assertEquals(17 * 17, ClearPlan.of(0, 0, 3, 15, 1, 0).slices().size(), "non-positive slice size = 1 chunk");
    }
}
