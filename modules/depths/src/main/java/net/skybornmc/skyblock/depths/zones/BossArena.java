package net.skybornmc.skyblock.depths.zones;

import java.util.Map;
import java.util.Objects;

/**
 * The world boss arena on its own island at the far north apex, reached by a portal in the last combat tier.
 *
 * @param portalZone   id of the zone holding {@code portal} (the last combat tier); entering needs access to it
 * @param portal       trigger cuboid inside {@code portalZone} that teleports to {@code spawn}
 * @param returnPortal trigger cuboid inside the arena that teleports back to {@code portalZone}'s spawn
 */
public record BossArena(String name, boolean built, Box region, Point spawn, String portalZone, Box portal, Box returnPortal,
                        Map<String, String> palette) {

    public static final String ID = "boss_arena";

    public BossArena {
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(spawn, "spawn");
        palette = Map.copyOf(palette);
    }
}
