package net.skybornmc.skyblock.common.tools;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/** A custom tool levelled up while {@link #getPlayer()} used it (main thread, after the item was updated). */
public final class ToolLevelUpEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final ToolInfo tool;
    private final int fromLevel;

    public ToolLevelUpEvent(Player player, ToolInfo tool, int fromLevel) {
        super(player);
        this.tool = tool;
        this.fromLevel = fromLevel;
    }

    /** The tool after the level-up. */
    public ToolInfo getTool() {
        return tool;
    }

    public int getFromLevel() {
        return fromLevel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
