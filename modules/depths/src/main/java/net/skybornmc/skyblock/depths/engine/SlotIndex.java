package net.skybornmc.skyblock.depths.engine;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.Arrays;

/**
 * Lazy per-chunk index of the slot positions of one palette region (DV2-SPIKE): the blocks in the region box whose
 * real block is the palette's base block (e.g. coal ore). A chunk is scanned the first time a player at a higher tier
 * needs it (chunk send, swap, a click) and then kept until a builder changes a block there or the region is reloaded.
 * Keys are {@link BlockKeys#block} values, sorted, so {@link #contains} is a binary search.
 */
public final class SlotIndex {

    /** Is the real block at this position a slot? Only called for loaded chunks. */
    @FunctionalInterface
    public interface Probe {
        boolean isSlot(int x, int y, int z);
    }

    private static final long[] NONE = new long[0];

    private final int minX;
    private final int minY;
    private final int minZ;
    private final int maxX;
    private final int maxY;
    private final int maxZ;
    private final Long2ObjectOpenHashMap<long[]> chunks = new Long2ObjectOpenHashMap<>();
    private int scans;

    public SlotIndex(Bounds box) {
        this(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
    }

    public SlotIndex(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.minX = Math.min(minX, maxX);
        this.minY = Math.min(minY, maxY);
        this.minZ = Math.min(minZ, maxZ);
        this.maxX = Math.max(minX, maxX);
        this.maxY = Math.max(minY, maxY);
        this.maxZ = Math.max(minZ, maxZ);
    }

    /** True if the chunk overlaps the region box. */
    public boolean covers(int chunkX, int chunkZ) {
        return (chunkX << 4) <= maxX && (chunkX << 4) + 15 >= minX && (chunkZ << 4) <= maxZ && (chunkZ << 4) + 15 >= minZ;
    }

    /** Chunk keys ({@link BlockKeys#chunk}) of every chunk the box overlaps. */
    public long[] chunkKeys() {
        int cx0 = minX >> 4;
        int cx1 = maxX >> 4;
        int cz0 = minZ >> 4;
        int cz1 = maxZ >> 4;
        long[] out = new long[(cx1 - cx0 + 1) * (cz1 - cz0 + 1)];
        int i = 0;
        for (int cx = cx0; cx <= cx1; cx++) {
            for (int cz = cz0; cz <= cz1; cz++) {
                out[i++] = BlockKeys.chunk(cx, cz);
            }
        }
        return out;
    }

    /** Slots in one chunk (sorted keys; empty when the chunk is outside the box). Scans the chunk on first use. */
    public long[] slots(int chunkX, int chunkZ, Probe probe) {
        if (!covers(chunkX, chunkZ)) {
            return NONE;
        }
        long chunkKey = BlockKeys.chunk(chunkX, chunkZ);
        long[] found = chunks.get(chunkKey);
        if (found == null) {
            found = scan(chunkX, chunkZ, probe);
            chunks.put(chunkKey, found);
        }
        return found;
    }

    private long[] scan(int chunkX, int chunkZ, Probe probe) {
        scans++;
        int x0 = Math.max(minX, chunkX << 4);
        int x1 = Math.min(maxX, (chunkX << 4) + 15);
        int z0 = Math.max(minZ, chunkZ << 4);
        int z1 = Math.min(maxZ, (chunkZ << 4) + 15);
        long[] buf = new long[16];
        int n = 0;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                for (int y = minY; y <= maxY; y++) {
                    if (probe.isSlot(x, y, z)) {
                        if (n == buf.length) {
                            buf = Arrays.copyOf(buf, n * 2);
                        }
                        buf[n++] = BlockKeys.block(x, y, z);
                    }
                }
            }
        }
        long[] out = n == 0 ? NONE : Arrays.copyOf(buf, n);
        Arrays.sort(out);
        return out;
    }

    /** True if {@code blockKey} is a slot (scans its chunk on first use). */
    public boolean contains(long blockKey, Probe probe) {
        int x = BlockKeys.x(blockKey);
        int y = BlockKeys.y(blockKey);
        int z = BlockKeys.z(blockKey);
        if (x < minX || x > maxX || y < minY || y > maxY || z < minZ || z > maxZ) {
            return false;
        }
        return Arrays.binarySearch(slots(x >> 4, z >> 4, probe), blockKey) >= 0;
    }

    /** True if the position lies in the box (cheap, no scan). */
    public boolean inBox(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    /** Forgets one chunk (a builder changed a block there). */
    public void invalidate(int chunkX, int chunkZ) {
        chunks.remove(BlockKeys.chunk(chunkX, chunkZ));
    }

    public void clear() {
        chunks.clear();
    }

    /** Chunks scanned so far (tests, /depths palette). */
    public int scans() {
        return scans;
    }

    /** Chunks currently indexed. */
    public int indexed() {
        return chunks.size();
    }
}
