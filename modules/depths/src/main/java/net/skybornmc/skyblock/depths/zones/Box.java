package net.skybornmc.skyblock.depths.zones;

/**
 * An axis-aligned box of blocks (inclusive corners, normalized so min ≤ max on every axis). World-less: all zones of
 * one {@link ZoneSet} live in the same world.
 */
public record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    public Box {
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

    /** Box from two corners in any order. */
    public static Box of(int x1, int y1, int z1, int x2, int y2, int z2) {
        return new Box(x1, y1, z1, x2, y2, z2);
    }

    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    /** True if the column (x, z) is inside the box's footprint (any y). */
    public boolean containsColumn(int x, int z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    public boolean contains(double x, double y, double z) {
        return contains((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
    }

    /** True if {@code other} lies completely inside this box. */
    public boolean contains(Box other) {
        return other.minX >= minX && other.maxX <= maxX && other.minY >= minY && other.maxY <= maxY
                && other.minZ >= minZ && other.maxZ <= maxZ;
    }

    /** True if the two boxes share at least one block. */
    public boolean intersects(Box o) {
        return minX <= o.maxX && maxX >= o.minX && minY <= o.maxY && maxY >= o.minY && minZ <= o.maxZ && maxZ >= o.minZ;
    }

    /** True if the footprints (x/z, ignoring y) share at least one column. */
    public boolean intersectsColumns(Box o) {
        return minX <= o.maxX && maxX >= o.minX && minZ <= o.maxZ && maxZ >= o.minZ;
    }

    /** True if the box touches the outer edge (wall line) of {@code zone} and stays within its footprint. */
    public boolean onEdgeOf(Box zone) {
        if (!zone.intersectsColumns(this) || minX < zone.minX || maxX > zone.maxX || minZ < zone.minZ || maxZ > zone.maxZ) {
            return false;
        }
        return minX == zone.minX || maxX == zone.maxX || minZ == zone.minZ || maxZ == zone.maxZ;
    }

    public int sizeX() {
        return maxX - minX + 1;
    }

    public int sizeY() {
        return maxY - minY + 1;
    }

    public int sizeZ() {
        return maxZ - minZ + 1;
    }

    public long volume() {
        return (long) sizeX() * sizeY() * sizeZ();
    }

    /** The box grown by {@code n} blocks on the horizontal axes only. */
    public Box growHorizontal(int n) {
        return new Box(minX - n, minY, minZ - n, maxX + n, maxY, maxZ + n);
    }

    /** "x y z" corners for messages and YAML. */
    public String corner1() {
        return minX + ", " + minY + ", " + minZ;
    }

    public String corner2() {
        return maxX + ", " + maxY + ", " + maxZ;
    }

    @Override
    public String toString() {
        return "(" + corner1() + ") to (" + corner2() + ")";
    }
}
