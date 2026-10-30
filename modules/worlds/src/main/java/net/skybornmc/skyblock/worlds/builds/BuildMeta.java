package net.skybornmc.skyblock.worlds.builds;

import net.skybornmc.skyblock.common.builds.BuildBox;
import net.skybornmc.skyblock.common.builds.BuildPoint;
import net.skybornmc.skyblock.common.builds.BuildRegion;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One build's metadata file ({@code <build>.yml} next to the {@code .schem}, BUILDER_BRIEF §3.6). Pure data.
 *
 * @param id          build id = file name without {@code .yml}
 * @param schematic   schematic file name next to the yml ({@code spawn_base_v1.schem}), or {@code null}
 * @param world       world the build belongs to, or {@code null}
 * @param origin      paste point {x, y, z} (the schematic's copy point goes here), or {@code null}
 * @param bounds      non-air extent after pasting (world coordinates), or {@code null}
 * @param playerSpawn {@code player_spawn} (the world's spawn point), or {@code null}
 * @param home        {@code home} (island schematics: home spot), or {@code null}
 * @param tier        the {@code tier} section as text values (Depths zones), empty if none
 * @param statsNonAir {@code stats.blocks_non_air}, -1 if missing
 * @param points      every marked spot (npcs, holograms, particles, ...; also player_spawn / home)
 * @param regions     every cuboid ({@code regions}, {@code portals})
 */
public record BuildMeta(String id, String schematic, String world, int[] origin, boolean pasteAir, boolean pasteBiomes,
                        BuildBox bounds, Spot playerSpawn, Spot home, Map<String, String> tier, long statsNonAir,
                        List<BuildPoint> points, List<BuildRegion> regions) {

    /** A position with facing; {@code integer} = the builder wrote block coordinates (no fractions). */
    public record Spot(double x, double y, double z, float yaw, float pitch, boolean integer) {

        /** Feet position: block coordinates are moved to the block center. */
        public double feetX() {
            return integer ? x + 0.5 : x;
        }

        public double feetZ() {
            return integer ? z + 0.5 : z;
        }
    }

    public BuildMeta {
        tier = Map.copyOf(tier);
        points = List.copyOf(points);
        regions = List.copyOf(regions);
    }

    public List<BuildPoint> points(String category) {
        return points.stream().filter(p -> p.category().equals(category)).toList();
    }

    public List<BuildRegion> regions(String category) {
        return regions.stream().filter(r -> r.category().equals(category)).toList();
    }

    public Optional<BuildRegion> region(String category, String id) {
        return regions.stream().filter(r -> r.category().equals(category) && r.id().equals(id)).findFirst();
    }

    public int originX() {
        return origin[0];
    }

    public int originY() {
        return origin[1];
    }

    public int originZ() {
        return origin[2];
    }
}
