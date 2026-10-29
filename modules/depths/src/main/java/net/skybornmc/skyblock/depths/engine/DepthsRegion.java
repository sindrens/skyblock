package net.skybornmc.skyblock.depths.engine;

import org.bukkit.Material;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * A box in one world where resource blocks are per player. Comes from {@code depths.yml} ({@code source = "config"})
 * or from another module through {@link Depths#registerRegion} (source = that module's id; the zones of M4.1 do this).
 *
 * @param id        unique id ({@code a-z 0-9 _ -})
 * @param world     world name
 * @param resources resource blocks by real material (copied, unmodifiable)
 * @param source    who defined it
 */
public record DepthsRegion(String id, String world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                           Map<Material, ResourceBlock> resources, String source) implements Bounds {

    public DepthsRegion {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(source, "source");
        if (!id.matches("[a-z0-9_-]+")) {
            throw new IllegalArgumentException("Invalid region id '" + id + "' (use a-z, 0-9, _ and -)");
        }
        int x0 = Math.min(minX, maxX);
        int x1 = Math.max(minX, maxX);
        int y0 = Math.min(minY, maxY);
        int y1 = Math.max(minY, maxY);
        int z0 = Math.min(minZ, maxZ);
        int z1 = Math.max(minZ, maxZ);
        minX = x0;
        maxX = x1;
        minY = y0;
        maxY = y1;
        minZ = z0;
        maxZ = z1;
        resources = resources.isEmpty() ? Map.of() : java.util.Collections.unmodifiableMap(new EnumMap<>(resources));
    }

    /** Region from two corners in any order. */
    public static DepthsRegion of(String id, String world, int x1, int y1, int z1, int x2, int y2, int z2,
                                  Map<Material, ResourceBlock> resources, String source) {
        return new DepthsRegion(id, world, x1, y1, z1, x2, y2, z2, resources, source);
    }

    /** The resource definition for this real block material, or {@code null} if it is not a resource here. */
    public ResourceBlock resource(Material material) {
        return resources.get(material);
    }
}
