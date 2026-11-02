package net.skybornmc.skyblock.guide;

import net.skybornmc.skyblock.common.island.IslandService;

/** Pure rules of the island guide (unit-tested). */
final class GuideRules {

    private GuideRules() {
    }

    /** The default spot: the island home plus the configured offset, facing back toward the home. */
    static GuideStore.Spot defaultSpot(String world, double homeX, double homeY, double homeZ, double ox, double oy, double oz) {
        double x = Math.floor(homeX + ox) + 0.5;
        double z = Math.floor(homeZ + oz) + 0.5;
        float yaw = yawToward(x, z, homeX, homeZ);
        return new GuideStore.Spot(world, x, Math.floor(homeY + oy), z, yaw);
    }

    /** Minecraft yaw (0 = south, 90 = west) looking from (fromX, fromZ) to (toX, toZ). */
    static float yawToward(double fromX, double fromZ, double toX, double toZ) {
        double dx = toX - fromX;
        double dz = toZ - fromZ;
        if (Math.abs(dx) < 1e-9 && Math.abs(dz) < 1e-9) {
            return 0f;
        }
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    /** True when the leader or an admin may move the guide. */
    static boolean mayMove(IslandService.Role role) {
        return role == IslandService.Role.LEADER || role == IslandService.Role.ADMIN;
    }

    /** True when the block column is inside the island border. */
    static boolean inside(IslandService.Bounds bounds, double x, double z) {
        return bounds != null && bounds.contains((int) Math.floor(x), (int) Math.floor(z));
    }

    /** Chunk key of a block position (same packing as Bukkit's {@code Chunk#getChunkKey}). */
    static long chunkKey(double x, double z) {
        long cx = ((long) Math.floor(x)) >> 4;
        long cz = ((long) Math.floor(z)) >> 4;
        return (cx & 0xffffffffL) | ((cz & 0xffffffffL) << 32);
    }
}
