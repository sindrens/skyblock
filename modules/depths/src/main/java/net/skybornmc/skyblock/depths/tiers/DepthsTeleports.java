package net.skybornmc.skyblock.depths.tiers;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.teleport.TeleportService;
import net.skybornmc.skyblock.depths.DepthsConfig;
import net.skybornmc.skyblock.depths.zones.Aspect;
import net.skybornmc.skyblock.depths.zones.BossArena;
import net.skybornmc.skyblock.depths.zones.Zone;
import net.skybornmc.skyblock.depths.zones.ZoneService;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Optional;

/**
 * How the Depths take part in the core teleports (owner answers round 2):
 * <ul>
 *   <li>345: the boss arena and the combat tiers are combat zones, so every player teleport out of them waits
 *       {@code teleport.yml → danger.warmup-seconds} (never blocked).</li>
 *   <li>346: {@code /tpahere} (or any player teleport) into a tier the traveller has not unlocked is refused.</li>
 * </ul>
 * Main thread (zone lookups are main-thread only).
 */
public final class DepthsTeleports {

    private final Core core;
    private final DepthsConfig config;
    private final ZoneService zones;
    private final TierGates gates;
    private final TeleportService.DangerZone danger = this::inCombatZone;
    private final TeleportService.Guard guard = this::deny;
    private TeleportService service;

    public DepthsTeleports(Core core, DepthsConfig config, ZoneService zones, TierGates gates) {
        this.core = core;
        this.config = config;
        this.zones = zones;
        this.gates = gates;
    }

    public void register() {
        service = core.services().optional(TeleportService.class).orElse(null);
        if (service != null) {
            service.addDangerZone(danger);
            service.addGuard(guard);
        }
    }

    public void unregister() {
        if (service != null) {
            service.removeDangerZone(danger);
            service.removeGuard(guard);
            service = null;
        }
    }

    /** Pure rule: the boss arena always counts; a tier zone counts when it is a combat tier and the setting is on. */
    static boolean combatZone(boolean inBossArena, Aspect zoneAspect, boolean combatTiersCount) {
        return inBossArena || (combatTiersCount && zoneAspect == Aspect.COMBAT);
    }

    /** Pure rule: a teleport into a tier zone is refused while the tier is locked for the traveller. */
    static boolean refuseInto(boolean destinationIsTier, boolean lockedForTraveller, boolean refuseLocked) {
        return refuseLocked && destinationIsTier && lockedForTraveller;
    }

    private boolean inCombatZone(Player player) {
        Location at = player.getLocation();
        if (!inDepths(at)) {
            return false;
        }
        BossArena arena = zones.bossArena();
        boolean inArena = arena != null && arena.region().contains(at.getX(), at.getY(), at.getZ());
        Zone zone = inArena ? null : zones.zoneAt(at);
        return combatZone(inArena, zone == null ? null : zone.aspect(), config.teleportCombatTiersAreDanger);
    }

    private Optional<String> deny(Player player, Location destination) {
        if (!inDepths(destination)) {
            return Optional.empty();
        }
        Zone zone = zones.zoneAt(destination);
        if (refuseInto(zone != null, zone != null && gates.locked(player, zone), config.teleportRefuseLockedTiers)) {
            return Optional.of(core.messages().raw("depths", "dungeon.teleport-locked"));
        }
        return Optional.empty();
    }

    private boolean inDepths(Location location) {
        World world = location.getWorld();
        return world != null && world.getName().equals(zones.world());
    }
}
