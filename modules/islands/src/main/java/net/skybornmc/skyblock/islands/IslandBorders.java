package net.skybornmc.skyblock.islands;

import net.skybornmc.skyblock.common.island.IslandService.Bounds;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player world borders: a player in the islands world sees only the border of the island whose slot they are in
 * (a virtual border, sent to that player only). Leaving the slot or the world removes it. Moves are checked only when
 * the player crosses into another 512-block slot, so this costs nothing per tick.
 */
final class IslandBorders implements Listener {

    /** What a player currently sees: island id + size, so a size change re-sends it. */
    private record Shown(UUID island, int size) {
    }

    private final IslandManager islands;
    private final Map<UUID, Shown> shown = new HashMap<>();

    IslandBorders(IslandManager islands) {
        this.islands = islands;
    }

    /** Sends (or removes) the right border for where {@code player} is now. */
    void update(Player player) {
        Location at = player.getLocation();
        Island island = islands.isIslandWorld(at.getWorld())
                ? islands.islandInSlotAt(at.getBlockX(), at.getBlockZ()) : null;
        Shown now = island == null ? null : new Shown(island.id(), island.sizeChunks());
        Shown before = shown.get(player.getUniqueId());
        if (now == null) {
            if (before != null) {
                shown.remove(player.getUniqueId());
                player.setWorldBorder(null);
            }
            return;
        }
        if (now.equals(before)) {
            return;
        }
        shown.put(player.getUniqueId(), now);
        player.setWorldBorder(border(island.bounds()));
    }

    /** Re-sends the border to players in the islands world (after {@code island} was created or resized). */
    void refresh(Island island) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!islands.isIslandWorld(player.getWorld())) {
                continue;
            }
            Shown seen = shown.get(player.getUniqueId());
            if (seen != null && seen.island().equals(island.id())) {
                shown.remove(player.getUniqueId());
            }
            update(player);
        }
    }

    /** Removes every border this module sent (module disable). */
    void clearAll() {
        for (UUID id : shown.keySet()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                player.setWorldBorder(null);
            }
        }
        shown.clear();
    }

    static WorldBorder border(Bounds bounds) {
        WorldBorder border = Bukkit.createWorldBorder();
        border.setCenter(bounds.centerX(), bounds.centerZ());
        border.setSize(bounds.width());
        border.setWarningDistance(0);
        border.setWarningTimeTicks(0);
        border.setDamageAmount(0);
        border.setDamageBuffer(0);
        return border;
    }

    private void later(Player player) {
        islands.core().scheduler().later("island-border", 1L, () -> {
            if (player.isOnline()) {
                update(player);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        later(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        later(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        later(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        later(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (IslandGrid.cell(from.getBlockX()) != IslandGrid.cell(to.getBlockX())
                || IslandGrid.cell(from.getBlockZ()) != IslandGrid.cell(to.getBlockZ())) {
            later(event.getPlayer());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        shown.remove(event.getPlayer().getUniqueId());
    }
}
