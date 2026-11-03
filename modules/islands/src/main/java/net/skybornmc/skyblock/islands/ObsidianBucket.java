package net.skybornmc.skyblock.islands;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Feedback 31 Oct 2026 ("right click obsidian with bucket to get lava bucket, for people who build cobble gen wrong and
 * lose lava"): an empty bucket on obsidian on an island turns it back into a lava bucket.
 *
 * <p>Only in the islands world, only on an island, and only where the player may break blocks: the click goes through a
 * {@link BlockBreakEvent} (no drops), so island roles, bans, borders and staff bypass decide exactly as for mining, and the
 * island worth sees the obsidian leave. A short cooldown and an hourly cap ({@code islands.yml obsidian-bucket.*}) bound the
 * (cheap) obsidian-to-lava loop. Main thread.</p>
 */
final class ObsidianBucket implements Listener {

    private final IslandsModule module;
    private final IslandManager islands;
    /** Player → recent uses (epoch ms), oldest first; trimmed to the last hour. */
    private final Map<UUID, Deque<Long>> uses = new HashMap<>();

    ObsidianBucket(IslandsModule module, IslandManager islands) {
        this.module = module;
        this.islands = islands;
    }

    /** Pure: why a use at {@code now} is refused ("cooldown", "limit") or null when allowed; drops entries older than an hour. */
    static String refusal(Deque<Long> recent, long now, long cooldownMs, int maxPerHour) {
        while (!recent.isEmpty() && recent.peekFirst() <= now - 3_600_000L) {
            recent.pollFirst();
        }
        if (!recent.isEmpty() && now - recent.peekLast() < cooldownMs) {
            return "cooldown";
        }
        if (maxPerHour > 0 && recent.size() >= maxPerHour) {
            return "limit";
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        IslandsConfig config = module.config();
        Block block = event.getClickedBlock();
        ItemStack item = event.getItem();
        if (!config.obsidianBucketEnabled || event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND
                || block == null || block.getType() != Material.OBSIDIAN || item == null || item.getType() != Material.BUCKET
                || item.getAmount() != 1 || custom(item)) {
            return;
        }
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.SPECTATOR || !player.hasPermission("skyblock.island.obsidianbucket")
                || !islands.isIslandWorld(block.getWorld()) || islands.islandAt(block.getX(), block.getZ()) == null) {
            return;
        }
        event.setCancelled(true); // never also place/use the bucket this click
        Deque<Long> recent = uses.computeIfAbsent(player.getUniqueId(), k -> new ArrayDeque<>());
        long now = System.currentTimeMillis();
        String refused = refusal(recent, now, config.obsidianBucketCooldownSeconds * 1000L, config.obsidianBucketMaxPerHour);
        if (refused != null) {
            module.msg().actionBar(player, "obsidian." + refused);
            return;
        }
        BlockBreakEvent check = new BlockBreakEvent(block, player);
        check.setDropItems(false);
        check.setExpToDrop(0);
        Bukkit.getPluginManager().callEvent(check);
        if (check.isCancelled() || block.getType() != Material.OBSIDIAN) {
            return; // island protection already told the player why
        }
        block.setType(Material.AIR);
        player.getInventory().setItemInMainHand(new ItemStack(Material.LAVA_BUCKET));
        recent.addLast(now);
        player.getWorld().playSound(block.getLocation().toCenterLocation(), Sound.ITEM_BUCKET_FILL_LAVA, 1f, 1f);
        player.getWorld().spawnParticle(Particle.LAVA, block.getLocation().toCenterLocation(), 6, 0.3, 0.3, 0.3);
        module.msg().actionBar(player, "obsidian.done");
        Island island = islands.islandAt(block.getX(), block.getZ());
        module.core().audit().logPlayer(AuditCategory.ISLAND, "obsidian_bucket", Actor.player(player), player.getUniqueId(),
                player.getName(), 1L, block.getLocation(), Map.of("island", island == null ? "" : island.id().toString()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        uses.remove(event.getPlayer().getUniqueId());
    }

    /** Our own items (keys, tools, menu items) carry plugin data; only a plain vanilla bucket counts. */
    private static boolean custom(ItemStack item) {
        return item.hasItemMeta() && !item.getItemMeta().getPersistentDataContainer().getKeys().isEmpty();
    }
}
