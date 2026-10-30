package net.skybornmc.skyblock.common.progression;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.menu.Menu;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The challenge book (PHASE-5 M5.3, O6), published by the progression module:
 * {@code core.services().optional(TaskService.class)}. Tasks live in {@code tasks.yml}; progress is shared by every
 * counting member of an island (never coop players or visitors) and counts from the island's creation (owner P5-9).
 *
 * <p><b>Gating (M5.4 upgrades, M5.5 masteries):</b> every task lists the unlock keys it is required for, e.g.
 * {@code upgrade.island_size.1} or {@code mastery.timber.2}. {@link #unlocked} is true when every task listing that key is
 * done (and also when no task lists it: check {@link #tasksFor} to tell a missing catalog entry apart). Completion is
 * persisted and never undone within a season, so a level that was unlocked stays unlocked.</p>
 *
 * <p>Every method is for the main thread and reads memory. Event: {@link TaskCompletedEvent}.</p>
 */
public interface TaskService {

    /** True once the stored progress was loaded (before that, progress reads 0 and nothing completes). */
    boolean ready();

    /** Every enabled task, by tier then file order. */
    List<TaskInfo> tasks();

    Optional<TaskInfo> task(String id);

    /** Tasks that list {@code unlockKey}, by tier then file order (empty when none). */
    List<TaskInfo> tasksFor(String unlockKey);

    /** Progress of the task on the island, 0 to its amount (reach tasks: the live value, or the amount once done). */
    long progress(UUID islandId, String taskId);

    /** True when the island completed the task this season. */
    boolean completed(UUID islandId, String taskId);

    /** True when every task listing {@code unlockKey} is completed by the island (true when no task lists it). */
    boolean unlocked(UUID islandId, String unlockKey);

    /** Number of tasks the island completed this season. */
    int completedCount(UUID islandId);

    /** The task's short name for {@code viewer}, e.g. "Mine 300 Coal Ore" (empty for unknown ids). */
    Component taskName(Player viewer, String taskId);

    /**
     * Ready-made lore lines for the tasks of an unlock key ({@code ✔ Mine 900 Copper Ore} / {@code ✘ Chop 600 Birch Logs
     * 412/600}), for the upgrade and mastery menus.
     */
    List<Component> taskLines(Player viewer, UUID islandId, String unlockKey);

    /** Opens the challenge book for {@code player}'s island; {@code parent} = Back target or null. */
    void openBook(Player player, Menu parent);

    /** Opens one task (deliver tasks: the hand-in screen); {@code parent} = Back target or null. */
    void openTask(Player player, String taskId, Menu parent);

    /** Registers (or with {@code null} removes) the island storage source of deliver tasks (STORE2). */
    void storage(TaskStorage storage);
}
