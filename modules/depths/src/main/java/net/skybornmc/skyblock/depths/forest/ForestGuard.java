package net.skybornmc.skyblock.depths.forest;

import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.depths.DepthsConfig;
import net.skybornmc.skyblock.depths.DepthsPerms;
import net.skybornmc.skyblock.depths.zones.Aspect;
import net.skybornmc.skyblock.depths.zones.Zone;
import net.skybornmc.skyblock.depths.zones.ZoneService;
import org.bukkit.GameMode;
import org.bukkit.Tag;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.LeavesDecayEvent;

/**
 * Forest (M4.7): leaves (and the wart-block crowns of the nether fungi) are decoration. They never decay in the Depths world and players can't break them anywhere in
 * a forest zone (inside the log fields the per-player engine already refuses every non-log block). Logs themselves are
 * per-player resource blocks of the engine (10 s respawn), their drops and Foraging XP follow the mine's path.
 */
public final class ForestGuard implements Listener {

    private final DepthsConfig config;
    private final ZoneService zones;
    private final ModuleMessages msg;

    public ForestGuard(DepthsConfig config, ZoneService zones, ModuleMessages msg) {
        this.config = config;
        this.zones = zones;
        this.msg = msg;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDecay(LeavesDecayEvent event) {
        if (config.forestNoLeafDecay && event.getBlock().getWorld().getName().equals(zones.world())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!config.forestProtectLeaves || !(Tag.LEAVES.isTagged(event.getBlock().getType()) || Tag.WART_BLOCKS.isTagged(event.getBlock().getType()))
                || !event.getBlock().getWorld().getName().equals(zones.world())) {
            return;
        }
        Zone zone = zones.zoneAt(event.getBlock().getLocation());
        if (zone == null || zone.aspect() != Aspect.FOREST) {
            return;
        }
        Player player = event.getPlayer();
        if (config.builderBypass && player.getGameMode() == GameMode.CREATIVE && player.hasPermission(DepthsPerms.BUILD)) {
            return;
        }
        event.setCancelled(true);
        msg.actionBar(player, "forest.leaves");
    }
}
