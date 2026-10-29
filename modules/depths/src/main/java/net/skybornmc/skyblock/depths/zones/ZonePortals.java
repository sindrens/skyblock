package net.skybornmc.skyblock.depths.zones;

import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.depths.DepthsConfig;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * The boss portal (placeholder): stepping into {@code boss-arena.portal} in the last combat tier teleports to the boss
 * arena if the player may enter that tier; {@code boss-arena.return-portal} leads back. Only block changes inside the
 * Depths world are looked at (two box checks).
 */
public final class ZonePortals implements Listener {

    private final ZoneManager zones;
    private final DepthsConfig config;
    private final ModuleMessages msg;

    public ZonePortals(ZoneManager zones, DepthsConfig config, ModuleMessages msg) {
        this.zones = zones;
        this.config = config;
        this.msg = msg;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        Location from = event.getFrom();
        if (!config.portalsEnabled || (to.getBlockX() == from.getBlockX() && to.getBlockY() == from.getBlockY()
                && to.getBlockZ() == from.getBlockZ())) {
            return;
        }
        BossArena arena = zones.bossArena();
        if (arena == null || to.getWorld() == null || !to.getWorld().getName().equals(zones.world())) {
            return;
        }
        Player player = event.getPlayer();
        if (entered(arena.portal(), from, to)) {
            Zone holder = arena.portalZone() == null ? null : zones.zone(arena.portalZone()).orElse(null);
            if (holder != null) {
                ZoneAccess access = zones.access(player, holder);
                if (!access.allowed()) {
                    msg.actionBar(player, "zones.portal.locked",
                            Msg.c("skill", msg.component(player, "zones.skill." + access.skill().id())),
                            Msg.p("required", access.required()), Msg.p("level", access.level()));
                    return;
                }
            }
            teleport(player, arena.spawn(), "zones.portal.boss", arena.name());
        } else if (entered(arena.returnPortal(), from, to)) {
            Zone holder = arena.portalZone() == null ? null : zones.zone(arena.portalZone()).orElse(null);
            if (holder != null) {
                teleport(player, holder.spawn(), "zones.portal.back", holder.name());
            } else if (zones.hub() != null) {
                teleport(player, zones.hub().spawn(), "zones.portal.back", zones.hub().name());
            }
        }
    }

    private static boolean entered(Box portal, Location from, Location to) {
        return portal != null && portal.contains(to.getX(), to.getY(), to.getZ()) && !portal.contains(from.getX(), from.getY(), from.getZ());
    }

    private void teleport(Player player, Point point, String key, String name) {
        Location target = zones.location(point);
        if (target == null) {
            return;
        }
        player.teleportAsync(target).thenAccept(ok -> {
            if (ok) {
                msg.actionBar(player, key, Msg.p("name", name));
            }
        });
    }
}
