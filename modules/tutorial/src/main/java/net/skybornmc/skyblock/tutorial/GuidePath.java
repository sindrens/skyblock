package net.skybornmc.skyblock.tutorial;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * The walkable route of the guide trail (A3-R0-02, TUT-FIX2): a bounded A* search on the block grid from where the player
 * stands to the step's target, over blocks a player can really walk on (2 blocks of head room, steps of one block up,
 * drops of up to three, no corner cutting, no liquids). Unknown blocks (unloaded chunks) are never walked on, so the
 * search never loads a chunk. Pure and unit-testable: the world is a {@link Terrain}; every block is read at most once
 * per search. Runs once per step change or when the player leaves the trail, never per tick (PERFORMANCE P-21).
 */
final class GuidePath {

    /** How a block behaves for walking. */
    static final int UNKNOWN = -1;
    /** Nothing to stand in or on (air, grass, flowers, open doors...). */
    static final int OPEN = 0;
    /** A full-height block to stand on. */
    static final int SOLID = 1;
    /** A low block (slab, carpet, snow layer): you stand in its cell, half a block up at most. */
    static final int LOW = 2;
    /** Never walk in or on it: liquids, fences and walls (too high to step on), lava. */
    static final int BLOCKED = 3;

    /** The world as the search sees it. */
    interface Terrain {
        int kind(int x, int y, int z);
    }

    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
    private static final int[] RISE = {0, 1, -1, -2, -3};
    private static final double DIAGONAL = Math.sqrt(2);

    private GuidePath() {
    }

    private static final class Node implements Comparable<Node> {
        final int x;
        final int y;
        final int z;
        final double g;
        final double f;
        final Node parent;

        Node(int x, int y, int z, double g, double f, Node parent) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.g = g;
            this.f = f;
            this.parent = parent;
        }

        @Override
        public int compareTo(Node o) {
            return Double.compare(f, o.f);
        }
    }

    /** Reads every block once per search. */
    private static final class Cache {
        final Terrain terrain;
        final Map<Long, Integer> kinds = new HashMap<>();

        Cache(Terrain terrain) {
            this.terrain = terrain;
        }

        int kind(int x, int y, int z) {
            long key = key(x, y, z);
            Integer k = kinds.get(key);
            if (k == null) {
                k = terrain.kind(x, y, z);
                kinds.put(key, k);
            }
            return k;
        }
    }

    static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | ((y + 2048) & 0xFFF);
    }

    /** True when a player can stand with their feet in this block. */
    static boolean walkable(Terrain t, int x, int y, int z) {
        return walkable(new Cache(t), x, y, z);
    }

    private static boolean walkable(Cache c, int x, int y, int z) {
        int feet = c.kind(x, y, z);
        if (feet == OPEN) {
            return c.kind(x, y - 1, z) == SOLID && c.kind(x, y + 1, z) == OPEN;
        }
        if (feet == LOW) {
            return c.kind(x, y + 1, z) == OPEN && c.kind(x, y + 2, z) == OPEN;
        }
        return false;
    }

    /** The block a player standing (or hovering a little) at {@code (x, y, z)} has under their feet, or {@code null}. */
    static int[] standing(Terrain t, double x, double y, double z) {
        Cache c = new Cache(t);
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        int by = (int) Math.floor(y);
        for (int dy = 0; dy >= -4; dy--) {
            if (walkable(c, bx, by + dy, bz)) {
                return new int[]{bx, by + dy, bz};
            }
        }
        return walkable(c, bx, by + 1, bz) ? new int[]{bx, by + 1, bz} : null;
    }

    /**
     * The route from the walkable block {@code start} to a walkable block within {@code goalRadius} (horizontal) and 3
     * blocks (vertical) of {@code goal}, or {@code null} when none is found within {@code maxNodes} visited blocks.
     */
    static Route find(int[] start, double[] goal, double goalRadius, Terrain terrain, int maxNodes) {
        if (start == null || goal == null) {
            return null;
        }
        Cache c = new Cache(terrain);
        if (!walkable(c, start[0], start[1], start[2])) {
            return null;
        }
        double r2 = goalRadius * goalRadius;
        PriorityQueue<Node> open = new PriorityQueue<>();
        Map<Long, Double> best = new HashMap<>();
        open.add(new Node(start[0], start[1], start[2], 0, h(start[0], start[1], start[2], goal), null));
        best.put(key(start[0], start[1], start[2]), 0.0);
        int visited = 0;
        while (!open.isEmpty()) {
            Node n = open.poll();
            Double known = best.get(key(n.x, n.y, n.z));
            if (known != null && known < n.g - 1e-9) {
                continue; // a better way to this block was found after this entry was queued
            }
            if (atGoal(n, goal, r2)) {
                return Route.of(trace(n, c));
            }
            if (++visited > maxNodes) {
                return null;
            }
            for (int[] d : DIRS) {
                boolean diagonal = d[0] != 0 && d[1] != 0;
                int nx = n.x + d[0];
                int nz = n.z + d[1];
                for (int rise : RISE) {
                    int ny = n.y + rise;
                    if (!walkable(c, nx, ny, nz) || !canMove(c, n, nx, ny, nz, rise, diagonal)) {
                        continue;
                    }
                    double g = n.g + (diagonal ? DIAGONAL : 1) + (rise > 0 ? 0.6 : rise < 0 ? 0.3 * -rise : 0);
                    long k = key(nx, ny, nz);
                    Double before = best.get(k);
                    if (before == null || g < before - 1e-9) {
                        best.put(k, g);
                        open.add(new Node(nx, ny, nz, g, g + h(nx, ny, nz, goal), n));
                    }
                    break; // one height per direction: the first walkable one (flat first, then up, then down)
                }
            }
        }
        return null;
    }

    /** Head room for a jump, an open fall column, and no squeezing past a corner on a diagonal. */
    private static boolean canMove(Cache c, Node n, int nx, int ny, int nz, int rise, boolean diagonal) {
        if (rise > 0 && c.kind(n.x, n.y + 2, n.z) != OPEN) {
            return false; // no room to jump up
        }
        if (rise < 0) {
            for (int y = ny + 2; y <= n.y + 1; y++) {
                if (c.kind(nx, y, nz) != OPEN) {
                    return false; // something in the way of the drop
                }
            }
        }
        if (diagonal) {
            int y = Math.max(n.y, ny);
            return passable(c, nx, y, n.z) && passable(c, n.x, y, nz);
        }
        return true;
    }

    private static boolean passable(Cache c, int x, int y, int z) {
        int k = c.kind(x, y, z);
        return (k == OPEN || k == LOW) && c.kind(x, y + 1, z) == OPEN;
    }

    private static boolean atGoal(Node n, double[] goal, double r2) {
        double dx = n.x + 0.5 - goal[0];
        double dz = n.z + 0.5 - goal[2];
        return dx * dx + dz * dz <= r2 && Math.abs(n.y - goal[1]) <= 3;
    }

    private static double h(int x, int y, int z, double[] goal) {
        double dx = Math.abs(x + 0.5 - goal[0]);
        double dz = Math.abs(z + 0.5 - goal[2]);
        double straight = Math.abs(dx - dz);
        double diag = Math.min(dx, dz);
        return straight + diag * DIAGONAL + Math.abs(y - goal[1]) * 0.3;
    }

    /** The route's points: block centres at the height of the surface you walk on (a slab counts half a block). */
    private static List<double[]> trace(Node end, Cache c) {
        List<double[]> out = new ArrayList<>();
        for (Node n = end; n != null; n = n.parent) {
            out.add(new double[]{n.x + 0.5, n.y + (c.kind(n.x, n.y, n.z) == LOW ? 0.5 : 0), n.z + 0.5});
        }
        java.util.Collections.reverse(out);
        return out;
    }
}
