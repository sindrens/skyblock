package net.skybornmc.skyblock.common.tutorial;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/**
 * The tutorial is complete for a player (finished, or skipped by staff). Fired on the main thread by the tutorial module,
 * only while the player is online. Ranks V2: the perks module listens to give Ashborn at once when the playtime is there.
 */
public final class TutorialFinishedEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final boolean rewarded;

    public TutorialFinishedEvent(Player player, boolean rewarded) {
        super(player);
        this.rewarded = rewarded;
    }

    /** True when this finish paid the tutorial reward (false for a staff skip or a repeat). */
    public boolean rewarded() {
        return rewarded;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
