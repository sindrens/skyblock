package net.skybornmc.skyblock.common.profile;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * A player now plays on another profile (main thread, after the new inventory was loaded and
 * {@link ProfileService#profileKey} returns the new key). Modules with per-profile caches (balances, skills, settings)
 * drop the old profile's values and load the new ones here.
 */
public final class ProfileSwitchedEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Profile from;
    private final Profile to;

    public ProfileSwitchedEvent(Player player, Profile from, Profile to) {
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

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
