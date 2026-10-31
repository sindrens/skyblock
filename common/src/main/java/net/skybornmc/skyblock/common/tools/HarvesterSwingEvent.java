package net.skybornmc.skyblock.common.tools;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * A player swung a harvester hoe (PHASE-7 M7.4): right-clicked a crop on their own island and the area was harvested.
 * Fired after all blocks of the swing were broken (each one already fired its own break event and, when ripe, its own
 * {@code IslandCropHarvestEvent}, which is where XP and books belong). Information only, for swing counters such as
 * Sunfire Harvest (E6). Only fired when at least one crop was harvested.
 */
public final class HarvesterSwingEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final ToolInfo hoe;
    private final Block center;
    private final int size;
    private final int harvested;

    public HarvesterSwingEvent(Player player, ToolInfo hoe, Block center, int size, int harvested) {
        super(player);
        this.hoe = hoe;
        this.center = center;
        this.size = size;
        this.harvested = harvested;
    }

    /** The hoe after the swing (level, perks). */
    public ToolInfo hoe() {
        return hoe;
    }

    /** The clicked crop block (the centre of the area). */
    public Block center() {
        return center;
    }

    /** Side length of the square area (3, 5, 7, 9). */
    public int size() {
        return size;
    }

    /** Crop blocks broken in this swing. */
    public int harvested() {
        return harvested;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
