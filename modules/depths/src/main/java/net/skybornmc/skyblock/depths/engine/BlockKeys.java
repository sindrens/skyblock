package net.skybornmc.skyblock.depths.engine;

/**
 * Packs block and chunk coordinates into {@code long}s so per-player state lives in primitive maps (no objects per
 * block). Block layout matches vanilla's {@code BlockPos.asLong}: 26 bits x, 26 bits z, 12 bits y (y -2048..2047).
 * Chunk keys match Bukkit's {@code Chunk.getChunkKey(x, z)}.
 */
public final class BlockKeys {

    private static final int XZ_BITS = 26;
    private static final int Y_BITS = 12;
    private static final long XZ_MASK = (1L << XZ_BITS) - 1;
    private static final long Y_MASK = (1L << Y_BITS) - 1;
    private static final int X_SHIFT = Y_BITS + XZ_BITS;
    private static final int Z_SHIFT = Y_BITS;

    private BlockKeys() {
    }

    public static long block(int x, int y, int z) {
        return ((x & XZ_MASK) << X_SHIFT) | ((z & XZ_MASK) << Z_SHIFT) | (y & Y_MASK);
    }

    public static int x(long key) {
        return (int) (key >> X_SHIFT);
    }

    public static int y(long key) {
        return (int) (key << (64 - Y_BITS) >> (64 - Y_BITS));
    }

    public static int z(long key) {
        return (int) (key << (64 - X_SHIFT) >> (64 - XZ_BITS));
    }

    /** Same value as Bukkit's {@code Chunk.getChunkKey(chunkX, chunkZ)}. */
    public static long chunk(int chunkX, int chunkZ) {
        return (chunkX & 0xFFFFFFFFL) | ((chunkZ & 0xFFFFFFFFL) << 32);
    }

    /** Chunk key of the chunk holding this block key. */
    public static long chunkOf(long blockKey) {
        return chunk(x(blockKey) >> 4, z(blockKey) >> 4);
    }
}
