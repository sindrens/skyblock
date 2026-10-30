package net.skybornmc.skyblock.common.builds;

/** An axis-aligned block box from build metadata (inclusive corners, normalized so min ≤ max). World coordinates. */
public record BuildBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    public BuildBox {
        int x0 = Math.min(minX, maxX);
        int x1 = Math.max(minX, maxX);
        int y0 = Math.min(minY, maxY);
        int y1 = Math.max(minY, maxY);
        int z0 = Math.min(minZ, maxZ);
        int z1 = Math.max(minZ, maxZ);
        minX = x0;
        maxX = x1;
        minY = y0;
        maxY = y1;
        minZ = z0;
        maxZ = z1;
    }

    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public boolean contains(double x, double y, double z) {
        return contains((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
    }

    /** True if {@code other} lies completely inside this box. */
    public boolean contains(BuildBox other) {
        return other.minX >= minX && other.maxX <= maxX && other.minY >= minY && other.maxY <= maxY
                && other.minZ >= minZ && other.maxZ <= maxZ;
    }

    public long volume() {
        return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }

    /** Chunk x range covered (inclusive). */
    public int minChunkX() {
        return minX >> 4;
    }

    public int maxChunkX() {
        return maxX >> 4;
    }

    public int minChunkZ() {
        return minZ >> 4;
    }

    public int maxChunkZ() {
        return maxZ >> 4;
    }

    /** Number of chunks the box touches. */
    public int chunkCount() {
        return (maxChunkX() - minChunkX() + 1) * (maxChunkZ() - minChunkZ() + 1);
    }

    @Override
    public String toString() {
        return "(" + minX + ", " + minY + ", " + minZ + ") to (" + maxX + ", " + maxY + ", " + maxZ + ")";
    }
}
