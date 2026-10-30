package net.skybornmc.skyblock.depths.combat;

import java.util.Map;

/**
 * Harmless per-player ambient mobs of one mine/forest tier (M4.8, REVIEW B6) from {@code mobs.yml → ambient.<wing>.<tier>}.
 *
 * @param wing {@code mine} or {@code forest}
 * @param tier tier in that wing
 * @param mobs mob id → weight
 */
public record AmbientSpawns(String wing, int tier, Map<String, Integer> mobs) {

    public AmbientSpawns {
        mobs = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(mobs));
    }
}
