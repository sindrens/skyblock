package net.skybornmc.skyblock.common.island;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * A player came through the island portal at spawn and has arrived on their island (main thread, fired by the worlds
 * module). {@link #firstTime()} is true when the portal created the island just now (owner answer 159: the first thing
 * on the server is walking into this portal; the tutorial starts on the island). The tutorial hooks this event.
 */
public final class IslandPortalEnteredEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final boolean firstTime;
    private final String portal;

    public IslandPortalEnteredEvent(Player player, boolean firstTime, String portal) {
        super(player);
        this.firstTime = firstTime;
        this.portal = portal;
    }

    /** True if the island was created by this portal use (a brand-new island). */
    public boolean firstTime() {
        return firstTime;
    }

    /** Id of the portal used ({@code to_island}). */
    public String portal() {
        return portal;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
