package net.skybornmc.skyblock.tools;

import net.skybornmc.skyblock.depths.engine.ResourceBreakEvent;
import net.skybornmc.skyblock.depths.zones.Aspect;
import net.skybornmc.skyblock.depths.zones.Zone;
import net.skybornmc.skyblock.depths.zones.ZoneService;
import net.skybornmc.skyblock.common.tools.ToolKind;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Optional;

/**
 * Tool XP and blueprint drops from The Depths. This is the <b>only</b> class of the tools module that touches the depths
 * module: {@code modules/tools} depends on {@code modules/depths} as {@code compileOnly} (both are shaded into the same
 * jar) and {@link ToolsModule} creates it inside a {@code NoClassDefFoundError} guard, like the skills module does.
 * Depths never depends on tools.
 */
final class DepthsHooks implements Listener, ToolsListener.Depths {

    private final ToolsModule module;

    DepthsHooks(ToolsModule module) {
        this.module = module;
    }

    private Optional<ZoneService> zones() {
        return module.core.services().optional(ZoneService.class);
    }

    /** After every other listener: a cancelled break (tier locked, satchel full) gives nothing. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(ResourceBreakEvent event) {
        Optional<ZoneService> zones = zones();
        if (zones.isEmpty()) {
            return;
        }
        Zone zone = zones.get().zoneAt(event.getLocation());
        if (zone == null) {
            return;
        }
        Player player = event.getPlayer();
        int tier = net.skybornmc.skyblock.depths.engine.PaletteMath.rewardTier(event.getTier(), zone.tier()); // DV2-SPIKE: the shown palette tier
        ToolKind needed = zone.aspect() == Aspect.MINE ? ToolKind.PICKAXE : zone.aspect() == Aspect.FOREST ? ToolKind.AXE : null;
        if (needed != null) {
            ToolItems.read(event.getTool()).filter(d -> d.kind() == needed).ifPresent(d ->
                    module.tracker().gain(player, module.config().tierXp(tier),
                            needed == ToolKind.AXE ? ToolData.STAT_LOGS : ToolData.STAT_BLOCKS));
        }
        module.drops().roll(player, zone.aspect().id(), tier, event.getLocation());
    }

    @Override
    public boolean inDepths(World world) {
        return zones().map(z -> z.world().equals(world.getName())).orElse(false);
    }

    @Override
    public int combatTier(Location location) {
        Optional<ZoneService> zones = zones();
        if (zones.isEmpty()) {
            return 0;
        }
        Zone zone = zones.get().zoneAt(location);
        return zone != null && zone.aspect() == Aspect.COMBAT ? zone.tier() : 0;
    }
}
