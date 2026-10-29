package net.skybornmc.skyblock.islands;

/**
 * "Which island is here?" in O(1) without the database, hashing or allocation (pure, main thread only). Slots are
 * handed out in a spiral from grid 0,0, so the used grid is a square around the origin: islands sit in a flat array
 * indexed by grid x/z that grows when a slot outside it is added. A block's grid cell is {@code block >> 9}
 * (512 = 2^9; the arithmetic shift floors negative coordinates like {@code Math.floorDiv}).
 *
 * <p>Protection rules built on it: a change from block S to block T inside the islands world is allowed only if both
 * are inside the border of the same island ({@link #sameIsland}). Everything outside a border (the void between slots
 * and the unused part of a slot) can't be changed by anything.</p>
 */
public final class Territory {

    private static final int SHIFT = 9;
    static {
        if (1 << SHIFT != IslandGrid.SLOT_SIZE) {
            throw new IllegalStateException("Territory.SHIFT must match IslandGrid.SLOT_SIZE");
        }
    }

    /** Half-width of the array in grid cells: covers grid -radius..radius on both axes. */
    private int radius;
    private int width;
    private Island[] cells;
    private int count;

    public Territory() {
        resize(4);
    }

    private void resize(int newRadius) {
        Island[] old = cells;
        int oldRadius = radius;
        int oldWidth = width;
        radius = newRadius;
        width = 2 * newRadius + 1;
        cells = new Island[width * width];
        if (old != null) {
            for (int i = 0; i < old.length; i++) {
                if (old[i] != null) {
                    int gx = i / oldWidth - oldRadius;
                    int gz = i % oldWidth - oldRadius;
                    cells[(gx + radius) * width + (gz + radius)] = old[i];
                }
            }
        }
    }

    /** Grid cell of a block coordinate (same as {@link IslandGrid#cell}). */
    public static int cell(int block) {
        return block >> SHIFT;
    }

    /** Adds or replaces the island of its grid cell. */
    public void put(Island island) {
        int needed = Math.max(Math.abs(island.gridX()), Math.abs(island.gridZ()));
        if (needed > radius) {
            resize(Math.max(needed, radius * 2));
        }
        int index = (island.gridX() + radius) * width + (island.gridZ() + radius);
        if (cells[index] == null) {
            count++;
        }
        cells[index] = island;
    }

    /** Removes the island of a grid cell if it is {@code island}. */
    public void remove(Island island) {
        Island here = inCell(island.gridX(), island.gridZ());
        if (here == island) {
            cells[(island.gridX() + radius) * width + (island.gridZ() + radius)] = null;
            count--;
        }
    }

    public void clear() {
        java.util.Arrays.fill(cells, null);
        count = 0;
    }

    public int size() {
        return count;
    }

    /** Island of grid cell gx/gz, or null. */
    public Island inCell(int gx, int gz) {
        int ix = gx + radius;
        int iz = gz + radius;
        if (ix < 0 || iz < 0 || ix >= width || iz >= width) {
            return null;
        }
        return cells[ix * width + iz];
    }

    /** Island whose slot contains block x/z (even outside its border), or null. */
    public Island inSlot(int x, int z) {
        return inCell(x >> SHIFT, z >> SHIFT);
    }

    /** Island whose border contains block x/z, or null. */
    public Island at(int x, int z) {
        Island island = inCell(x >> SHIFT, z >> SHIFT);
        return island != null && island.contains(x, z) ? island : null;
    }

    /** True if both blocks are inside the border of one and the same island. */
    public boolean sameIsland(int sx, int sz, int tx, int tz) {
        int cx = tx >> SHIFT;
        int cz = tz >> SHIFT;
        if (sx >> SHIFT != cx || sz >> SHIFT != cz) {
            return false;
        }
        Island island = inCell(cx, cz);
        return island != null && island.contains(sx, sz) && island.contains(tx, tz);
    }

    /** True if block x/z is inside the border of {@code island}. */
    public static boolean inside(Island island, int x, int z) {
        return island != null && island.contains(x, z);
    }

    /**
     * Clamps a position (x or z, as a double) into the island's border with {@code margin} blocks to spare: where an
     * entity pushed outside is put back. Values already inside are returned unchanged.
     */
    public static double clamp(double value, int min, int max, double margin) {
        double low = min + margin;
        double high = max + 1 - margin;
        if (value < low) {
            return low;
        }
        if (value > high) {
            return high;
        }
        return value;
    }
}
