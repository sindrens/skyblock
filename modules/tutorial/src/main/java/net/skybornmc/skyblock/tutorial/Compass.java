package net.skybornmc.skyblock.tutorial;

/**
 * Boss bar compass math (TUTORIAL.md §5 "Compass rule"): where a target is compared to the way the player faces.
 * Minecraft yaw: 0 = south (+z), 90 = west (-x), growing clockwise seen from above. Pure, unit-tested.
 */
final class Compass {

    enum Direction { AHEAD, LEFT, RIGHT, BEHIND, HERE }

    private Compass() {
    }

    /**
     * @param yaw         the player's yaw in degrees
     * @param dx          target x minus player x
     * @param dz          target z minus player z
     * @param ahead       half-angle counted as straight ahead (30)
     * @param behind      from this angle the target is behind (135)
     * @param hereRadius  closer than this (horizontal blocks) = {@link Direction#HERE}
     */
    static Direction direction(double yaw, double dx, double dz, double ahead, double behind, double hereRadius) {
        if (dx * dx + dz * dz <= hereRadius * hereRadius) {
            return Direction.HERE;
        }
        double target = Math.toDegrees(Math.atan2(-dx, dz));
        double diff = normalize(target - yaw);
        double abs = Math.abs(diff);
        if (abs <= ahead) {
            return Direction.AHEAD;
        }
        if (abs >= behind) {
            return Direction.BEHIND;
        }
        return diff > 0 ? Direction.RIGHT : Direction.LEFT;
    }

    /** Angle in (-180, 180]. */
    static double normalize(double degrees) {
        double d = degrees % 360.0;
        if (d > 180) {
            d -= 360;
        } else if (d <= -180) {
            d += 360;
        }
        return d;
    }

    /** Boss bar fill for a distance: 1.0 at the target, falling to 0.05 at {@code start} blocks or more. */
    static float distanceProgress(double distance, double start) {
        if (start <= 0) {
            return 1f;
        }
        double p = 1.0 - Math.min(1.0, Math.max(0.0, distance / start));
        return (float) Math.max(0.05, p);
    }
}
