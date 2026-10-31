package net.skybornmc.skyblock.depths.combat;

/**
 * The ring a mini-boss is leashed to (M8.2, plan: "outside it, the mini-boss walks back and heals 5%/s until it is
 * inside"). The ring is a circle around its home (where it spawned), {@code radius} blocks wide and {@code height}
 * blocks up or down. Pulled outside, it turns back ({@link Action#RETURN}): it drops its target, walks home and heals,
 * and abilities wait. It keeps walking until it is back in the inner half of the ring, so a player can't keep it
 * dancing on the edge. Still outside after {@code teleportAfterMs} (stuck, knocked away) it is put back home
 * ({@link Action#TELEPORT}). Pure, time passed in; main thread only.
 */
public final class MiniBossLeash {

    /** What the mini-boss should do after a check. */
    public enum Action {
        /** In its ring: fight as usual. */
        INSIDE,
        /** Outside (or still on its way back): walk home and heal. */
        RETURN,
        /** Outside for too long: put it back home now. */
        TELEPORT
    }

    private final double homeX;
    private final double homeY;
    private final double homeZ;
    private final double radius;
    private final double height;
    private long outsideSince = -1;

    /**
     * @param radius ring radius in blocks (above 0)
     * @param height blocks above or below home that still count as inside
     */
    public MiniBossLeash(double homeX, double homeY, double homeZ, double radius, double height) {
        this.homeX = homeX;
        this.homeY = homeY;
        this.homeZ = homeZ;
        this.radius = Math.max(1, radius);
        this.height = Math.max(1, height);
    }

    public double homeX() {
        return homeX;
    }

    public double homeY() {
        return homeY;
    }

    public double homeZ() {
        return homeZ;
    }

    public double radius() {
        return radius;
    }

    /** True while it is walking back home. */
    public boolean returning() {
        return outsideSince >= 0;
    }

    /** True if the spot is inside the ring. */
    public boolean inside(double x, double y, double z) {
        return within(x, y, z, radius);
    }

    private boolean within(double x, double y, double z, double r) {
        double dx = x - homeX;
        double dz = z - homeZ;
        return dx * dx + dz * dz <= r * r && Math.abs(y - homeY) <= height;
    }

    /**
     * One leash check.
     *
     * @param nowMs           current time in milliseconds
     * @param teleportAfterMs put it back home after this long outside (0 or less = never)
     */
    public Action check(double x, double y, double z, long nowMs, long teleportAfterMs) {
        boolean back = returning() ? within(x, y, z, radius / 2.0) : within(x, y, z, radius);
        if (back) {
            outsideSince = -1;
            return Action.INSIDE;
        }
        if (outsideSince < 0) {
            outsideSince = nowMs;
        }
        if (teleportAfterMs > 0 && nowMs - outsideSince >= teleportAfterMs) {
            outsideSince = -1;
            return Action.TELEPORT;
        }
        return Action.RETURN;
    }

    /**
     * Health to add for one check while it walks back: {@code percentPerSecond} of its max health per second, for
     * {@code intervalTicks} ticks, never above its max.
     */
    public static double heal(double health, double maxHealth, double percentPerSecond, int intervalTicks) {
        if (!(maxHealth > 0) || !(percentPerSecond > 0) || intervalTicks <= 0 || !(health > 0) || health >= maxHealth) {
            return 0;
        }
        double amount = maxHealth * percentPerSecond / 100.0 * intervalTicks / 20.0;
        return Math.min(maxHealth - health, amount);
    }
}
