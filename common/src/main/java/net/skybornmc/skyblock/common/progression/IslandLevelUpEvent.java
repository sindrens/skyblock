package net.skybornmc.skyblock.common.progression;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * The dynamic island level changed (PHASE-5 M5.2), main thread, after the new value was applied. Fired for rises and
 * for drops (live sources such as block worth can go down). Island achievements ("Rising Island") listen here.
 */
public final class IslandLevelUpEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID islandId;
    private final int oldLevel;
    private final int newLevel;

    public IslandLevelUpEvent(UUID islandId, int oldLevel, int newLevel) {
        this.islandId = islandId;
        this.oldLevel = oldLevel;
        this.newLevel = newLevel;
    }

    public UUID islandId() {
        return islandId;
    }

    public int oldLevel() {
        return oldLevel;
    }

    public int newLevel() {
        return newLevel;
    }

    /** True if the level went up. */
    public boolean rose() {
        return newLevel > oldLevel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
