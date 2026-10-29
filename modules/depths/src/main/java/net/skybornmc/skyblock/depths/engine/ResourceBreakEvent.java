package net.skybornmc.skyblock.depths.engine;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * A player broke a resource block in a Depths region (the break passed the fast-break check). Fired on the main
 * thread before the block is depleted for the player. Listen to give drops, skill XP and tokens.
 *
 * <p>Cancel it to refuse the break (e.g. tier locked, satchel full): the block stays and nothing is depleted.
 * {@link #setRespawnTicks} changes how long it stays depleted for this player (perks).</p>
 */
public final class ResourceBreakEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Block block;
    private final DepthsRegion region;
    private final ResourceBlock resource;
    private final ItemStack tool;
    private int respawnTicks;
    private boolean cancelled;

    public ResourceBreakEvent(Player player, Block block, DepthsRegion region, ResourceBlock resource, ItemStack tool) {
        super(player);
        this.block = block;
        this.region = region;
        this.resource = resource;
        this.tool = tool;
        this.respawnTicks = resource.respawnTicks();
    }

    /** The real block (unchanged in the world). */
    public Block getBlock() {
        return block;
    }

    /** The real block's material. */
    public Material getBlockType() {
        return resource.block();
    }

    public Location getLocation() {
        return block.getLocation();
    }

    /** Item in the main hand when breaking (may be air). Do not modify it here. */
    public ItemStack getTool() {
        return tool;
    }

    public DepthsRegion getRegion() {
        return region;
    }

    public ResourceBlock getResource() {
        return resource;
    }

    public int getRespawnTicks() {
        return respawnTicks;
    }

    /** At least 1 tick. */
    public void setRespawnTicks(int respawnTicks) {
        this.respawnTicks = Math.max(1, respawnTicks);
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
