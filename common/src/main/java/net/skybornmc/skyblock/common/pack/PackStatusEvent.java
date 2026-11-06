package net.skybornmc.skyblock.common.pack;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** Fired on the main thread by the pack module when a player's {@link PackService.Status} really changes. */
public final class PackStatusEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final PackService.Status old;
    private final PackService.Status now;

    public PackStatusEvent(Player player, PackService.Status old, PackService.Status now) {
        this.player = player;
        this.old = old;
        this.now = now;
    }

    public Player player() {
        return player;
    }

    /** The status before ({@code null} when there was none yet). */
    public PackService.Status old() {
        return old;
    }

    public PackService.Status now() {
        return now;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
