package net.skybornmc.skyblock.enchants;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongPredicate;

/**
 * Shapes of the area enchants (ENCHANTS §6.2, §6.3), pure and unit-tested. Blocks are packed into longs ({@link #key});
 * "member" predicates answer "is this one of the player's own, not yet depleted resource blocks of the same region?",
 * so a shape never reaches real world blocks or another player's view.
 */
final class AreaShapes {

    private static final int XZ_BITS = 26;
    private static final int Y_BITS = 12;
    private static final long XZ_MASK = (1L << XZ_BITS) - 1;
    private static final long Y_MASK = (1L << Y_BITS) - 1;
    private static final int XZ_OFFSET = 1 << (XZ_BITS - 1);
    private static final int Y_OFFSET = 1 << (Y_BITS - 1);
    private static final Map<Integer, List<int[]>> NEAREST = new ConcurrentHashMap<>();

    private AreaShapes() {
    }

    // ------------------------------------------------------------------ keys

    static long key(int x, int y, int z) {
        return (((long) (x + XZ_OFFSET) & XZ_MASK) << (XZ_BITS + Y_BITS))
                | (((long) (z + XZ_OFFSET) & XZ_MASK) << Y_BITS)
                | ((long) (y + Y_OFFSET) & Y_MASK);
    }

    static int x(long key) {
        return (int) ((key >>> (XZ_BITS + Y_BITS)) & XZ_MASK) - XZ_OFFSET;
    }

    static int z(long key) {
        return (int) ((key >>> Y_BITS) & XZ_MASK) - XZ_OFFSET;
    }

    static int y(long key) {
        return (int) (key & Y_MASK) - Y_OFFSET;
    }

    // ------------------------------------------------------------------ mine shapes

    /**
     * Offsets of a cube with {@code radius} around the origin (origin left out), nearest first (ties: higher first, then
     * x, then z, so the order is stable). Cached per radius. Vein Rend takes the first N that are the same ore.
     */
    static List<int[]> nearest(int radius) {
        int r = Math.max(0, Math.min(radius, 8));
        return NEAREST.computeIfAbsent(r, AreaShapes::buildNearest);
    }

    private static List<int[]> buildNearest(int r) {
        List<int[]> out = new ArrayList<>();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx != 0 || dy != 0 || dz != 0) {
                        out.add(new int[]{dx, dy, dz});
                    }
                }
            }
        }
        out.sort(Comparator.<int[]>comparingInt(o -> o[0] * o[0] + o[1] * o[1] + o[2] * o[2])
                .thenComparingInt(o -> -o[1]).thenComparingInt(o -> o[0]).thenComparingInt(o -> o[2]));
        return List.copyOf(out);
    }

    /**
     * A blast: {@code size × size} squares across the facing direction, {@code depth} layers deep starting at the origin's
     * layer and going along the facing (away from the player). The origin itself is left out. {@code facing} is a unit
     * axis vector (one of the six block faces).
     */
    static List<int[]> blast(int size, int depth, int fx, int fy, int fz) {
        int half = Math.max(0, (Math.max(1, size) - 1) / 2);
        int layers = Math.max(1, depth);
        List<int[]> out = new ArrayList<>();
        for (int layer = 0; layer < layers; layer++) {
            int cx = fx * layer;
            int cy = fy * layer;
            int cz = fz * layer;
            for (int a = -half; a <= half; a++) {
                for (int b = -half; b <= half; b++) {
                    int dx;
                    int dy;
                    int dz;
                    if (fx != 0) {          // facing east/west: square in y/z
                        dx = cx;
                        dy = cy + a;
                        dz = cz + b;
                    } else if (fy != 0) {   // facing up/down: square in x/z
                        dx = cx + a;
                        dy = cy;
                        dz = cz + b;
                    } else {                // facing north/south: square in x/y
                        dx = cx + a;
                        dy = cy + b;
                        dz = cz;
                    }
                    if (dx != 0 || dy != 0 || dz != 0) {
                        out.add(new int[]{dx, dy, dz});
                    }
                }
            }
        }
        return out;
    }

    /** The facing axis from a look direction: the largest component wins (a steep look up/down blasts vertically). */
    static int[] facing(double dx, double dy, double dz) {
        double ax = Math.abs(dx);
        double ay = Math.abs(dy);
        double az = Math.abs(dz);
        if (ay >= ax && ay >= az) {
            return new int[]{0, dy >= 0 ? 1 : -1, 0};
        }
        if (ax >= az) {
            return new int[]{dx >= 0 ? 1 : -1, 0, 0};
        }
        return new int[]{0, 0, dz >= 0 ? 1 : -1};
    }

    // ------------------------------------------------------------------ tree shapes

    /**
     * Connected members (26 neighbours) around {@code start}, breadth first, at most {@code max}. The start block itself is
     * not returned and not tested (it is the block being broken), but the search grows from it.
     */
    static List<Long> fill(long start, int max, LongPredicate member) {
        return grow(start, false, max, member, new HashSet<>());
    }

    /**
     * One whole tree: the member {@code seed} and everything connected to it, at most {@code max} blocks, breadth first.
     * {@code visited} collects every block looked at, so a later tree never takes the same logs.
     */
    static List<Long> tree(long seed, int max, LongPredicate member, Set<Long> visited) {
        return grow(seed, true, max, member, visited);
    }

    private static List<Long> grow(long start, boolean includeStart, int max, LongPredicate member, Set<Long> visited) {
        List<Long> out = new ArrayList<>();
        if (max <= 0) {
            return out;
        }
        ArrayDeque<Long> queue = new ArrayDeque<>();
        visited.add(start);
        if (includeStart) {
            if (!member.test(start)) {
                return out;
            }
            out.add(start);
        }
        queue.add(start);
        while (!queue.isEmpty() && out.size() < max) {
            long at = queue.poll();
            int x = x(at);
            int y = y(at);
            int z = z(at);
            for (int dy = 1; dy >= -1 && out.size() < max; dy--) { // upward first: a tree falls from the cut up
                for (int dx = -1; dx <= 1 && out.size() < max; dx++) {
                    for (int dz = -1; dz <= 1 && out.size() < max; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        long next = key(x + dx, y + dy, z + dz);
                        if (!visited.add(next)) {
                            continue;
                        }
                        if (member.test(next)) {
                            out.add(next);
                            queue.add(next);
                        }
                    }
                }
            }
        }
        return out;
    }

    /**
     * Seeds for other trees: member blocks in a box around the origin ({@code radius} sideways, {@code down}/{@code up}
     * vertically), nearest first by horizontal distance (then lowest). Blocks in {@code skip} are left out.
     */
    static List<Long> seeds(int ox, int oy, int oz, int radius, int down, int up, LongPredicate member, Set<Long> skip) {
        List<long[]> found = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int d2 = dx * dx + dz * dz;
                if (d2 > radius * radius) {
                    continue;
                }
                for (int dy = -down; dy <= up; dy++) {
                    long k = key(ox + dx, oy + dy, oz + dz);
                    if (!skip.contains(k) && member.test(k)) {
                        found.add(new long[]{k, d2, dy});
                        break; // the lowest member of this column is enough as a seed
                    }
                }
            }
        }
        found.sort(Comparator.<long[]>comparingLong(a -> a[1]).thenComparingLong(a -> a[2]));
        List<Long> out = new ArrayList<>(found.size());
        for (long[] f : found) {
            out.add(f[0]);
        }
        return out;
    }

    /**
     * The nearest other trees (Wildfire, Ashen Grove): up to {@code maxTrees} trees whose seeds lie in the box, each at
     * most {@code maxTreeSize} logs, all trees together at most {@code maxBlocks}. {@code visited} should already hold the
     * player's first tree so it is not counted again.
     */
    static List<List<Long>> trees(int ox, int oy, int oz, int radius, int down, int up, int maxTrees, int maxTreeSize, int maxBlocks,
                                  LongPredicate member, Set<Long> visited) {
        List<List<Long>> out = new ArrayList<>();
        int budget = maxBlocks;
        for (long seed : seeds(ox, oy, oz, radius, down, up, member, visited)) {
            if (out.size() >= maxTrees || budget <= 0) {
                break;
            }
            if (visited.contains(seed)) {
                continue;
            }
            List<Long> tree = tree(seed, Math.min(maxTreeSize, budget), member, visited);
            if (!tree.isEmpty()) {
                out.add(tree);
                budget -= tree.size();
            }
        }
        return out;
    }
}
