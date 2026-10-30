package net.skybornmc.skyblock.common.progression;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * A task of the challenge book was completed by an island (PHASE-5 M5.3). Fired once per island, task and season on the
 * main thread, after the completion and its rewards were saved in one database transaction. M5.4 upgrades and M5.5
 * masteries listen to show "Ready to buy" when {@link #unlocksCompleted()} is not empty.
 */
public final class TaskCompletedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID islandId;
    private final TaskInfo task;
    private final UUID cause;
    private final List<String> unlocksCompleted;

    public TaskCompletedEvent(UUID islandId, TaskInfo task, UUID cause, List<String> unlocksCompleted) {
        this.islandId = islandId;
        this.task = task;
        this.cause = cause;
        this.unlocksCompleted = unlocksCompleted == null ? List.of() : List.copyOf(unlocksCompleted);
    }

    public UUID islandId() {
        return islandId;
    }

    public TaskInfo task() {
        return task;
    }

    /** Profile key of the member whose action finished it (null for reach tasks found by the periodic check). */
    public UUID cause() {
        return cause;
    }

    /** Unlock keys of this task whose tasks are now all done (e.g. {@code upgrade.island_size.1}). */
    public List<String> unlocksCompleted() {
        return unlocksCompleted;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
