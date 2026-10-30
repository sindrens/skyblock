package net.skybornmc.skyblock.common.region;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.function.BiPredicate;

/**
 * Region flags (DEPTHSPROT), published by the worlds module ({@code core.services().optional(RegionFlags.class)}).
 * A flag is resolved per block: the regions containing it (build metadata regions by kind, regions handed in with
 * {@link #contribute}) with the highest priority that set the flag win (deny wins a tie), else the world's flags,
 * else allowed. Lookups are chunk-indexed (two hash gets and a few box checks) and allocation-free; the worlds module
 * enforces every flag itself, other modules only query (the depths module's combat mobs drop targets in safe zones).
 */
public interface RegionFlags {

    /** True if {@code flag} is denied at this block. */
    boolean denied(String world, int x, int y, int z, RegionFlag flag);

    default boolean denied(Location location, RegionFlag flag) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        return denied(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ(), flag);
    }

    /** The regions at a block, as {@code owner:id (kind)} lines, highest priority first (debug tools). */
    List<String> regionsAt(String world, int x, int y, int z);

    /** Ids of the flags denied at a block (debug tools). */
    List<String> deniedAt(String world, int x, int y, int z);

    /** Replaces every region handed in by {@code owner} (main thread). An empty list removes them. */
    void contribute(String owner, List<FlagRegion> regions);

    /**
     * Adds a bypass: when it returns true for (player, world), protection flags ({@link RegionFlag#bypassable()}) don't
     * apply to that player there. Essentials adds its {@code /build} mode. Replaces an earlier bypass of the same owner.
     */
    void addBypass(String owner, BiPredicate<Player, String> bypass);

    void removeBypass(String owner);

    /** True if protection flags don't apply to this player in this world right now. */
    boolean bypasses(Player player, String world);
}
