package net.skybornmc.skyblock.depths.zones;

/** A compass direction on the x/z plane (north = -Z, south = +Z, west = -X, east = +X). */
public enum Side {
    NORTH(0, -1),
    SOUTH(0, 1),
    WEST(-1, 0),
    EAST(1, 0);

    final int dx;
    final int dz;

    Side(int dx, int dz) {
        this.dx = dx;
        this.dz = dz;
    }

    public Side opposite() {
        return switch (this) {
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
            case WEST -> EAST;
            case EAST -> WEST;
        };
    }

    /** True for west/east (moves along X). */
    public boolean alongX() {
        return dx != 0;
    }

    /** True if moving this way increases the coordinate (south, east). */
    public boolean positive() {
        return dx + dz > 0;
    }

    /** Minecraft yaw of someone facing this way (south 0, west 90, north 180, east -90). */
    public float yaw() {
        return switch (this) {
            case SOUTH -> 0f;
            case WEST -> 90f;
            case NORTH -> 180f;
            case EAST -> -90f;
        };
    }
}
