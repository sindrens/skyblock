package net.skybornmc.skyblock.depths.combat;

import java.util.Map;

/**
 * Spawning of one combat tier (M4.8) from {@code mobs.yml → combat-zones.<tier>}: which mobs (weighted), how many and
 * the mini-boss placeholder timer. Target alive = {@code base + perPlayer × players}, at most {@code max}; nothing
 * spawns while the zone is empty.
 *
 * @param tier            combat tier
 * @param mobs            mob id → weight (copied, insertion order)
 * @param base            mobs with one player present (before per-player)
 * @param perPlayer       extra mobs per player in the zone
 * @param max             spawn cap of the zone
 * @param miniBoss        mini-boss mob id, {@code null} = none
 * @param miniBossMinutes minutes between mini-bosses (counted from the last one's death or removal)
 */
public record ZoneSpawns(int tier, Map<String, Integer> mobs, int base, int perPlayer, int max, String miniBoss,
                         int miniBossMinutes) {

    public ZoneSpawns {
        mobs = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(mobs));
    }

    /** True if a mini-boss is configured. */
    public boolean hasMiniBoss() {
        return miniBoss != null && miniBossMinutes > 0;
    }
}
