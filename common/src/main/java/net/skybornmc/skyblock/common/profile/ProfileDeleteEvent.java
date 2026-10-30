package net.skybornmc.skyblock.common.profile;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Before a player deletes one of their (inactive) profiles (main thread). Modules cancel it when deleting would destroy
 * something the player must deal with first (an island with other members, unsold auction items …) and set a
 * {@link #reason}. After the delete {@link ProfileDeletedEvent} follows.
 */
public final class ProfileDeleteEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Profile profile;
    private boolean cancelled;
    private Component reason;

    public ProfileDeleteEvent(Player player, Profile profile) {
        super(player);
        this.profile = profile;
    }

    public Profile profile() {
        return profile;
    }

    public Component reason() {
        return reason;
    }

    public void refuse(Component why) {
        this.reason = why;
        this.cancelled = true;
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
