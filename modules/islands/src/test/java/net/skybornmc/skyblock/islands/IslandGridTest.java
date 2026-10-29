package net.skybornmc.skyblock.islands;

import net.skybornmc.skyblock.common.island.IslandService.Bounds;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IslandGridTest {

    @Test
    void spiralStartsAtOriginAndFillsRingOne() {
        assertArrayEquals(new int[]{0, 0}, IslandGrid.spiral(0));
        Set<String> ring = new HashSet<>();
        for (int i = 1; i <= 8; i++) {
            int[] c = IslandGrid.spiral(i);
            assertEquals(1, Math.max(Math.abs(c[0]), Math.abs(c[1])), "index " + i);
            ring.add(c[0] + "," + c[1]);
        }
        assertEquals(8, ring.size());
    }

    @Test
    void spiralIsUniqueAndCompletesEverySquare() {
        Set<Long> seen = new HashSet<>();
        int previousRing = 0;
        for (int i = 0; i < 25 * 25; i++) {
            int[] c = IslandGrid.spiral(i);
            assertTrue(seen.add(IslandGrid.key(c[0], c[1])), "duplicate cell at index " + i);
            int ring = Math.max(Math.abs(c[0]), Math.abs(c[1]));
            assertTrue(ring >= previousRing, "ring went backwards at index " + i);
            previousRing = ring;
        }
        // the first 625 indexes are exactly the 25x25 square around 0,0
        for (int x = -12; x <= 12; x++) {
            for (int z = -12; z <= 12; z++) {
                assertTrue(seen.contains(IslandGrid.key(x, z)), x + "," + z);
            }
        }
    }

    @Test
    void neighbouringSpiralSlotsTouch() {
        for (int i = 1; i < 2000; i++) {
            int[] a = IslandGrid.spiral(i - 1);
            int[] b = IslandGrid.spiral(i);
            int step = Math.abs(a[0] - b[0]) + Math.abs(a[1] - b[1]);
            // within a ring each step is one cell; entering a new ring may move diagonally
            assertTrue(step <= 2, "jump between " + (i - 1) + " and " + i);
        }
    }

    @Test
    void negativeIndexRejected() {
        assertThrows(IllegalArgumentException.class, () -> IslandGrid.spiral(-1));
    }

    @Test
    void cellsUseFloorDivision() {
        assertEquals(0, IslandGrid.cell(0));
        assertEquals(0, IslandGrid.cell(511));
        assertEquals(1, IslandGrid.cell(512));
        assertEquals(-1, IslandGrid.cell(-1));
        assertEquals(-1, IslandGrid.cell(-512));
        assertEquals(-2, IslandGrid.cell(-513));
    }

    @Test
    void startIslandIsThreeChunksAlignedToChunks() {
        Bounds b = IslandGrid.islandBounds("islands", 0, 0, 3);
        assertEquals(240, b.minX());
        assertEquals(287, b.maxX());
        assertEquals(48, b.width());
        assertEquals(0, b.minX() % 16);
        assertEquals(15, b.maxX() % 16);
        assertEquals(264.0, b.centerX());
        assertEquals(IslandGrid.centerBlock(0), (int) b.centerX());
    }

    @Test
    void maxIslandStaysInsideItsSlotEverywhere() {
        for (int gx : new int[]{-3, 0, 5}) {
            Bounds slot = IslandGrid.slotBounds("islands", gx, gx);
            for (int size = 1; size <= IslandGrid.MAX_POSSIBLE_CHUNKS; size += 2) {
                Bounds island = IslandGrid.islandBounds("islands", gx, gx, size);
                assertTrue(island.minX() >= slot.minX() && island.maxX() <= slot.maxX(), "size " + size);
                assertEquals(size * 16, island.width());
                assertEquals(0, Math.floorMod(island.minX(), 16));
            }
        }
        Bounds fifteen = IslandGrid.islandBounds("islands", 0, 0, 15);
        assertEquals(144, fifteen.minX());
        assertEquals(383, fifteen.maxX());
    }

    @Test
    void islandsKeepTheSameCenterWhenGrowing() {
        double center = IslandGrid.islandBounds("islands", 2, -1, 3).centerZ();
        for (int size = 5; size <= 15; size += 2) {
            assertEquals(center, IslandGrid.islandBounds("islands", 2, -1, size).centerZ());
        }
    }

    @Test
    void sizesMustBeOdd() {
        assertTrue(IslandGrid.validSize(3));
        assertTrue(IslandGrid.validSize(31));
        assertFalse(IslandGrid.validSize(4));
        assertFalse(IslandGrid.validSize(0));
        assertFalse(IslandGrid.validSize(33));
        assertThrows(IllegalArgumentException.class, () -> IslandGrid.islandBounds("islands", 0, 0, 4));
        assertEquals(5, IslandGrid.grow(3, 15));
        assertEquals(15, IslandGrid.grow(15, 15));
    }

    @Test
    void boundsContainment() {
        Bounds b = IslandGrid.islandBounds("islands", -1, 0, 3);
        assertTrue(b.contains(b.minX(), b.minZ()));
        assertTrue(b.contains(b.maxX(), b.maxZ()));
        assertFalse(b.contains(b.minX() - 1, b.minZ()));
        assertFalse(b.contains(b.maxX(), b.maxZ() + 1));
        assertEquals(-512 + 240, b.minX());
    }
}
