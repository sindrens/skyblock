package net.skybornmc.skyblock.tutorial;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PERF4: compiled mine-block rules and the once-computed marker distances behave like the old per-block code. */
class MarkerScanTest {

    /** The old rule test, kept here as the reference. */
    private static boolean oldRule(String key, List<String> rules) {
        for (String rule : rules) {
            String r = rule.strip().toLowerCase(java.util.Locale.ROOT);
            if (r.startsWith("*") ? key.endsWith(r.substring(1)) : key.equals(r)) {
                return true;
            }
        }
        return false;
    }

    @Test
    void matcherAgreesWithTheOldRule() {
        List<String> rules = List.of("*_ore", " Stone ", "cobblestone", "deepslate", "");
        MarkerScan.Matcher m = MarkerScan.Matcher.compile(rules);
        for (String key : List.of("iron_ore", "deepslate_iron_ore", "stone", "cobblestone", "deepslate", "dirt", "ore",
                "stone_bricks", "", "andesite")) {
            assertEquals(oldRule(key, rules), m.matches(key), key);
        }
        assertTrue(m.matches("iron_ore"));
        assertFalse(m.matches("dirt"));
        assertTrue(MarkerScan.Matcher.compile(List.of("*")).matches("anything"), "a bare * matches every block");
        assertFalse(MarkerScan.Matcher.compile(List.of()).matches("stone"));
    }

    @Test
    void nearestFirstIsStableAndUsesTheBlockCorner() {
        // centre 0.5/64/0.5 (a player standing in the middle of a block); blocks measured from their corner
        List<MarkerScan.Hit> hits = List.of(
                MarkerScan.Hit.of(4, 64, 0, 0.5, 64, 0.5),
                MarkerScan.Hit.of(0, 64, 0, 0.5, 64, 0.5),
                MarkerScan.Hit.of(1, 64, 1, 0.5, 64, 0.5),
                MarkerScan.Hit.of(-2, 64, 0, 0.5, 64, 0.5));
        List<MarkerScan.Hit> sorted = MarkerScan.nearestFirst(hits);
        assertEquals(0.5, sorted.get(0).distanceSquared(), 1e-9);
        assertEquals(0, sorted.get(0).x());
        assertEquals(1, sorted.get(1).x(), "(1,1) ties with (0,0) at 0.5 but came later: stable order");
        assertEquals(-2, sorted.get(2).x());
        assertEquals(4, sorted.get(3).x());
        assertEquals(4, hits.size(), "input untouched");
    }

    // ------------------------------------------------------------------ PERF7: nearest-first walk with early stop

    /** The old scan: test every block in loop order, stable-sort the hits, then filter and take the first {@code limit}. */
    private static List<MarkerScan.Hit> oldScan(java.util.function.Predicate<int[]> block, java.util.function.Predicate<int[]> after,
                                                int bx, int by, int bz, double cx, double cy, double cz, int r, int dyMin, int dyMax,
                                                MarkerScan.Loop loop, int limit) {
        List<MarkerScan.Hit> found = new java.util.ArrayList<>();
        for (int dx = -r; dx <= r; dx++) {
            for (int a = (loop == MarkerScan.Loop.X_Z_Y ? -r : dyMin); a <= (loop == MarkerScan.Loop.X_Z_Y ? r : dyMax); a++) {
                for (int b = (loop == MarkerScan.Loop.X_Z_Y ? dyMin : -r); b <= (loop == MarkerScan.Loop.X_Z_Y ? dyMax : r); b++) {
                    int dz = loop == MarkerScan.Loop.X_Z_Y ? a : b;
                    int dy = loop == MarkerScan.Loop.X_Z_Y ? b : a;
                    int[] pos = {bx + dx, by + dy, bz + dz};
                    if (block.test(pos)) {
                        found.add(MarkerScan.Hit.of(pos[0], pos[1], pos[2], cx, cy, cz));
                    }
                }
            }
        }
        List<MarkerScan.Hit> out = new java.util.ArrayList<>();
        for (MarkerScan.Hit hit : MarkerScan.nearestFirst(found)) {
            if (!after.test(new int[]{hit.x(), hit.y(), hit.z()})) {
                continue;
            }
            out.add(hit);
            if (out.size() >= limit) {
                break;
            }
        }
        return out;
    }

    @Test
    void nearestFirstWalkPicksExactlyTheOldMarkers() {
        java.util.Random random = new java.util.Random(7);
        for (int round = 0; round < 300; round++) {
            long seed = random.nextLong();
            double density = random.nextDouble() * 0.3;
            java.util.function.Predicate<int[]> block = pos -> new java.util.Random(seed ^ (pos[0] * 73856093L) ^ (pos[1] * 19349663L)
                    ^ (pos[2] * 83492791L)).nextDouble() < density;
            java.util.function.Predicate<int[]> island = pos -> Math.floorMod(pos[0] + pos[2], 5) != 0; // some hits filtered out
            int bx = random.nextInt(2000) - 1000;
            int by = 40 + random.nextInt(40);
            int bz = random.nextInt(2000) - 1000;
            // centres on the block corner, the block middle and odd fractions (ties appear with .0 and .5)
            double[] fractions = {0.0, 0.5, random.nextDouble()};
            double cx = bx + fractions[random.nextInt(3)];
            double cy = by + fractions[random.nextInt(3)];
            double cz = bz + fractions[random.nextInt(3)];
            boolean farm = random.nextBoolean();
            int r = farm ? 2 + random.nextInt(5) : 2 + random.nextInt(4);
            int dyMin = farm ? -3 : -r;
            int dyMax = farm ? 2 : r;
            MarkerScan.Loop loop = farm ? MarkerScan.Loop.X_Z_Y : MarkerScan.Loop.X_Y_Z;
            int limit = 1 + random.nextInt(6);
            List<MarkerScan.Hit> expected = oldScan(block, island, bx, by, bz, cx, cy, cz, r, dyMin, dyMax, loop, limit);
            List<MarkerScan.Hit> order = MarkerScan.order(bx, by, bz, cx, cy, cz, r, dyMin, dyMax, loop);
            List<MarkerScan.Hit> actual = MarkerScan.firstMatches(order, h -> {
                int[] pos = {h.x(), h.y(), h.z()};
                return block.test(pos) && island.test(pos);
            }, limit);
            assertEquals(expected, actual, "round " + round);
        }
    }

    @Test
    void walkStopsEarly() {
        int[] reads = {0};
        List<MarkerScan.Hit> order = MarkerScan.order(0, 64, 0, 0.5, 64, 0.5, 6, -3, 2, MarkerScan.Loop.X_Z_Y);
        assertEquals(13 * 13 * 6, order.size());
        List<MarkerScan.Hit> hits = MarkerScan.firstMatches(order, h -> {
            reads[0]++;
            return true;
        }, 3);
        assertEquals(3, hits.size());
        assertEquals(3, reads[0], "only as many blocks as markers needed were read");
        assertTrue(MarkerScan.firstMatches(order, h -> true, 0).isEmpty());
    }

    @Test
    void chunkSpanCoversTheBox() {
        org.junit.jupiter.api.Assertions.assertArrayEquals(new int[]{0, 1}, MarkerScan.chunkSpan(10, 6));
        org.junit.jupiter.api.Assertions.assertArrayEquals(new int[]{-1, 0}, MarkerScan.chunkSpan(2, 6));
        org.junit.jupiter.api.Assertions.assertArrayEquals(new int[]{-2, -1}, MarkerScan.chunkSpan(-20, 6));
        org.junit.jupiter.api.Assertions.assertArrayEquals(new int[]{0, 0}, MarkerScan.chunkSpan(8, 6));
    }
}
