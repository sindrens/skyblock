package net.skybornmc.skyblock.quests;

import net.skybornmc.skyblock.depths.combat.DepthsMobKillEvent;
import net.skybornmc.skyblock.depths.engine.ResourceBreakEvent;
import org.bukkit.Tag;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;


/**
 * Mine, chop and kill progress from The Depths. The <b>only</b> class of the quests module that touches the depths module
 * ({@code compileOnly}; both are shaded into the same jar); {@link QuestsModule} registers it inside a
 * {@code NoClassDefFoundError} guard. Depths blocks are per player and never placed by players, and the break event only
 * fires after the fast-break check, so every accepted break counts. Kills come from {@link DepthsMobKillEvent}, which fires
 * only for players who earned the mob's rewards (own kill, or a mini-boss damage share): no stack or spawner kills.
 */
final class DepthsQuestListener implements Listener {

    private final QuestsModule m;

    DepthsQuestListener(QuestsModule module) {
        this.m = module;
    }

    /** After every other listener: a cancelled break (tier locked, satchel full) counts nothing. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(ResourceBreakEvent event) {
        int tier = event.getTier() > 0 ? event.getTier() : QuestMath.tierOf(event.getRegion().id()); // DV2-SPIKE: shown tier
        if (tier <= 0) {
            return;
        }
        QuestType type = Tag.LOGS.isTagged(event.getShownType()) ? QuestType.CHOP : QuestType.MINE;
        m.engine().count(event.getPlayer(), type, tier, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(DepthsMobKillEvent event) {
        m.engine().count(event.getPlayer(), QuestType.KILL, Math.max(1, event.getTier()), 1);
    }
}
