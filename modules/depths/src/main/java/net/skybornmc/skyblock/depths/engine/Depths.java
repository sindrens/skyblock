package net.skybornmc.skyblock.depths.engine;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.function.BiPredicate;

/**
 * The per-player blocks engine, published as a service ({@code core.services().get(Depths.class)}).
 *
 * <p>Other modules react to {@link ResourceBreakEvent} for drops, XP and tokens, register their own regions (zones,
 * tiers) and query depleted state. All methods are main-thread only.</p>
 */
public interface Depths {

    /** Region at a block, or {@code null}. Allocation-free. */
    DepthsRegion regionAt(String world, int x, int y, int z);

    /** Region at a location, or {@code null}. */
    default DepthsRegion regionAt(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        return regionAt(location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    /** Every active region (config + registered), in priority order (first wins on overlap). */
    List<DepthsRegion> regions();

    /**
     * Adds (or replaces, same id and same source) a region defined by code. Depleted blocks of a replaced region
     * are restored first.
     *
     * @throws IllegalArgumentException if the id belongs to a region from another source (e.g. {@code depths.yml})
     */
    void registerRegion(DepthsRegion region);

    /** Removes a region registered by code (restores its depleted blocks). Returns false if unknown. */
    boolean unregisterRegion(String id);

    /** Removes every region registered by {@code source} (call it when your module disables). */
    void unregisterAll(String source);

    /** True if this block is currently depleted for the player. */
    boolean isDepleted(Player player, Block block);

    /** How many blocks are depleted for the player right now. */
    int depletedCount(Player player);

    /**
     * Depletes a block for one player (as if they had broken it), without firing {@link ResourceBreakEvent}.
     * Returns false if the block is not a resource block in a region or already depleted for them.
     */
    boolean deplete(Player player, Block block, int respawnTicks);

    /** Shows the real block to the player again right now. Returns false if it was not depleted. */
    boolean restore(Player player, Block block);

    /** The player's visibility choice (default {@link Visibility#EVERYONE}). Not applied yet (hook). */
    Visibility visibility(Player player);

    void visibility(Player player, Visibility visibility);

    /** Islands plugs in "same island?" here so {@link Visibility#ISLAND} works later. */
    void islandCheck(BiPredicate<Player, Player> sameIsland);

    /** Whether {@code viewer} should see {@code other} in The Depths under the viewer's visibility choice. */
    boolean shouldSee(Player viewer, Player other);
}
