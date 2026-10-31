package net.skybornmc.skyblock.islands;

import net.skybornmc.skyblock.common.island.IslandStat;
import net.skybornmc.skyblock.common.stacking.StackKillEvent;
import net.skybornmc.skyblock.islands.stats.IslandStatsConfig;
import net.skybornmc.skyblock.islands.stats.RecentPlacements;
import net.skybornmc.skyblock.islands.stats.StatsTracker;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Feeds island stats from the game (M2.6). Every handler is O(1) (grid lookup + one in-memory increment) and runs at
 * MONITOR after protection, so cancelled actions never count. Only stored members of the island count (never coop
 * players, visitors or players in creative/spectator mode); generator output and visitors count for the whole island.
 * <ul>
 *   <li>break: ripe crops ({@code crops.ripe-blocks} at max age) and harvest blocks → crops harvested, logs → logs
 *       chopped, anything else → blocks mined. A block the island placed recently ({@code anti-farm.placed-memory}) gives
 *       nothing when broken again (except ripe crops, which grew after planting); right-click harvests (berries) count
 *       as crops harvested;</li>
 *   <li>place → blocks placed; lava + water forming {@code generator.blocks} → generator output;</li>
 *   <li>mob deaths by a member → mobs killed, plus the extra kills of stacked batch kills ({@link StackKillEvent});</li>
 *   <li>playtime: sampled for members standing inside their own island's border.</li>
 * </ul>
 */
final class IslandStatsListener implements Listener {

    private final IslandManager islands;
    private final StatsTracker tracker;
    private final Map<UUID, RecentPlacements> placed = new HashMap<>();
    private Set<Material> ripe = EnumSet.noneOf(Material.class);
    private Set<Material> harvest = EnumSet.noneOf(Material.class);
    private Set<Material> generator = EnumSet.noneOf(Material.class);
    private int placedMemory;

    IslandStatsListener(IslandManager islands, StatsTracker tracker) {
        this.islands = islands;
        this.tracker = tracker;
    }

    void configure(IslandStatsConfig config) {
        ripe = materials(config.ripeBlocks);
        harvest = materials(config.harvestBlocks);
        generator = materials(config.generatorBlocks);
        if (placedMemory != config.placedMemory) {
            placed.clear();
        }
        placedMemory = config.placedMemory;
    }

    private static Set<Material> materials(java.util.List<String> keys) {
        Set<Material> out = EnumSet.noneOf(Material.class);
        for (String key : IslandStatsConfig.keys(keys)) {
            Material m = Material.matchMaterial(key);
            if (m != null && m.isBlock()) {
                out.add(m);
            } else {
                Bukkit.getLogger().warning("[PhoenixSkyblock] island-stats.yml: '" + key + "' is not a block; ignored");
            }
        }
        return out;
    }

    /** Forgets the placed blocks of a removed island. */
    void forget(UUID islandId) {
        placed.remove(islandId);
    }

    private Island islandAt(Location location) {
        if (location.getWorld() == null || !islands.isIslandWorld(location.getWorld())) {
            return null;
        }
        return islands.islandAt(location.getBlockX(), location.getBlockZ());
    }

    /** The player's member key when they count for {@code island} (stored member, survival/adventure), else null. */
    private UUID counting(Island island, Player player) {
        if (island == null || player == null) {
            return null;
        }
        GameMode mode = player.getGameMode();
        if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) {
            return null;
        }
        UUID key = islands.key(player);
        return island.members().containsKey(key) ? key : null;
    }

    private RecentPlacements recent(Island island) {
        return placed.computeIfAbsent(island.id(), id -> new RecentPlacements(placedMemory));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        Island island = islandAt(block.getLocation());
        if (island == null) {
            return;
        }
        if (placedMemory > 0) {
            recent(island).placed(block.getX(), block.getY(), block.getZ());
        }
        UUID member = counting(island, event.getPlayer());
        if (member != null) {
            tracker.record(island.id(), member, IslandStat.BLOCKS_PLACED, 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Island island = islandAt(block.getLocation());
        if (island == null) {
            return;
        }
        RecentPlacements recent = placedMemory > 0 ? placed.get(island.id()) : null;
        boolean ownPlacement = recent != null && recent.brokeOwnPlacement(block.getX(), block.getY(), block.getZ());
        UUID member = counting(island, event.getPlayer());
        if (member == null) {
            return;
        }
        Material type = block.getType();
        IslandStat stat;
        if (ripe.contains(type)) {
            BlockData data = block.getBlockData();
            if (!(data instanceof Ageable ageable) || ageable.getAge() < ageable.getMaximumAge()) {
                return; // unripe crops count for nothing
            }
            stat = IslandStat.CROPS_HARVESTED;
        } else if (ownPlacement) {
            return;
        } else if (harvest.contains(type)) {
            stat = IslandStat.CROPS_HARVESTED;
        } else if (Tag.LOGS.isTagged(type)) {
            stat = IslandStat.LOGS_CHOPPED;
        } else {
            stat = IslandStat.BLOCKS_MINED;
        }
        tracker.record(island.id(), member, stat, 1);
    }

    /** Right-click harvests (sweet berries, glow berries) count as one harvest. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHarvest(PlayerHarvestBlockEvent event) {
        Island island = islandAt(event.getHarvestedBlock().getLocation());
        UUID member = counting(island, event.getPlayer());
        if (member != null) {
            tracker.record(island.id(), member, IslandStat.CROPS_HARVESTED, 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onForm(BlockFormEvent event) {
        Material formed = event.getNewState().getType();
        // PHASE-7 M7.2: a custom generator may have turned the cobblestone into an ore before this listener runs.
        if (!generator.contains(formed) && !net.skybornmc.skyblock.common.production.GeneratorOutput.contains(formed)) {
            return;
        }
        Island island = islandAt(event.getBlock().getLocation());
        if (island != null) {
            tracker.recordIsland(island.id(), IslandStat.GENERATOR_OUTPUT, 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof Player) {
            return;
        }
        Player killer = entity.getKiller();
        if (killer == null) {
            return;
        }
        Island island = islandAt(entity.getLocation());
        UUID member = counting(island, killer);
        if (member != null) {
            tracker.record(island.id(), member, IslandStat.MOBS_KILLED, 1);
        }
    }

    /** Stacked batch kills: the extra mobs that died without their own death event (ENCHANTS §7.2). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onStackKill(StackKillEvent event) {
        if (event.extraKills() <= 0) {
            return;
        }
        Island island = islandAt(event.location());
        UUID member = counting(island, event.killer());
        if (member != null) {
            tracker.record(island.id(), member, IslandStat.MOBS_KILLED, event.extraKills());
        }
    }

    /** Adds {@code seconds} of playtime for every member standing inside their own island's border. */
    void samplePlaytime(int seconds) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getGameMode() == GameMode.SPECTATOR || player.isDead()) {
                continue;
            }
            Island island = islandAt(player.getLocation());
            if (island == null) {
                continue;
            }
            UUID key = islands.key(player);
            if (island.members().containsKey(key)) {
                tracker.record(island.id(), key, IslandStat.PLAYTIME, seconds);
            }
        }
    }
}
