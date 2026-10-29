package net.skybornmc.skyblock.essentials;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Every teleport of this module goes through here: guards (other modules can forbid a destination), warmup with
 * move/damage cancel, the staff {@code /back} location and the teleport sound.
 *
 * <p>Published as a service ({@code core.services().get(TeleportService.class)}) so later modules (islands, PvP
 * zones, combat tag) can add a {@link Guard} and use the same warmup for their own teleports.</p>
 */
public final class TeleportService implements Listener {

    /** Can forbid a teleport. Return a message (MiniMessage) to show why, or empty to allow. */
    @FunctionalInterface
    public interface Guard {
        Optional<String> deny(Player player, Location destination);
    }

    private record Warmup(Location start, BukkitTask task) {
    }

    private final Core core;
    private final EssentialsConfig config;
    private final ModuleMessages msg;
    private final List<Guard> guards = new CopyOnWriteArrayList<>();
    private final Map<UUID, Warmup> warmups = new ConcurrentHashMap<>();
    private final Map<UUID, Location> back = new ConcurrentHashMap<>();

    TeleportService(Core core, EssentialsConfig config, ModuleMessages msg) {
        this.core = core;
        this.config = config;
        this.msg = msg;
    }

    public void addGuard(Guard guard) {
        guards.add(guard);
    }

    public void removeGuard(Guard guard) {
        guards.remove(guard);
    }

    /** The first guard's denial reason, if any guard forbids this destination. */
    public Optional<String> denied(Player player, Location destination) {
        for (Guard guard : guards) {
            Optional<String> reason = guard.deny(player, destination);
            if (reason.isPresent()) {
                return reason;
            }
        }
        return Optional.empty();
    }

    /**
     * Teleports after {@code warmupSeconds} of standing still (instant for 0 or staff with the bypass permission).
     *
     * @param destinationName shown in messages, e.g. "spawn" or a player name
     * @param onArrive        runs after a successful teleport (main thread)
     */
    public void teleport(Player player, Location destination, int warmupSeconds, String destinationName, Runnable onArrive) {
        Optional<String> reason = denied(player, destination);
        if (reason.isPresent()) {
            player.sendMessage(core.messages().parse(player, reason.get()));
            core.sounds().play(player, "error");
            return;
        }
        cancelWarmup(player.getUniqueId(), false);
        if (warmupSeconds <= 0 || player.hasPermission(Perms.TELEPORT_BYPASS)) {
            now(player, destination, destinationName, onArrive);
            return;
        }
        int[] left = {warmupSeconds};
        msg.send(player, "teleport.warmup", Msg.p("seconds", warmupSeconds), Msg.p("destination", destinationName));
        BukkitTask task = core.plugin().getServer().getScheduler().runTaskTimer(core.plugin(), () -> {
            if (!player.isOnline()) {
                cancelWarmup(player.getUniqueId(), false);
                return;
            }
            if (left[0] <= 0) {
                Warmup done = warmups.remove(player.getUniqueId());
                if (done != null) {
                    done.task().cancel();
                }
                now(player, destination, destinationName, onArrive);
                return;
            }
            msg.actionBar(player, "teleport.countdown", Msg.p("seconds", left[0]), Msg.p("destination", destinationName));
            core.sounds().play(player, "click");
            left[0]--;
        }, 0L, 20L);
        warmups.put(player.getUniqueId(), new Warmup(player.getLocation().clone(), task));
    }

    /** Instant teleport (staff commands): guards still apply unless the player has the bypass permission. */
    public void now(Player player, Location destination, String destinationName, Runnable onArrive) {
        if (!player.hasPermission(Perms.TELEPORT_BYPASS)) {
            Optional<String> reason = denied(player, destination);
            if (reason.isPresent()) {
                player.sendMessage(core.messages().parse(player, reason.get()));
                return;
            }
        }
        player.teleportAsync(destination, PlayerTeleportEvent.TeleportCause.COMMAND).thenAccept(ok -> {
            if (ok) {
                core.sounds().play(player, "teleport");
                if (onArrive != null) {
                    onArrive.run();
                }
            } else {
                msg.send(player, "teleport.failed", Msg.p("destination", destinationName));
            }
        });
    }

    /** Last location before a teleport (or death), for staff {@code /back}. */
    public Optional<Location> back(UUID player) {
        return Optional.ofNullable(back.get(player));
    }

    public boolean hasWarmup(UUID player) {
        return warmups.containsKey(player);
    }

    private void cancelWarmup(UUID player, boolean notify) {
        Warmup warmup = warmups.remove(player);
        if (warmup == null) {
            return;
        }
        warmup.task().cancel();
        Player p = core.plugin().getServer().getPlayer(player);
        if (notify && p != null) {
            msg.send(p, "teleport.cancelled");
            core.sounds().play(p, "error");
        }
    }

    void shutdown() {
        warmups.values().forEach(w -> w.task().cancel());
        warmups.clear();
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!config.cancelOnMove || warmups.isEmpty() || !event.hasChangedBlock()) {
            return;
        }
        Warmup warmup = warmups.get(event.getPlayer().getUniqueId());
        if (warmup != null) {
            cancelWarmup(event.getPlayer().getUniqueId(), true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onDamage(EntityDamageEvent event) {
        if (config.cancelOnDamage && event.getEntity() instanceof Player p && warmups.containsKey(p.getUniqueId())) {
            cancelWarmup(p.getUniqueId(), true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onTeleport(PlayerTeleportEvent event) {
        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        if (cause == PlayerTeleportEvent.TeleportCause.COMMAND || cause == PlayerTeleportEvent.TeleportCause.PLUGIN) {
            back.put(event.getPlayer().getUniqueId(), event.getFrom().clone());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        if (config.backRememberDeath) {
            back.put(event.getPlayer().getUniqueId(), event.getPlayer().getLocation().clone());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        cancelWarmup(id, false);
        if (!event.getPlayer().hasPermission(Perms.BACK)) {
            back.remove(id);
        }
    }
}
