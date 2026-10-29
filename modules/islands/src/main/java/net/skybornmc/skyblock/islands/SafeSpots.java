package net.skybornmc.skyblock.islands;

import net.skybornmc.skyblock.common.island.IslandService.Bounds;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/** {@link SafeSpot} on a real world: block classification and the vertical search window (main thread). */
final class SafeSpots {

    /** How far up/down from the wanted Y a safe spot may be (keeps the search cheap). */
    static final int VERTICAL_RANGE = 32;

    private static final Set<Material> DANGER = EnumSet.noneOf(Material.class);

    static {
        for (String name : new String[]{"LAVA", "FIRE", "SOUL_FIRE", "MAGMA_BLOCK", "CAMPFIRE", "SOUL_CAMPFIRE", "CACTUS",
                "SWEET_BERRY_BUSH", "POWDER_SNOW", "WITHER_ROSE", "POINTED_DRIPSTONE", "COBWEB", "NETHER_PORTAL", "END_PORTAL",
                "END_GATEWAY"}) {
            Material material = Material.getMaterial(name);
            if (material != null) {
                DANGER.add(material);
            }
        }
    }

    private SafeSpots() {
    }

    static SafeSpot.Kind classify(Material material) {
        if (material.isAir()) {
            return SafeSpot.Kind.PASSABLE;
        }
        if (DANGER.contains(material)) {
            return SafeSpot.Kind.DANGER;
        }
        if (material == Material.WATER || material == Material.BUBBLE_COLUMN) {
            return SafeSpot.Kind.LIQUID;
        }
        return material.isSolid() ? SafeSpot.Kind.SOLID : SafeSpot.Kind.PASSABLE;
    }

    /** Reads the world; unloaded chunks count as danger so they are never chosen. */
    static SafeSpot.Column column(World world) {
        return (x, y, z) -> {
            if (y < world.getMinHeight() || y >= world.getMaxHeight()) {
                return SafeSpot.Kind.PASSABLE;
            }
            if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                return SafeSpot.Kind.DANGER;
            }
            return classify(world.getType(x, y, z));
        };
    }

    static boolean isSafe(Location feet) {
        return feet.getWorld() != null && SafeSpot.isSafeAt(column(feet.getWorld()), feet.getBlockX(), feet.getY(), feet.getBlockZ());
    }

    /** The safe spot nearest to {@code wanted} inside {@code area}, within Y {@code minY}-{@code maxY}. */
    static Optional<Location> nearest(Location wanted, Bounds area, int radius, int minY, int maxY) {
        World world = wanted.getWorld();
        int y = wanted.getBlockY();
        int low = Math.max(minY, y - VERTICAL_RANGE);
        int high = Math.min(maxY, y + VERTICAL_RANGE);
        return SafeSpot.nearest(column(world), wanted.getBlockX(), y, wanted.getBlockZ(), radius, low, high, area::contains)
                .map(spot -> new Location(world, spot.x() + 0.5, spot.y(), spot.z() + 0.5, wanted.getYaw(), wanted.getPitch()));
    }
}
