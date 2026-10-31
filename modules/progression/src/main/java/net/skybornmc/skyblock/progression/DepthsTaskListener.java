package net.skybornmc.skyblock.progression;

import net.skybornmc.skyblock.common.progression.TaskType;
import net.skybornmc.skyblock.depths.engine.ResourceBreakEvent;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Mine and chop task progress from The Depths (PHASE-5 M5.3). This is the <b>only</b> class of the progression module
 * that touches the depths module ({@code compileOnly}, both are shaded into the same jar); {@link ProgressionModule}
 * registers it inside a {@code NoClassDefFoundError} guard. Depths blocks are per player (never placed by players), so
 * every accepted break counts. Logs count as chop, everything else as mine.
 */
final class DepthsTaskListener implements Listener {

    private final TaskSources sources;
    private final TaskManager tasks;

    DepthsTaskListener(TaskSources sources, TaskManager tasks) {
        this.sources = sources;
        this.tasks = tasks;
    }

    /** After every other listener: a cancelled break (tier locked, satchel full) counts nothing. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(ResourceBreakEvent event) {
        Material type = event.getShownType(); // DV2-SPIKE: the block the player saw (palette tier)
        TaskType kind = Tag.LOGS.isTagged(type) ? TaskType.CHOP : TaskType.MINE;
        String object = type.getKey().getKey();
        if (!tasks.enabled() || !tasks.tracks(kind, object)) {
            return;
        }
        sources.countDepths(event.getPlayer(), event.getLocation(), kind, object);
    }
}
