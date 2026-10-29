package net.skybornmc.skyblock.islands;

import net.skybornmc.skyblock.common.island.IslandService.Bounds;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Border math: O(1) slot lookup (negative coordinates too), same-island rule and putting entities back. */
class TerritoryTest {

    private static Island island(int slot, int size) {
        int[] grid = IslandGrid.spiral(slot);
        return new Island(UUID.randomUUID(), 1, UUID.randomUUID(), false, slot, grid[0], grid[1], size, null, null, 0L, "islands");
    }

    @Test
    void cellMatchesFloorDivForNegativeCoordinates() {
        for (int block : new int[]{-1025, -1024, -513, -512, -511, -1, 0, 1, 511, 512, 1023, 1024, 100_000, -100_000}) {
            assertEquals(IslandGrid.cell(block), Territory.cell(block), "block " + block);
        }
    }

    @Test
    void cachedAreaEqualsGridBoundsAndFollowsResize() {
        for (int slot = 0; slot < 30; slot++) {
            Island island = island(slot, 3);
            Bounds bounds = IslandGrid.islandBounds("islands", island.gridX(), island.gridZ(), 3);
            assertEquals(bounds.minX(), island.minX());
            assertEquals(bounds.maxZ(), island.maxZ());
            island.sizeChunks(15);
            Bounds big = IslandGrid.islandBounds("islands", island.gridX(), island.gridZ(), 15);
            assertEquals(big.minX(), island.minX());
            assertEquals(big.minZ(), island.minZ());
            assertEquals(big.maxX(), island.maxX());
            assertEquals(big.maxZ(), island.maxZ());
            assertTrue(island.contains(big.minX(), big.maxZ()));
            assertFalse(island.contains(big.minX() - 1, big.maxZ()));
            assertFalse(island.contains(big.minX(), big.maxZ() + 1));
        }
    }

    @Test
    void lookupFindsEveryIslandInItsSlotAndGrows() {
        Territory territory = new Territory();
        Island[] islands = new Island[200]; // spiral reaches ring 7: forces several array resizes from radius 4
        for (int i = 0; i < islands.length; i++) {
            islands[i] = island(i, 3);
            territory.put(islands[i]);
        }
        assertEquals(200, territory.size());
        for (Island island : islands) {
            int cx = island.centerX();
            int cz = island.centerZ();
            assertSame(island, territory.at(cx, cz));
            assertSame(island, territory.inSlot(IslandGrid.slotMin(island.gridX()), IslandGrid.slotMin(island.gridZ())));
            assertNull(territory.at(IslandGrid.slotMin(island.gridX()), IslandGrid.slotMin(island.gridZ())),
                    "slot corner is outside the border");
            assertSame(island, territory.at(island.minX(), island.minZ()));
            assertSame(island, territory.at(island.maxX(), island.maxZ()));
            assertNull(territory.at(island.maxX() + 1, island.maxZ()));
        }
        assertNull(territory.inSlot(1_000_000, -1_000_000), "far outside the grid");
        territory.remove(islands[5]);
        assertNull(territory.inCell(islands[5].gridX(), islands[5].gridZ()));
        assertEquals(199, territory.size());
        territory.clear();
        assertEquals(0, territory.size());
        assertNull(territory.at(islands[0].centerX(), islands[0].centerZ()));
    }

    @Test
    void negativeSlotsWork() {
        Territory territory = new Territory();
        // slot index 6 is grid (-1, -1) in the spiral
        Island island = island(6, 3);
        assertTrue(island.gridX() < 0 && island.gridZ() < 0);
        territory.put(island);
        assertSame(island, territory.at(island.centerX(), island.centerZ()));
        assertSame(island, territory.at(island.minX(), island.minZ()));
        assertNull(territory.at(island.minX() - 1, island.minZ()));
    }

    @Test
    void sameIslandRule() {
        Territory territory = new Territory();
        Island a = island(0, 3);
        Island b = island(1, 3);
        territory.put(a);
        territory.put(b);
        int x = a.maxX();
        int z = a.centerZ();
        assertTrue(territory.sameIsland(a.minX(), a.minZ(), a.maxX(), a.maxZ()), "inside one border");
        assertTrue(territory.sameIsland(x, z, x, z), "vertical flow");
        assertFalse(territory.sameIsland(x, z, x + 1, z), "water at the east edge can't flow out");
        assertFalse(territory.sameIsland(x + 1, z, x, z), "nothing flows in from outside");
        assertFalse(territory.sameIsland(a.centerX(), a.centerZ(), b.centerX(), b.centerZ()), "two islands");
        assertFalse(territory.sameIsland(-1, -1, 0, 0), "across a slot edge (void)");
        int voidX = IslandGrid.slotMin(a.gridX()) + 10;
        assertFalse(territory.sameIsland(voidX, voidX, voidX + 1, voidX), "void between islands never changes");
        a.sizeChunks(5);
        assertTrue(territory.sameIsland(x, z, x + 1, z), "after growing the border the block is inside");
    }

    @Test
    void clampPutsEntitiesBackInsideWithMargin() {
        // border from 240 to 287 (inclusive blocks) = 240.0 .. 288.0
        assertEquals(241.5, Territory.clamp(230.0, 240, 287, 1.5));
        assertEquals(286.5, Territory.clamp(300.2, 240, 287, 1.5));
        assertEquals(260.25, Territory.clamp(260.25, 240, 287, 1.5), "inside stays unchanged");
        assertEquals(-271.5, Territory.clamp(-300, -272, -225, 0.5));
        assertEquals(-224.5, Territory.clamp(-200, -272, -225, 0.5));
    }
}
