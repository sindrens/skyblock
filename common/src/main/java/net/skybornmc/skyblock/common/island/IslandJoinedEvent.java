package net.skybornmc.skyblock.common.island;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * A player accepted an island invite and is now a stored member (main thread, fired by the islands module after the
 * join is saved). The tutorial gives new players the Starter pack here (TUTORIAL.md §6.1).
 */
public final class IslandJoinedEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID islandId;

    public IslandJoinedEvent(Player player, UUID islandId) {
        super(player);
        this.islandId = islandId;
    }

    public UUID islandId() {
        return islandId;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
