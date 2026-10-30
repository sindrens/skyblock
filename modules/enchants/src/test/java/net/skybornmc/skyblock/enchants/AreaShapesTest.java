package net.skybornmc.skyblock.enchants;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Area enchant shapes (ENCHANTS §6.2, §6.3). */
class AreaShapesTest {

    @Test
    void keysRoundTrip() {
        for (int[] p : new int[][]{{0, 0, 0}, {-1, -64, -1}, {30_000_000, 319, -30_000_000}, {123, 70, -456}}) {
            long k = AreaShapes.key(p[0], p[1], p[2]);
            assertEquals(p[0], AreaShapes.x(k));
            assertEquals(p[1], AreaShapes.y(k));
            assertEquals(p[2], AreaShapes.z(k));
        }
    }

    @Test
    void nearestStartsWithTheSixFaces() {
        List<int[]> cube = AreaShapes.nearest(2);
        assertEquals(124, cube.size());
        for (int i = 0; i < 6; i++) {
            int[] o = cube.get(i);
            assertEquals(1, Math.abs(o[0]) + Math.abs(o[1]) + Math.abs(o[2]));
        }
        assertEquals(1, cube.get(0)[1], "ties: the block above first");
    }

    @Test
    void blastSizes() {
        assertEquals(8, AreaShapes.blast(3, 1, 0, 0, 1).size());   // Dragonfire I: 3x3x1
        assertEquals(17, AreaShapes.blast(3, 2, 0, 0, 1).size());  // II: 3x3x2
        assertEquals(26, AreaShapes.blast(3, 3, 0, 0, 1).size());  // III: 3x3x3
        assertEquals(74, AreaShapes.blast(5, 3, 1, 0, 0).size());  // Worldbreaker 5x5x3
    }

    @Test
    void blastGoesAwayFromThePlayer() {
        for (int[] o : AreaShapes.blast(3, 3, 0, 0, 1)) {       // facing south (+z)
            assertTrue(o[2] >= 0 && o[2] <= 2);
            assertTrue(Math.abs(o[0]) <= 1 && Math.abs(o[1]) <= 1);
        }
        for (int[] o : AreaShapes.blast(3, 2, 0, -1, 0)) {      // looking down
            assertTrue(o[1] <= 0 && o[1] >= -1);
            assertTrue(Math.abs(o[0]) <= 1 && Math.abs(o[2]) <= 1);
        }
        for (int[] o : AreaShapes.blast(3, 1, -1, 0, 0)) {      // facing west, one layer: only the plane x = 0
            assertEquals(0, o[0]);
        }
    }

    @Test
    void facingPicksTheMainAxis() {
        assertArrayEquals3(new int[]{0, 0, 1}, AreaShapes.facing(0.1, -0.2, 0.9));
        assertArrayEquals3(new int[]{-1, 0, 0}, AreaShapes.facing(-0.9, 0.3, 0.1));
        assertArrayEquals3(new int[]{0, -1, 0}, AreaShapes.facing(0.3, -0.9, 0.2));
    }

    private static void assertArrayEquals3(int[] a, int[] b) {
        assertEquals(a[0], b[0]);
        assertEquals(a[1], b[1]);
        assertEquals(a[2], b[2]);
    }

    /** A tree: trunk x=0,z=0, y=0..9 plus a branch at y=6 going to x=1..2. */
    private static Set<Long> tree(int ox, int oz) {
        Set<Long> logs = new HashSet<>();
        for (int y = 0; y < 10; y++) {
            logs.add(AreaShapes.key(ox, y, oz));
        }
        logs.add(AreaShapes.key(ox + 1, 6, oz));
        logs.add(AreaShapes.key(ox + 2, 7, oz));
        return logs;
    }

    @Test
    void treefellerTakesLogsOfTheSameTreeUpwards() {
        Set<Long> logs = tree(0, 0);
        logs.addAll(tree(6, 0));
        long cut = AreaShapes.key(0, 2, 0);
        List<Long> felled = AreaShapes.fill(cut, 3, logs::contains);
        assertEquals(3, felled.size());
        assertFalse(felled.contains(cut));
        assertTrue(felled.contains(AreaShapes.key(0, 3, 0)), "the log above first");
        for (long k : felled) {
            assertEquals(0, AreaShapes.x(k), "never the other tree");
        }
        assertEquals(11, AreaShapes.fill(cut, 99, logs::contains).size(), "whole tree minus the cut log");
    }

    @Test
    void wildfireFindsTheNearestOtherTrees() {
        Set<Long> logs = tree(0, 0);
        logs.addAll(tree(4, 0));
        logs.addAll(tree(-6, 0));
        logs.addAll(tree(20, 0)); // out of range
        long cut = AreaShapes.key(0, 0, 0);
        Set<Long> visited = new HashSet<>();
        List<Long> own = AreaShapes.tree(cut, 64, logs::contains, visited);
        assertEquals(12, own.size());
        List<List<Long>> others = AreaShapes.trees(0, 0, 0, 8, 4, 12, 1, 64, 999, logs::contains, visited);
        assertEquals(1, others.size());
        assertEquals(4, AreaShapes.x(others.get(0).get(0)), "nearest tree first");
        Set<Long> visited2 = new HashSet<>(own);
        List<List<Long>> all = AreaShapes.trees(0, 0, 0, 8, 4, 12, 10, 64, 999, logs::contains, visited2);
        assertEquals(2, all.size(), "the tree 20 blocks away is out of range");
        List<List<Long>> capped = AreaShapes.trees(0, 0, 0, 8, 4, 12, 10, 64, 5, logs::contains, new HashSet<>(own));
        assertEquals(5, capped.stream().mapToInt(List::size).sum(), "never more blocks than the budget");
    }
}
