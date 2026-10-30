package net.skybornmc.skyblock.depths.combat;

import java.util.Map;
import java.util.random.RandomGenerator;

/** Pure spawn-cap and scheduling math of the combat and ambient spawners (M4.8). */
public final class SpawnMath {

    private SpawnMath() {
    }

    /** Mobs a zone should have alive: none while empty, else {@code base + perPlayer × players} up to {@code max}. */
    public static int target(ZoneSpawns spawns, int players) {
        if (spawns == null || players <= 0) {
            return 0;
        }
        long wanted = (long) spawns.base() + (long) spawns.perPlayer() * players;
        return (int) Math.max(0, Math.min(spawns.max(), wanted));
    }

    /**
     * How many to spawn this cycle: the missing ones, at most {@code perCycle}, and never past the global cap.
     *
     * @param target    {@link #target}
     * @param alive     mobs of the zone alive now
     * @param perCycle  most spawns per zone per cycle
     * @param globalRoom free room under the global cap (max total − alive in all zones)
     */
    public static int toSpawn(int target, int alive, int perCycle, int globalRoom) {
        int missing = target - alive;
        if (missing <= 0 || perCycle <= 0 || globalRoom <= 0) {
            return 0;
        }
        return Math.min(missing, Math.min(perCycle, globalRoom));
    }

    /** True once a zone has been empty of players ({@code emptySinceMs} &gt; 0) for at least {@code delayMs}: its mobs go. */
    public static boolean despawnDue(long emptySinceMs, long nowMs, long delayMs) {
        return emptySinceMs > 0 && nowMs - emptySinceMs >= delayMs;
    }

    /** A random key by weight (weights &gt; 0), or {@code null} for an empty map. */
    public static String pick(Map<String, Integer> weights, RandomGenerator rng) {
        long total = 0;
        for (int w : weights.values()) {
            total += Math.max(0, w);
        }
        if (total <= 0) {
            return null;
        }
        long roll = rng.nextLong(total);
        for (Map.Entry<String, Integer> e : weights.entrySet()) {
            roll -= Math.max(0, e.getValue());
            if (roll < 0) {
                return e.getKey();
            }
        }
        return null;
    }

    /** Ambient mobs to add for one player: missing up to {@code perPlayer}, never past the global room. */
    public static int ambientToSpawn(int perPlayer, int alive, int globalRoom) {
        return Math.max(0, Math.min(perPlayer - alive, globalRoom));
    }

    /** True if a squared distance is inside {@code [min, max]} blocks. */
    public static boolean inRing(double distSq, double min, double max) {
        return distSq >= min * min && distSq <= max * max;
    }
}
