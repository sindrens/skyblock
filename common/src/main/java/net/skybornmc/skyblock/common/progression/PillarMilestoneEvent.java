package net.skybornmc.skyblock.common.progression;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * An island reached a synergy milestone: every shown pillar is at {@link #milestone()} or higher (default 10 / 25 / 40 /
 * 60). Fired once per island, milestone and season (main thread), after the milestone was saved. Phase 5 pays island
 * crystals and shows a toast; Phase 7 hangs outposts and biomes on the same event.
 */
public final class PillarMilestoneEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID islandId;
    private final int milestone;

    public PillarMilestoneEvent(UUID islandId, int milestone) {
        this.islandId = islandId;
        this.milestone = milestone;
    }

    public UUID islandId() {
        return islandId;
    }

    public int milestone() {
        return milestone;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
