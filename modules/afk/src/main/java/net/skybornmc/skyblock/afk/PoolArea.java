package net.skybornmc.skyblock.afk;

import net.skybornmc.skyblock.common.builds.BuildBox;
import net.skybornmc.skyblock.common.builds.BuildPoints;
import net.skybornmc.skyblock.common.builds.BuildRegion;
import org.bukkit.Location;

import java.util.Optional;

/**
 * The AFK pool box. Taken from the applied builds' metadata (region {@code pool.build-region} in the {@code regions}
 * list, e.g. added to the spawn build by the builders) or, when that region does not exist, from the box in afk.yml
 * ({@code pool.use-config-area}). No WorldGuard.
 *
 * @param source where it came from, for logs: {@code build:<build>} or {@code afk.yml}
 */
record PoolArea(String world, BuildBox box, String source) {

    boolean contains(Location location) {
        return location != null && location.getWorld() != null && location.getWorld().getName().equals(world)
                && box.contains(location.getX(), location.getY(), location.getZ());
    }

    /** The pool from the builds (first) or the config; empty = closed. */
    static Optional<PoolArea> resolve(AfkConfig config, BuildPoints builds) {
        if (builds != null && config.buildRegion != null && !config.buildRegion.isBlank()) {
            Optional<BuildRegion> region = builds.region("regions", config.buildRegion.trim());
            if (region.isPresent()) {
                BuildRegion r = region.get();
                return Optional.of(new PoolArea(r.world(), r.box(), "build:" + r.build()));
            }
        }
        if (config.useConfigArea && config.world != null && !config.world.isBlank()) {
            return Optional.of(new PoolArea(config.world.trim(),
                    new BuildBox(config.minX, config.minY, config.minZ, config.maxX, config.maxY, config.maxZ), "afk.yml"));
        }
        return Optional.empty();
    }
}
