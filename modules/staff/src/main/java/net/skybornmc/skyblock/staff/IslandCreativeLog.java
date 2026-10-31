package net.skybornmc.skyblock.staff;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.island.IslandService;
import net.skybornmc.skyblock.common.staff.StaffModeEvent;
import net.skybornmc.skyblock.common.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * F15: every block a staff member places or breaks in creative on an island during staff mode is written to the audit
 * log (category {@code staff}, actions {@code island_creative_place} / {@code island_creative_break}): batched every
 * {@code island-creative.log-flush-seconds} by {@link CreativeBlockBuffer}, with the island id as target, the world and
 * the first block as location (so {@code /logs near} finds it) and every block's coordinates and material in the details.
 * The audit log itself writes asynchronously. Leaving staff mode writes that staff member's blocks at once.
 */
final class IslandCreativeLog implements Listener {

    private final Core core;
    private final StaffConfig config;
    private final Predicate<Player> inStaffMode;
    private final CreativeBlockBuffer buffer = new CreativeBlockBuffer();
    private Scheduler.Task task;

    IslandCreativeLog(Core core, StaffConfig config, Predicate<Player> inStaffMode) {
        this.core = core;
        this.config = config;
        this.inStaffMode = inStaffMode;
    }

    void start() {
        long period = Math.max(20L, config.creativeLogFlushSeconds * 20L);
        task = core.scheduler().repeat("staff:island-creative-log", period, period, () -> write(buffer.drain(config.creativeLogMaxBlocks)));
    }

    void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        write(buffer.drain(config.creativeLogMaxBlocks));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        record(event.getPlayer(), event.getBlockPlaced(), CreativeBlockBuffer.Action.PLACE);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        record(event.getPlayer(), event.getBlock(), CreativeBlockBuffer.Action.BREAK);
    }

    /** Leaving staff mode (also on quit) writes that staff member's blocks right away. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onStaffMode(StaffModeEvent event) {
        if (!event.entered()) {
            write(buffer.drain(event.getPlayer().getUniqueId(), config.creativeLogMaxBlocks));
        }
    }

    private void record(Player player, Block block, CreativeBlockBuffer.Action action) {
        if (player.getGameMode() != GameMode.CREATIVE || !inStaffMode.test(player)) {
            return;
        }
        Optional<IslandService> islands = core.services().optional(IslandService.class);
        if (islands.isEmpty() || !islands.get().isIslandWorld(block.getWorld())) {
            return;
        }
        Location at = block.getLocation();
        String island = islands.get().islandAt(at).or(() -> islands.get().islandInSlot(at))
                .map(i -> i.id().toString()).orElse("none");
        buffer.add(new CreativeBlockBuffer.Key(player.getUniqueId(), player.getName(), island, block.getWorld().getName(), action),
                new CreativeBlockBuffer.Block(block.getX(), block.getY(), block.getZ(),
                        block.getType().getKey().getKey().toLowerCase(Locale.ROOT)));
    }

    private void write(List<CreativeBlockBuffer.Batch> batches) {
        for (CreativeBlockBuffer.Batch batch : batches) {
            CreativeBlockBuffer.Key key = batch.key();
            CreativeBlockBuffer.Block first = batch.blocks().getFirst();
            World world = Bukkit.getWorld(key.world());
            Location location = world == null ? null : new Location(world, first.x(), first.y(), first.z());
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("island", key.island());
            details.put("world", key.world());
            details.put("count", batch.blocks().size());
            details.put("blocks", batch.described());
            core.audit().log(AuditCategory.STAFF, key.action().audit, Actor.player(key.staff(), key.staffName()), key.island(),
                    null, (long) batch.blocks().size(), location, details);
        }
    }

    /** Blocks waiting for the next write (for tests and diagnostics). */
    int pending() {
        return buffer.size();
    }
}
