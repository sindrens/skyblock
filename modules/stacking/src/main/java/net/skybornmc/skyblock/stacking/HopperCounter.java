package net.skybornmc.skyblock.stacking;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.island.IslandService;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Hoppers per chunk (owner F13): a cached count per loaded chunk in the stacking worlds and a rate-limited staff alert
 * when a chunk passes {@code hoppers.alert-above} (no hard cap).
 *
 * <p>Cost: a chunk is counted once when it loads, from its block entities only (never a block scan; new chunks are
 * skipped), then kept up to date by place, break, explosion and entity-change events. Nothing runs per tick. Pistons can't
 * move hoppers (block entities are immovable), so they need no listener. Edits that bypass events (world edit, island
 * reset) can make the cache drift until the chunk reloads, so a chunk is recounted exactly from its block entities
 * before it alerts.</p>
 */
final class HopperCounter implements Listener {

    static final String PERM_ALERTS = "skyblock.stacking.hopper-alerts";

    private final StackingModule module;
    private final HopperCounts counts = new HopperCounts();
    private final HopperAlerts alerts = new HopperAlerts();

    HopperCounter(StackingModule module) {
        this.module = module;
    }

    private boolean active(World world) {
        return module.config().hopperCounterEnabled && module.eligible(world);
    }

    /** Exact hopper count of a loaded chunk from its block entities (no block scan, no snapshots). */
    static int countIn(Chunk chunk) {
        return chunk.getTileEntities(block -> block.getType() == Material.HOPPER, false).size();
    }

    /** Counts the chunks that are already loaded (late enable, reload, counter switched on). */
    void loadAll() {
        counts.clear();
        for (World world : Bukkit.getWorlds()) {
            if (!active(world)) {
                continue;
            }
            for (Chunk chunk : world.getLoadedChunks()) {
                counts.set(world.getName(), HopperCounts.key(chunk.getX(), chunk.getZ()), countIn(chunk));
            }
        }
    }

    void clear() {
        counts.clear();
        alerts.clear();
    }

    /** Hoppers in the chunk at {@code location} (cached; 0 for unknown chunks). */
    int count(Location location) {
        if (location.getWorld() == null) {
            return 0;
        }
        return counts.get(location.getWorld().getName(), HopperCounts.key(location.getBlockX() >> 4, location.getBlockZ() >> 4));
    }

    /** The loaded chunk with the most hoppers inside the block rectangle (both corners inclusive). */
    Optional<HopperCounts.Hotspot> busiest(String world, int minX, int minZ, int maxX, int maxZ) {
        int cx1 = minX >> 4;
        int cz1 = minZ >> 4;
        int cx2 = maxX >> 4;
        int cz2 = maxZ >> 4;
        return counts.busiest(world, (x, z) -> x >= cx1 && x <= cx2 && z >= cz1 && z <= cz2);
    }

    // ------------------------------------------------------------------ chunk lifecycle

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (event.isNewChunk() || !active(event.getWorld())) {
            return; // a freshly generated chunk has no hoppers
        }
        long start = module.perf().start();
        try {
            Chunk chunk = event.getChunk();
            counts.set(event.getWorld().getName(), HopperCounts.key(chunk.getX(), chunk.getZ()), countIn(chunk));
        } finally {
            module.perf().end(start);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();
        counts.unload(event.getWorld().getName(), HopperCounts.key(chunk.getX(), chunk.getZ()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldUnload(WorldUnloadEvent event) {
        counts.unloadWorld(event.getWorld().getName());
    }

    // ------------------------------------------------------------------ changes

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (block.getType() == Material.HOPPER && active(block.getWorld())) {
            changed(block, 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() == Material.HOPPER && active(block.getWorld())) {
            changed(block, -1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        exploded(event.getLocation().getWorld(), event.blockList());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        exploded(event.getBlock().getWorld(), event.blockList());
    }

    /** Withers and other mobs that break or replace blocks. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        Block block = event.getBlock();
        if (block.getType() == Material.HOPPER && event.getTo() != Material.HOPPER && active(block.getWorld())) {
            changed(block, -1);
        }
    }

    private void exploded(World world, List<Block> blocks) {
        if (blocks.isEmpty() || !active(world)) {
            return;
        }
        for (Block block : blocks) {
            if (block.getType() == Material.HOPPER) {
                changed(block, -1);
            }
        }
    }

    private void changed(Block block, int delta) {
        StackingConfig c = module.config();
        String world = block.getWorld().getName();
        long key = HopperCounts.key(block.getX() >> 4, block.getZ() >> 4);
        int before = counts.get(world, key);
        int after = counts.add(world, key, delta);
        if (delta > 0 && HopperAlerts.rises(before, after, c.hopperAlertAbove, c.hopperAlertStep)) {
            verifyAndAlert(block, world, key);
        }
    }

    /** Recounts the chunk exactly (rare: only when a new alert level is reached) and alerts staff if it holds. */
    private void verifyAndAlert(Block block, String world, long key) {
        StackingConfig c = module.config();
        int exact = countIn(block.getChunk());
        counts.set(world, key, exact);
        if (!alerts.tryAlert(world, key, exact, c.hopperAlertAbove, c.hopperAlertStep, c.hopperAlertCooldownMinutes * 60_000L,
                c.hopperAlertsPerMinute, System.currentTimeMillis())) {
            return;
        }
        alert(block, exact);
    }

    private String raw(String key) {
        String text = module.core().messages().raw(StackingModule.ID, key);
        return text == null ? "-" : text;
    }

    private void alert(Block block, int count) {
        StackingConfig c = module.config();
        Location at = block.getLocation();
        Optional<IslandService.IslandInfo> island = module.islands().flatMap(s -> s.islandAt(at));
        String islandName = island.map(IslandService.IslandInfo::name).orElse(raw("hoppers.no-island"));
        String owner = island.map(i -> Bukkit.getOfflinePlayer(i.owner()).getName()).orElse(null);
        if (owner == null) {
            owner = raw("hoppers.no-owner");
        }
        var tags = new net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[]{
                Msg.p("count", count), Msg.p("threshold", c.hopperAlertAbove), Msg.p("island", islandName), Msg.p("owner", owner),
                Msg.p("world", at.getWorld().getName()), Msg.p("x", at.getBlockX()), Msg.p("y", at.getBlockY() + 1),
                Msg.p("z", at.getBlockZ()), Msg.p("chunk-x", at.getBlockX() >> 4), Msg.p("chunk-z", at.getBlockZ() >> 4)};
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (!staff.hasPermission(PERM_ALERTS)) {
                continue;
            }
            boolean bedrock = module.core().bedrock().isBedrock(staff.getUniqueId());
            // GUI-5: Bedrock staff can't click [Teleport]: they read the coordinates and also get a popup that runs it.
            module.core().menus().offers().offer(staff, net.skybornmc.skyblock.common.menu.Offer.builder(module.msg().module(), "hoppers.popup")
                    .id("hoppers:alert")
                    .icon(org.bukkit.Material.HOPPER)
                    .placeholders(tags)
                    .button(net.skybornmc.skyblock.common.menu.Offer.Button.teleport("tppos " + at.getBlockX() + " " + (at.getBlockY() + 1)
                            + " " + at.getBlockZ() + " " + at.getWorld().getName()))
                    .chat(viewer -> module.msg().send(viewer, bedrock ? "hoppers.alert-bedrock" : "hoppers.alert", tags))
                    .build());
        }
        module.core().plugin().getLogger().info("Stacking: chunk " + (at.getBlockX() >> 4) + "," + (at.getBlockZ() >> 4) + " in "
                + at.getWorld().getName() + " has " + count + " hoppers (alert above " + c.hopperAlertAbove + "), island "
                + islandName + " of " + owner + " (F13).");
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("threshold", c.hopperAlertAbove);
        details.put("chunk_x", at.getBlockX() >> 4);
        details.put("chunk_z", at.getBlockZ() >> 4);
        details.put("island_owner", owner);
        module.core().audit().log(AuditCategory.ISLAND, "hopper_alert", Actor.system("hoppers"),
                island.map(i -> i.id().toString()).orElse(null), islandName, (long) count, at, details);
    }
}
