package net.skybornmc.skyblock.collection;

import net.skybornmc.skyblock.depths.combat.DepthsMobKillEvent;
import net.skybornmc.skyblock.depths.drops.DepthsDropEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Map;

/**
 * Depths finds: the drops of a block or log (mine, forest; rare finds included) and of a combat mob, read at MONITOR
 * after every other listener changed the amounts. Only items that really drop (amount above 0) count. No database work
 * here: the recorder checks a cached set.
 */
final class DepthsHooks implements Listener {

    private final CollectionModule m;

    DepthsHooks(CollectionModule module) {
        this.m = module;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDrop(DepthsDropEvent event) {
        if (!m.settings.enabled) {
            return;
        }
        Player p = event.getPlayer();
        String source = "depths_" + event.getWing();
        if (event.getItems() > 0) {
            m.found(p, event.getItem(), source);
        }
        for (Map.Entry<String, Integer> e : event.getExtra().entrySet()) {
            if (e.getValue() != null && e.getValue() > 0) {
                m.found(p, e.getKey(), source);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(DepthsMobKillEvent event) {
        if (!m.settings.enabled) {
            return;
        }
        Player p = event.getPlayer();
        for (Map.Entry<String, Integer> e : event.getItems().entrySet()) {
            if (e.getValue() != null && e.getValue() > 0) {
                m.found(p, e.getKey(), "depths_combat");
            }
        }
    }
}
