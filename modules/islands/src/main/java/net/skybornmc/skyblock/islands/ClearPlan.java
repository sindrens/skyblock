package net.skybornmc.skyblock.islands;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Which chunks of a slot are emptied after a disband/reset, cut into small slices (pure, M2.7). The area is a square of
 * whole chunks around the island's center chunk ({@link IslandGrid#CENTER_CHUNK}): the larger of the island's own size
 * and the max island size, plus {@code extraRings} chunk rings, clamped to the slot. Each slice is a strip of up to
 * {@code chunksPerSlice} chunks in one chunk row, so it is a rectangle (one FAWE cuboid, or a short loop for the Bukkit
 * fallback) and only a few chunks are loaded at a time.
 *
 * @param slices        the slices in clearing order (row by row, west to east)
 * @param minChunkX     first chunk x of the area (absolute chunk coordinate)
 * @param minChunkZ     first chunk z of the area
 * @param widthChunks   width of the (square) area in chunks
 */
record ClearPlan(List<Slice> slices, int minChunkX, int minChunkZ, int widthChunks) {

    /** Chunks per slot side (512 / 16). */
    static final int SLOT_CHUNKS = IslandGrid.SLOT_SIZE / IslandGrid.CHUNK;

    /** One strip of chunks in a single chunk row: chunk x from {@code minChunkX} to {@code maxChunkX}, both inclusive. */
    record Slice(int minChunkX, int maxChunkX, int chunkZ) {

        int chunks() {
            return maxChunkX - minChunkX + 1;
        }

        int minBlockX() {
            return minChunkX * IslandGrid.CHUNK;
        }

        int maxBlockX() {
            return maxChunkX * IslandGrid.CHUNK + IslandGrid.CHUNK - 1;
        }

        int minBlockZ() {
            return chunkZ * IslandGrid.CHUNK;
        }

        int maxBlockZ() {
            return chunkZ * IslandGrid.CHUNK + IslandGrid.CHUNK - 1;
        }
    }

    ClearPlan {
        slices = Collections.unmodifiableList(new ArrayList<>(slices));
    }

    /**
     * The plan for the slot at grid {@code gridX}/{@code gridZ}.
     *
     * @param islandChunks   the removed island's border width in chunks (any value; non-positive = ignored)
     * @param maxChunks      the configured max island width in chunks
     * @param extraRings     chunk rings cleared outside that (0 or more)
     * @param chunksPerSlice chunks per slice (at least 1)
     */
    static ClearPlan of(int gridX, int gridZ, int islandChunks, int maxChunks, int extraRings, int chunksPerSlice) {
        int width = Math.max(Math.max(1, islandChunks), Math.max(1, maxChunks)) + 2 * Math.max(0, extraRings);
        int half = width / 2;
        int lo = Math.max(0, IslandGrid.CENTER_CHUNK - half);
        int hi = Math.min(SLOT_CHUNKS - 1, IslandGrid.CENTER_CHUNK + half);
        int baseX = gridX * SLOT_CHUNKS;
        int baseZ = gridZ * SLOT_CHUNKS;
        int per = Math.max(1, chunksPerSlice);
        List<Slice> slices = new ArrayList<>();
        for (int cz = lo; cz <= hi; cz++) {
            for (int cx = lo; cx <= hi; cx += per) {
                slices.add(new Slice(baseX + cx, baseX + Math.min(hi, cx + per - 1), baseZ + cz));
            }
        }
        return new ClearPlan(slices, baseX + lo, baseZ + lo, hi - lo + 1);
    }

    /** Number of chunks in the whole plan. */
    int chunks() {
        return widthChunks * widthChunks;
    }
}
