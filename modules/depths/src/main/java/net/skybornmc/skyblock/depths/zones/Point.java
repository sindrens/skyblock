package net.skybornmc.skyblock.depths.zones;

/** A position with facing (spawn points, teleport targets). Yaw: 0 south, 90 west, 180 north, -90 east. */
public record Point(double x, double y, double z, float yaw, float pitch) {

    public int blockX() {
        return (int) Math.floor(x);
    }

    public int blockY() {
        return (int) Math.floor(y);
    }

    public int blockZ() {
        return (int) Math.floor(z);
    }
}
