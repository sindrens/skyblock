package net.skybornmc.skyblock.common.progression;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * An island pillar went up one or more levels (main thread, after the XP was added). One event per gain, even when it
 * jumps several levels. The island level (M5.2), island achievements and later outposts listen here. Also fired for the
 * hidden Conquest pillar ({@link #shown()} is false then).
 *
 * @see ProgressionService
 */
public final class PillarLevelUpEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID islandId;
    private final Pillar pillar;
    private final int oldLevel;
    private final int newLevel;
    private final UUID cause;
    private final boolean shown;

    /**
     * @param cause the member (profile key) whose action caused it, or {@code null} (staff, events)
     */
    public PillarLevelUpEvent(UUID islandId, Pillar pillar, int oldLevel, int newLevel, UUID cause, boolean shown) {
        this.islandId = islandId;
        this.pillar = pillar;
        this.oldLevel = oldLevel;
        this.newLevel = newLevel;
        this.cause = cause;
        this.shown = shown;
    }

    public UUID islandId() {
        return islandId;
    }

    public Pillar pillar() {
        return pillar;
    }

    public int oldLevel() {
        return oldLevel;
    }

    public int newLevel() {
        return newLevel;
    }

    /** The member (profile key) whose action caused the level-up, or {@code null}. */
    public UUID cause() {
        return cause;
    }

    /** False while the pillar is hidden (Conquest before its first source). */
    public boolean shown() {
        return shown;
    }

    /** True if {@code level} was reached with this level-up (oldLevel &lt; level &lt;= newLevel). */
    public boolean reached(int level) {
        return oldLevel < level && level <= newLevel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
