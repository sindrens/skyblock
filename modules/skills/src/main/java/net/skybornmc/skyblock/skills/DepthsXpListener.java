package net.skybornmc.skyblock.skills;

import net.skybornmc.skyblock.depths.engine.ResourceBreakEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Mining and Foraging XP from The Depths. This is the <b>only</b> class of the skills module that touches the depths
 * module: {@code modules/skills} depends on {@code modules/depths} as {@code compileOnly} (both are shaded into the same
 * jar), and {@link SkillsModule} registers this listener inside a {@code NoClassDefFoundError} guard. When the depths
 * module is disabled the event simply never fires. Depths never depends on skills (it reads levels through
 * {@code SkillService} in common), so there is no cycle.
 */
final class DepthsXpListener implements Listener {

    private final SkillManager manager;

    DepthsXpListener(SkillManager manager) {
        this.manager = manager;
    }

    /** After every other listener: a cancelled break (tier locked, satchel full) gives no XP. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(ResourceBreakEvent event) {
        manager.onResourceBreak(event.getPlayer(), event.getBlockType().getKey().getKey());
    }
}
