package net.skybornmc.skyblock.common.profile;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Before a player switches profile (main thread). Cancel it to refuse the switch, e.g. in combat, while frozen or in a
 * boss arena (round 3 PR4); set a {@link #reason} so the player knows why. {@link ProfileSwitchedEvent} follows when the
 * switch is done.
 */
public final class ProfileSwitchEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Profile from;
    private final Profile to;
    private boolean cancelled;
    private Component reason;

    public ProfileSwitchEvent(Player player, Profile from, Profile to) {
        super(player);
        this.from = from;
        this.to = to;
    }

    public Profile from() {
        return from;
    }

    public Profile to() {
        return to;
    }

    /** Why the switch was refused (shown to the player), or {@code null} for the generic text. */
    public Component reason() {
        return reason;
    }

    /** Cancels the switch with a message for the player. */
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
