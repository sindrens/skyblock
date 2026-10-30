package net.skybornmc.skyblock.worlds.portals;

import net.skybornmc.skyblock.common.builds.BuildBox;

/**
 * One build portal from {@code build-points.yml} (category {@code portals}): a trigger cuboid players step into.
 *
 * @param build    build id ({@code spawn_gatehouse_v1})
 * @param id       portal id inside the build ({@code to_island})
 * @param world    world of the trigger
 * @param box      the trigger cuboid (inclusive block coordinates)
 * @param target   the builder's {@code target} ({@code spawn}, {@code depths}, {@code island}...), empty if none
 * @param open     false when the builder wrote {@code status: closed}
 * @param landing  where players arrive at this portal when they come through its counterpart (in front of it), or
 *                 {@code null}: {@code landing_tp} when given, else {@code landing} (block → block center)
 * @param particle the portal's particle spot from the same build, or {@code null}
 */
public record Portal(String build, String id, String world, BuildBox box, String target, boolean open, Pos landing,
                     Particle particle) {

    /** A feet position with facing (yaw: 0 south, 90 west, 180 north, -90 east). */
    public record Pos(double x, double y, double z, float yaw, float pitch) {
    }

    /**
     * A particle spot ({@code particles} entry of the build).
     *
     * @param type    vanilla particle id ({@code happy_villager}, {@code dust}...)
     * @param color   {@code #RRGGBB} for dust, or {@code null}
     * @param enabled false when the builder wrote {@code enabled: false}
     */
    public record Particle(String id, double x, double y, double z, String type, String color, boolean enabled) {
    }

    /** {@code build:id}, unique server-wide. */
    public String key() {
        return build + ":" + id;
    }

    public double centerX() {
        return (box.minX() + box.maxX() + 1) / 2.0;
    }

    public double centerZ() {
        return (box.minZ() + box.maxZ() + 1) / 2.0;
    }
}
