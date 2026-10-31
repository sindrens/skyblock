package net.skybornmc.skyblock.production;

import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * When each online player last did something real (PHASE-7 M7.1, R5 / rule 9): break or place a block, hit a mob,
 * harvest, fish, use a menu, chat, or walk {@code fuel.walk-blocks} blocks (not in water, a vehicle or a bubble column).
 * Turning the camera or standing still does not count, so standing AFK on the island burns fuel. Handlers only write a
 * timestamp, at most once a second per player.
 */
final class ActivityTracker implements Listener {

    private static final long MIN_GAP_MS = 1_000;

    private final Map<UUID, Long> last = new ConcurrentHashMap<>();
    /** Where the current walk started (x, z), per player; main thread only. */
    private final Map<UUID, double[]> anchors = new ConcurrentHashMap<>();
    private final Consumer<Player> onAction;
    private volatile int walkBlocks = 5;

    /** @param onAction runs on the main thread after a counted action (wakes an asleep island right away) */
    ActivityTracker(Consumer<Player> onAction) {
        this.onAction = onAction;
    }

    void walkBlocks(int blocks) {
        this.walkBlocks = Math.max(1, blocks);
    }

    /** Last real action in epoch millis (0 = none since joining). */
    long lastAction(UUID player) {
        return last.getOrDefault(player, 0L);
    }

    /** True when the player did something real in the last {@code windowMs}. */
    boolean activeWithin(UUID player, long now, long windowMs) {
        long at = lastAction(player);
        return at > 0 && now - at < windowMs;
    }

    /** Records an action; true when it was recorded (not within a second of the last one). */
    static boolean record(Map<UUID, Long> last, UUID player, long now) {
        Long before = last.get(player);
        if (before != null && now - before < MIN_GAP_MS) {
            return false;
        }
        last.put(player, now);
        return true;
    }

    /** True once the walk from the anchor reaches {@code blocks} (horizontal distance). */
    static boolean walked(double fromX, double fromZ, double toX, double toZ, int blocks) {
        double dx = toX - fromX;
        double dz = toZ - fromZ;
        return dx * dx + dz * dz >= (double) blocks * blocks;
    }

    private void touch(Player p) {
        if (record(last, p.getUniqueId(), System.currentTimeMillis())) {
            onAction.accept(p);
        }
    }

    void forget(UUID player) {
        last.remove(player);
        anchors.remove(player);
    }

    // ------------------------------------------------------------------ events (main thread unless noted)

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        Location from = event.getFrom();
        if (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        Player p = event.getPlayer();
        UUID id = p.getUniqueId();
        if (p.isInsideVehicle() || p.isInWater() || p.isFlying() || p.isGliding()
                || to.getBlock().getType() == Material.BUBBLE_COLUMN) {
            anchors.remove(id); // carried or swimming: start over once back on foot
            return;
        }
        double[] anchor = anchors.get(id);
        if (anchor == null) {
            anchors.put(id, new double[]{to.getX(), to.getZ()});
            return;
        }
        if (walked(anchor[0], anchor[1], to.getX(), to.getZ(), walkBlocks)) {
            anchor[0] = to.getX();
            anchor[1] = to.getZ();
            touch(p);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        touch(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        touch(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player p) {
            touch(p);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHarvest(PlayerHarvestBlockEvent event) {
        touch(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        touch(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player p) {
            touch(p);
        }
    }

    /** Async: only the timestamp here; the wake-up check hops to the main thread. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player p = event.getPlayer();
        if (record(last, p.getUniqueId(), System.currentTimeMillis())) {
            wakeLater.accept(p);
        }
    }

    private volatile Consumer<Player> wakeLater = p -> { };

    /** How an async chat action reaches the main thread (set by the engine). */
    void wakeLater(Consumer<Player> hop) {
        this.wakeLater = hop == null ? p -> { } : hop;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        forget(event.getPlayer().getUniqueId());
    }
}
