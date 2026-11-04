package net.skybornmc.skyblock.guide;

import net.skybornmc.skyblock.common.island.IslandService;

/** Pure rules of the island guide (unit-tested). */
final class GuideRules {

    /** Ticks after an arrival when the guide is checked (the arrival chunks may still be loading at the first one). */
    static final long[] ARRIVAL_CHECK_TICKS = {1L, 10L, 40L};

    private GuideRules() {
    }

    /** Filled dots of a {@code length}-dot progress bar at {@code percent} (0-100, rounded down, clamped). */
    static int filled(int percent, int length) {
        if (length <= 0) {
            return 0;
        }
        int p = Math.max(0, Math.min(100, percent));
        return p * length / 100;
    }

    /** A placeholder's percent text ({@code "46"}, {@code "46.5"}, {@code "46%"}) as a whole number; 0 when not a number. */
    static int percent(String text) {
        if (text == null) {
            return 0;
        }
        String t = text.strip().replace("%", "");
        try {
            return (int) Math.max(0, Math.min(100, Math.floor(Double.parseDouble(t))));
        } catch (NumberFormatException e) {
            return 0;
        }
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
