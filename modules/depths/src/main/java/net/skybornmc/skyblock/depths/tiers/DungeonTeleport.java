package net.skybornmc.skyblock.depths.tiers;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Scheduler;
import net.skybornmc.skyblock.depths.DepthsConfig;
import net.skybornmc.skyblock.depths.zones.Zone;
import net.skybornmc.skyblock.depths.zones.ZoneService;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * {@code /dungeon} teleports: a warmup of {@code dungeon.warmup-seconds} standing still (cancelled by moving or
 * damage), then a teleport to the zone's arrival pad. The lock is checked when asked and again on arrival (a level can
 * not drop in between today, but staff can set XP).
 *
 * <p>The essentials {@code TeleportService} lives in a module, not in {@code common}, so depths cannot use it without
 * depending on essentials; this is the same behaviour with the depths texts (queued: move a teleport interface to
 * common).</p>
 */
final class DungeonTeleport implements Listener {

    private record Warmup(Zone zone, Scheduler.Task task) {
    }

    private final Core core;
    private final DepthsConfig config;
    private final ZoneService zones;
    private final TierGates gates;
    private final ModuleMessages msg;
    private final Map<UUID, Warmup> warmups = new HashMap<>();

    DungeonTeleport(Core core, DepthsConfig config, ZoneService zones, TierGates gates, ModuleMessages msg) {
        this.core = core;
        this.config = config;
        this.zones = zones;
        this.gates = gates;
        this.msg = msg;
    }

    void teleport(Player player, Zone zone) {
        if (gates.locked(player, zone)) {
            msg.send(player, "dungeon.locked", gates.requirement(player, zone));
            core.sounds().play(player, "error");
            return;
        }
        if (zones.location(zone.spawn()) == null) {
            msg.send(player, "zones.no-world", Msg.p("world", zones.world()));
            core.sounds().play(player, "error");
            return;
        }
        cancel(player.getUniqueId(), false);
        int seconds = config.dungeonWarmupSeconds;
        if (seconds <= 0 || gates.bypass(player)) {
            now(player, zone);
            return;
        }
        msg.send(player, "dungeon.warmup", Msg.p("seconds", seconds), gates.requirement(player, zone));
        int[] left = {seconds};
        Scheduler.Task task = core.scheduler().repeat("depths-dungeon-warmup", 0, 20, () -> {
            if (!player.isOnline()) {
                cancel(player.getUniqueId(), false);
                return;
            }
            if (left[0] <= 0) {
                Warmup done = warmups.remove(player.getUniqueId());
                if (done != null) {
                    done.task().cancel();
                }
                now(player, zone);
                return;
            }
            msg.actionBar(player, "dungeon.countdown", Msg.p("seconds", left[0]), gates.requirement(player, zone));
            core.sounds().play(player, "click");
            left[0]--;
        });
        warmups.put(player.getUniqueId(), new Warmup(zone, task));
    }

    private void now(Player player, Zone zone) {
        if (gates.locked(player, zone)) {
            msg.send(player, "dungeon.locked", gates.requirement(player, zone));
            core.sounds().play(player, "error");
            return;
        }
        Location target = zones.location(zone.spawn());
        if (target == null) {
            msg.send(player, "zones.no-world", Msg.p("world", zones.world()));
            return;
        }
        player.teleportAsync(target, PlayerTeleportEvent.TeleportCause.PLUGIN).thenAccept(ok -> {
            if (ok) {
                core.sounds().play(player, "teleport");
                msg.actionBar(player, "dungeon.arrived", gates.requirement(player, zone));
            } else {
                msg.send(player, "dungeon.failed", gates.requirement(player, zone));
            }
        });
    }

    private void cancel(UUID player, boolean notify) {
        Warmup warmup = warmups.remove(player);
        if (warmup == null) {
            return;
        }
        warmup.task().cancel();
        Player online = core.plugin().getServer().getPlayer(player);
        if (notify && online != null) {
            msg.send(online, "dungeon.cancelled");
            core.sounds().play(online, "error");
        }
    }

    void stop() {
        warmups.values().forEach(w -> w.task().cancel());
        warmups.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (config.dungeonCancelOnMove && !warmups.isEmpty() && event.hasChangedBlock()) {
            cancel(event.getPlayer().getUniqueId(), true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (config.dungeonCancelOnDamage && !warmups.isEmpty() && event.getEntity() instanceof Player player) {
            cancel(player.getUniqueId(), true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancel(event.getPlayer().getUniqueId(), false);
    }
}
