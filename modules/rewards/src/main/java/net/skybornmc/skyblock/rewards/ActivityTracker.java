package net.skybornmc.skyblock.rewards;

import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * When each online player last did something themselves (PHASE-10 M10.6: "AFK = no action for 5 minutes"). Turning the
 * camera, walking on the ground, chatting, commands, clicking, breaking, placing and hitting count. Being pushed by
 * water, riding or standing still does not, so AFK machines don't earn playtime. Handlers only write a timestamp, and at
 * most once a second per player.
 */
final class ActivityTracker implements Listener {

    private static final long MIN_GAP_MS = 1_000;

    private final Map<UUID, Long> last = new ConcurrentHashMap<>();

    /** Last own action in epoch millis (0 = unknown). */
    long lastAction(UUID player) {
        return last.getOrDefault(player, 0L);
    }

    void touch(UUID player) {
        touch(player, System.currentTimeMillis());
    }

    void touch(UUID player, long now) {
        Long before = last.get(player);
        if (before == null || now - before >= MIN_GAP_MS) {
            last.put(player, now);
        }
    }

    void forget(UUID player) {
        last.remove(player);
    }

    /** True when a move is the player's own doing: the camera turned, or they walked on solid ground (not in water/vehicles). */
    static boolean ownMove(float fromYaw, float fromPitch, float toYaw, float toPitch, boolean blockChanged, boolean onGroundOutsideWater) {
        return fromYaw != toYaw || fromPitch != toPitch || (blockChanged && onGroundOutsideWater);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        boolean blockChanged = from.getBlockX() != to.getBlockX() || from.getBlockZ() != to.getBlockZ();
        Player p = event.getPlayer();
        if (ownMove(from.getYaw(), from.getPitch(), to.getYaw(), to.getPitch(), blockChanged,
                blockChanged && !p.isInsideVehicle() && !p.isInWater() && ((org.bukkit.entity.Entity) p).isOnGround())) {
            touch(p.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChat(AsyncChatEvent event) {
        touch(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        touch(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent event) {
        touch(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBreak(BlockBreakEvent event) {
        touch(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlace(BlockPlaceEvent event) {
        touch(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClick(InventoryClickEvent event) {
        touch(event.getWhoClicked().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onHit(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player p) {
            touch(p.getUniqueId());
        }
    }
}
