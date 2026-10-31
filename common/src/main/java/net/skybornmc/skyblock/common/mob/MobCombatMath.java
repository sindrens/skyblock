package net.skybornmc.skyblock.common.mob;

import java.util.ArrayList;
import java.util.List;

/**
 * Small pure rules of mob fights (M8.1): armor and shield on player hits, the melee attack cooldown, the enrage
 * threshold and the shapes of telegraph rings and lanes. These only ever apply to hits between a player and a custom
 * mob, never to player-versus-player damage.
 */
public final class MobCombatMath {

    private MobCombatMath() {
    }

    /** Damage a player's hit deals to a mob with {@code armor}% armor, and a shield taking {@code shieldReduction} less. */
    public static double playerHitOnMob(double damage, double armor, boolean shielded, double shieldReduction) {
        if (!(damage > 0)) {
            return 0;
        }
        double a = Double.isNaN(armor) ? 0 : Math.max(0, Math.min(MobStats.MAX_ARMOR, armor));
        double out = damage * (1.0 - a / 100.0);
        if (shielded) {
            double r = Double.isNaN(shieldReduction) ? 0 : Math.max(0, Math.min(0.95, shieldReduction));
            out *= 1.0 - r;
        }
        return out;
    }

    /** True if a mob may melee again: {@code cooldownTicks <= 0} (vanilla) or enough ticks since its last melee hit. */
    public static boolean meleeAllowed(long lastHitTick, long now, int cooldownTicks) {
        return cooldownTicks <= 0 || lastHitTick == Long.MIN_VALUE || now - lastHitTick >= cooldownTicks;
    }

    /** Attack cooldown after enrage's {@code factor} (0.6 = 40% faster), at least 1 tick when a cooldown is set. */
    public static int scaledCooldown(int cooldownTicks, double factor) {
        if (cooldownTicks <= 0) {
            return 0;
        }
        double f = Double.isNaN(factor) || factor <= 0 ? 1 : Math.min(1, factor);
        return (int) Math.max(1, Math.round(cooldownTicks * f));
    }

    /** True once the mob's health after a hit is at or below {@code below} of its max (and it hasn't raged yet). */
    public static boolean enrageDue(double healthAfter, double maxHealth, double below, boolean already) {
        return !already && maxHealth > 0 && healthAfter > 0 && healthAfter / maxHealth <= below;
    }

    // ------------------------------------------------------------------ shapes (block offsets around a center)

    /** Block offsets {@code {dx, dz}} of a ring outline at {@code radius} (one block wide, no duplicates). */
    public static List<int[]> ring(double radius) {
        List<int[]> out = new ArrayList<>();
        double r = Math.max(0.5, radius);
        int max = (int) Math.ceil(r + 0.5);
        double inner = (r - 0.5) * (r - 0.5);
        double outer = (r + 0.5) * (r + 0.5);
        for (int dx = -max; dx <= max; dx++) {
            for (int dz = -max; dz <= max; dz++) {
                double d = dx * dx + dz * dz;
                if (d >= inner && d < outer) {
                    out.add(new int[]{dx, dz});
                }
            }
        }
        return out;
    }

    /** Block offsets {@code {dx, dz}} along a lane from the center in direction (dirX, dirZ), {@code length} blocks. */
    public static List<int[]> lane(double dirX, double dirZ, int length) {
        List<int[]> out = new ArrayList<>();
        double len = Math.hypot(dirX, dirZ);
        if (!(len > 1e-9) || length <= 0) {
            return out;
        }
        double ux = dirX / len;
        double uz = dirZ / len;
        int lastX = Integer.MIN_VALUE;
        int lastZ = Integer.MIN_VALUE;
        for (int step = 1; step <= length * 2; step++) {
            double t = step * 0.5;
            int x = (int) Math.round(ux * t);
            int z = (int) Math.round(uz * t);
            if (x != lastX || z != lastZ) {
                out.add(new int[]{x, z});
                lastX = x;
                lastZ = z;
            }
        }
        return out;
    }

    /** True if (dx, dz) from the center is inside a circle of {@code radius} (horizontal). */
    public static boolean inside(double dx, double dz, double radius) {
        return dx * dx + dz * dz <= radius * radius;
    }

    /** Horizontal distance of point P to the segment A-B. */
    public static double distanceToSegment(double px, double pz, double ax, double az, double bx, double bz) {
        double vx = bx - ax;
        double vz = bz - az;
        double len2 = vx * vx + vz * vz;
        double t = len2 <= 1e-12 ? 0 : Math.max(0, Math.min(1, ((px - ax) * vx + (pz - az) * vz) / len2));
        double cx = ax + t * vx - px;
        double cz = az + t * vz - pz;
        return Math.sqrt(cx * cx + cz * cz);
    }

    /** Yaw offsets in degrees of {@code count} projectiles spread evenly over {@code spread} degrees (centered on 0). */
    public static double[] fan(int count, double spread) {
        int n = Math.max(1, count);
        double[] out = new double[n];
        if (n == 1) {
            return out;
        }
        double step = spread / (n - 1);
        for (int i = 0; i < n; i++) {
            out[i] = -spread / 2.0 + step * i;
        }
        return out;
    }
}
