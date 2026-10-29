package net.skybornmc.skyblock.depths.engine;

/** An axis-aligned box of blocks in one world (inclusive corners, min ≤ max on every axis). */
public interface Bounds {

    String world();

    int minX();

    int minY();

    int minZ();

    int maxX();

    int maxY();

    int maxZ();

    default boolean contains(int x, int y, int z) {
        return x >= minX() && x <= maxX() && y >= minY() && y <= maxY() && z >= minZ() && z <= maxZ();
    }

    /** Number of blocks inside (for the debug command and size checks). */
    default long volume() {
        return (long) (maxX() - minX() + 1) * (maxY() - minY() + 1) * (maxZ() - minZ() + 1);
    }
}
