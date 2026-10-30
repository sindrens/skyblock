package net.skybornmc.skyblock.common.profile;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * A profile was deleted (main thread, after the profiles table marks it deleted). Modules remove or zero the profile's
 * own data (balances, skills, unlocks …) keyed by {@link Profile#id()}. The key is never used again.
 */
public final class ProfileDeletedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Profile profile;

    public ProfileDeletedEvent(Profile profile) {
        this.profile = profile;
    }

    public Profile profile() {
        return profile;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
