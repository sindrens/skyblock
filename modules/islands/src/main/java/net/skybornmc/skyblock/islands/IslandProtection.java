package net.skybornmc.skyblock.islands;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Basic island protection for M2.1 (safest default until M2.2): in the islands world only members of an island may
 * break or place blocks (and use buckets) inside that island's border; nobody builds outside a border. Staff with
 * {@code skyblock.island.bypass} may build anywhere.
 *
 * <p><b>Extension point (M2.2):</b> {@link #allows} is the single decision. M2.2 replaces its body with the role
 * permission matrix, visitor settings, the Y 0-256 limit and the staff bypass toggle, and adds the other {@link Action}s
 * (interact, containers, …) plus cross-border rules (pistons, liquids, explosions) as new handlers.</p>
 *
 * <p>Also handles void falls: below {@code void.teleport-below-y} a player goes back to an island home without damage
 * (WORLD.md §3).</p>
 */
final class IslandProtection implements Listener {

    /** Protected actions (M2.2 adds interact, containers, redstone, animals, …). */
    enum Action { BREAK, PLACE }

    private final IslandsModule module;
    private final IslandManager islands;
    private final Map<UUID, Long> lastMessage = new HashMap<>();

    IslandProtection(IslandsModule module, IslandManager islands) {
        this.module = module;
        this.islands = islands;
    }

    /** True if {@code player} may do {@code action} at {@code location}. Only the islands world is protected. */
    boolean allows(Player player, Location location, Action action) {
        if (location.getWorld() == null || !islands.isIslandWorld(location.getWorld())) {
            return true;
        }
        if (player.hasPermission(IslandsModule.PERM_BYPASS)) {
            return true;
        }
        Island island = islands.islandAt(location.getBlockX(), location.getBlockZ());
        return island != null && island.role(player.getUniqueId()).isPresent();
    }

    private void check(Player player, Block block, Action action, Cancellable event) {
        if (!allows(player, block.getLocation(), action)) {
            event.setCancelled(true);
            deny(player, block.getLocation());
        }
    }

    private void deny(Player player, Location location) {
        long now = System.currentTimeMillis();
        Long last = lastMessage.get(player.getUniqueId());
        if (last != null && now - last < module.config().protectionMessageCooldownMs) {
            return;
        }
        lastMessage.put(player.getUniqueId(), now);
        Island island = islands.islandAt(location.getBlockX(), location.getBlockZ());
        module.msg().actionBar(player, island == null ? "protection.outside" : "protection.not-member");
        module.core().sounds().play(player, "error");
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        check(event.getPlayer(), event.getBlock(), Action.BREAK, event);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        check(event.getPlayer(), event.getBlock(), Action.PLACE, event);
        if (!event.isCancelled() && event instanceof BlockMultiPlaceEvent multi) {
            for (var state : multi.getReplacedBlockStates()) {
                if (!allows(event.getPlayer(), state.getLocation(), Action.PLACE)) {
                    event.setCancelled(true);
                    deny(event.getPlayer(), state.getLocation());
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        check(event.getPlayer(), event.getBlock(), Action.PLACE, event);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        check(event.getPlayer(), event.getBlock(), Action.BREAK, event);
    }

    // ------------------------------------------------------------------ void falls

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to.getY() >= module.config().voidY || event.getFrom().getY() < to.getY() || !islands.isIslandWorld(to.getWorld())) {
            return;
        }
        islands.rescueFromVoid(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onVoidDamage(EntityDamageEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.VOID && event.getEntity() instanceof Player player
                && islands.isIslandWorld(player.getWorld())) {
            event.setCancelled(true);
            islands.rescueFromVoid(player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastMessage.remove(event.getPlayer().getUniqueId());
    }
}
