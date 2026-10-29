package net.skybornmc.skyblock.islands;

import net.skybornmc.skyblock.common.island.IslandService.Bounds;

/**
 * The island grid (pure math, no Bukkit). The islands world is cut into {@value #SLOT_SIZE} × {@value #SLOT_SIZE}
 * slots, exactly one region file each (WORLD.md §3), so resetting an island is one region-file job. Slots are numbered
 * in a square spiral from grid 0,0 (slot id = spiral index).
 *
 * <p>Islands are aligned to chunks (DESIGN §3.1). A slot is 32 chunks wide, so an odd-width island can't sit exactly in
 * the middle: its center chunk is chunk {@value #CENTER_CHUNK} of the slot (blocks 256-271 from the slot corner) and the
 * center point is 264. Start 3×3 chunks = blocks 240-287; max 15×15 = blocks 144-383; the biggest possible (31×31)
 * still fits in the slot.</p>
 */
public final class IslandGrid {

    /** Blocks per slot side: one region file (512 = 32 chunks). Never configurable: islands are stored by grid cell. */
    public static final int SLOT_SIZE = 512;
    public static final int CHUNK = 16;
    /** Index (0-31) of the island's center chunk inside its slot. */
    public static final int CENTER_CHUNK = 16;
    /** Largest odd width (in chunks) that fits in a slot around the center chunk. */
    public static final int MAX_POSSIBLE_CHUNKS = 31;

    private IslandGrid() {
    }

    /** Grid coordinates {x, z} of spiral index {@code index} (0 = 0,0, then ring 1 with 8 slots, ring 2 with 16, …). */
    public static int[] spiral(int index) {
        if (index < 0) {
            throw new IllegalArgumentException("Spiral index must be >= 0: " + index);
        }
        if (index == 0) {
            return new int[]{0, 0};
        }
        int ring = ring(index);
        int side = 2 * ring;
        int last = (2 * ring + 1) * (2 * ring + 1) - 1; // last index of this ring
        int offset = last - index;                      // 0 .. 8*ring-1, walking the ring backwards
        if (offset < side) {
            return new int[]{ring - offset, -ring};      // top edge, right to left
        }
        offset -= side;
        if (offset < side) {
            return new int[]{-ring, -ring + offset};     // left edge, top to bottom
        }
        offset -= side;
        if (offset < side) {
            return new int[]{-ring + offset, ring};      // bottom edge, left to right
        }
        offset -= side;
        return new int[]{ring, ring - offset};           // right edge, bottom to top
    }

    /** Ring number of a spiral index: the smallest r with (2r+1)² > index. */
    static int ring(int index) {
        int r = (int) Math.ceil((Math.sqrt(index + 1.0) - 1) / 2);
        while ((2L * r + 1) * (2L * r + 1) <= index) {
            r++;
        }
        while (r > 0 && (2L * r - 1) * (2L * r - 1) > index) {
            r--;
        }
        return r;
    }

    /** Grid cell of a block coordinate. */
    public static int cell(int block) {
        return Math.floorDiv(block, SLOT_SIZE);
    }

    /** One long key for a grid cell (for maps). */
    public static long key(int gridX, int gridZ) {
        return ((long) gridX << 32) | (gridZ & 0xFFFFFFFFL);
    }

    /** First block (x or z) of a grid cell. */
    public static int slotMin(int grid) {
        return grid * SLOT_SIZE;
    }

    /**
     * Island center block (x or z) of a grid cell: the starter island is pasted with its origin here. The exact middle
     * of the island (and of its border) is this block's north-west edge, e.g. 264.0.
     */
    public static int centerBlock(int grid) {
        return slotMin(grid) + CENTER_CHUNK * CHUNK + CHUNK / 2;
    }

    public static Bounds slotBounds(String world, int gridX, int gridZ) {
        return new Bounds(world, slotMin(gridX), slotMin(gridZ), slotMin(gridX) + SLOT_SIZE - 1, slotMin(gridZ) + SLOT_SIZE - 1);
    }

    /** Playable area of an island {@code sizeChunks} wide (odd) in cell gridX/gridZ. */
    public static Bounds islandBounds(String world, int gridX, int gridZ, int sizeChunks) {
        if (!validSize(sizeChunks)) {
            throw new IllegalArgumentException("Island size must be odd and between 1 and " + MAX_POSSIBLE_CHUNKS + ": " + sizeChunks);
        }
        int half = sizeChunks / 2;
        int minX = slotMin(gridX) + (CENTER_CHUNK - half) * CHUNK;
        int minZ = slotMin(gridZ) + (CENTER_CHUNK - half) * CHUNK;
        int width = sizeChunks * CHUNK;
        return new Bounds(world, minX, minZ, minX + width - 1, minZ + width - 1);
    }

    /** Odd, at least 1, and fits in a slot. */
    public static boolean validSize(int sizeChunks) {
        return sizeChunks >= 1 && sizeChunks <= MAX_POSSIBLE_CHUNKS && sizeChunks % 2 == 1;
    }

    /** The next size after {@code sizeChunks}: one chunk ring more on every side (+2), capped at {@code max}. */
    public static int grow(int sizeChunks, int max) {
        return Math.min(max, sizeChunks + 2);
    }
}
