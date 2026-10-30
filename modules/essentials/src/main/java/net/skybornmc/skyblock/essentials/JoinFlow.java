package net.skybornmc.skyblock.essentials;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.db.Sql;
import net.skybornmc.skyblock.common.player.PlayerData;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * M1.1 join flow: spawn on join, first-join welcome (title, sound, clickable chat, server-wide broadcast with the
 * unique join number), welcome-back title, join/quit messages only for players with the join-message permission
 * (ranks + staff, owner decision), respawn at spawn.
 *
 * <p>Runs at HIGH so the vanish listener (HIGHEST) can still silence vanished staff. F1: the final join/leave line is
 * then delivered at MONITOR only to the players who keep {@code join-messages} on in {@code /settings} (and the
 * console), instead of the vanilla broadcast to everyone.</p>
 */
final class JoinFlow implements Listener {

    private final Core core;
    private final EssentialsConfig config;
    private final ModuleMessages msg;
    private final EssentialsSettings settings;

    JoinFlow(Core core, EssentialsConfig config, ModuleMessages msg, EssentialsSettings settings) {
        this.core = core;
        this.config = config;
        this.msg = msg;
        this.settings = settings;
    }

    /** Sends a join/leave line to the console and to every online player who wants join messages ({@code except} skipped). */
    private void deliver(Component line, Player except) {
        Bukkit.getConsoleSender().sendMessage(line);
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (except != null) {
            online.remove(except);
        }
        for (Player viewer : EssentialsSettings.recipients(online, p -> settings.wantsJoinMessages(p.getUniqueId()))) {
            viewer.sendMessage(line);
        }
    }

    /** The spawn point of the configured spawn world, or empty if that world isn't loaded. */
    Optional<Location> spawn() {
        World world = Bukkit.getWorld(config.spawnWorld);
        return world == null ? Optional.empty() : Optional.of(world.getSpawnLocation().toCenterLocation());
    }

    /** Rank-colored name for join/quit and broadcasts. */
    private Component name(Player player) {
        return Component.text(player.getName(), core.ranks().rankColor(player));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        boolean first = core.players().get(player.getUniqueId()).map(PlayerData::isNew).orElse(!player.hasPlayedBefore());

        if (first || config.joinTeleportToSpawn) {
            spawn().ifPresent(loc -> player.teleportAsync(loc));
        }

        if (first) {
            event.joinMessage(null);
            firstJoin(player);
            return;
        }
        event.joinMessage(player.hasPermission(config.joinMessagePermission)
                ? msg.component(player, "join.message", Msg.c("player", name(player)), Msg.p("rank", core.ranks().groupDisplayName(player)))
                : null);
        if (config.welcomeBackTitle) {
            Bukkit.getScheduler().runTaskLater(core.plugin(), () -> {
                if (player.isOnline()) {
                    msg.title(player, "join.welcome-back", Msg.p("player", player.getName()));
                }
            }, 10L);
        }
    }

    private void firstJoin(Player player) {
        Bukkit.getScheduler().runTaskLater(core.plugin(), () -> {
            if (!player.isOnline()) {
                return;
            }
            msg.title(player, "join.first-title", Msg.p("player", player.getName()));
            core.sounds().play(player, "unlock");
            msg.send(player, "join.first-welcome", Msg.p("player", player.getName()));
        }, 20L);
        if (!config.firstJoinBroadcast) {
            return;
        }
        // Unique join number = how many players have ever joined (the new row is saved before join).
        core.database().async(c -> Sql.queryOne(c, "SELECT COUNT(*) FROM players", rs -> rs.getLong(1)).orElse(0L))
                .whenComplete((count, error) -> Bukkit.getScheduler().runTask(core.plugin(), () -> {
                    String number = error == null && count > 0 ? Format.number(count) : "?";
                    List<Player> online = EssentialsSettings.recipients(Bukkit.getOnlinePlayers(),
                            p -> p.equals(player) || settings.wantsJoinMessages(p.getUniqueId()));
                    for (Player viewer : online) {
                        msg.send(viewer, "join.first-broadcast", Msg.p("player", player.getName()), Msg.p("number", number));
                    }
                }));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        event.quitMessage(player.hasPermission(config.joinMessagePermission)
                ? msg.component(player, "join.quit", Msg.c("player", name(player)), Msg.p("rank", core.ranks().groupDisplayName(player)))
                : null);
    }

    /** After every other listener chose the line (vanish nulls it at HIGHEST): show it only to players who want it. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void deliverJoin(PlayerJoinEvent event) {
        Component line = event.joinMessage();
        if (line != null) {
            event.joinMessage(null);
            deliver(line, null);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void deliverQuit(PlayerQuitEvent event) {
        Component line = event.quitMessage();
        if (line != null) {
            event.quitMessage(null);
            deliver(line, event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        if (config.respawnAtSpawn && !event.isBedSpawn() && !event.isAnchorSpawn()) {
            spawn().ifPresent(event::setRespawnLocation);
        }
    }
}
