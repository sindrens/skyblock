package net.skybornmc.skyblock.npcs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PERF4 ("npcs:look"): the head-turn task asked the world for nearby players once per NPC (an entity-section query and
 * a fresh collection each) and built two eye {@code Location}s per pair. Now the players of a world are put into a flat
 * grid once per run (cells of {@code look-radius}) and each NPC only looks at the 3x3 cells around it. Pure,
 * unit-tested. Main thread.
 *
 * @param <T> the item (a player)
 */
final class NearestGrid<T> {

    private record Point<T>(T item, double x, double y, double z) {
    }

    private final double cell;
    private final Map<Long, List<Point<T>>> cells = new HashMap<>();

    NearestGrid(double cellSize) {
        this.cell = Math.max(1.0, cellSize);
    }

    void add(T item, double x, double y, double z) {
        cells.computeIfAbsent(key(cellOf(x), cellOf(z)), k -> new ArrayList<>(2)).add(new Point<>(item, x, y, z));
    }

    boolean isEmpty() {
        return cells.isEmpty();
    }

    /**
     * The item nearest to {@code (x, y, z)} within {@code radius} (inclusive, 3D), or null. Same test as the old loop
     * ({@code d <= best}, starting at {@code radius²}); which of two equally near items wins was never defined.
     */
    T nearest(double x, double y, double z, double radius) {
        if (cells.isEmpty()) {
            return null;
        }
        int reach = (int) Math.ceil(radius / cell);
        int cx = cellOf(x);
        int cz = cellOf(z);
        double best = radius * radius;
        T found = null;
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                List<Point<T>> here = cells.get(key(cx + dx, cz + dz));
                if (here == null) {
                    continue;
                }
                for (Point<T> p : here) {
                    double ddx = p.x - x;
                    double ddy = p.y - y;
                    double ddz = p.z - z;
                    double d = ddx * ddx + ddy * ddy + ddz * ddz;
                    if (d <= best) {
                        best = d;
                        found = p.item;
                    }
                }
            }
        }
        return found;
    }

    private int cellOf(double v) {
        return (int) Math.floor(v / cell);
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }
}
