package net.skybornmc.skyblock.stacking;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.db.Sql;
import net.skybornmc.skyblock.common.island.IslandService;
import net.skybornmc.skyblock.common.profile.ProfileService;
import net.skybornmc.skyblock.common.stacking.ChunkCollectorItem;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Hopper;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chunk Collectors (owner 4 Nov 2026, "Coming soon" built for real; bought on the Black Market): a hopper placed on your
 * island catches every item that drops in its chunk before it lies on the ground (crop farms, spawners, mob grinders) and
 * passes it on like a hopper (into the chest under it). One per chunk, at most {@code collectors.max-per-island} per
 * island. Items players drop or throw are never caught. Breaking it gives the collector back.
 *
 * <p>Runs before the item stacker (LOW): a caught item never spawns. What does not fit stays on the ground. Positions:
 * {@code stacking_chunk_collectors} (loaded at start, small).</p>
 */
final class ChunkCollectors implements Listener {

    record Pos(String world, int x, int y, int z) {
        static Pos of(Block b) {
            return new Pos(b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
        }

        long chunk() {
            return chunkKey(x >> 4, z >> 4);
        }
    }

    record Placed(Pos pos, UUID island, UUID player) {
    }

    private final StackingModule module;
    /** world name → chunk key → collector. */
    private final Map<String, Map<Long, Placed>> byChunk = new ConcurrentHashMap<>();

    ChunkCollectors(StackingModule module) {
        this.module = module;
    }

    static long chunkKey(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xffffffffL);
    }

    void load() {
        module.core().database().async(c -> Sql.query(c, "SELECT world, x, y, z, island_id, owner_player FROM stacking_chunk_collectors",
                rs -> new Placed(new Pos(rs.getString("world"), rs.getInt("x"), rs.getInt("y"), rs.getInt("z")),
                        Sql.uuid(rs, "island_id"), Sql.uuid(rs, "owner_player")))).whenComplete((rows, error) -> {
            if (error != null) {
                module.core().plugin().getLogger().warning("Chunk Collectors could not be loaded: " + error.getMessage());
                return;
            }
            rows.forEach(this::track);
        });
    }

    private void track(Placed p) {
        byChunk.computeIfAbsent(p.pos().world(), k -> new ConcurrentHashMap<>()).put(p.pos().chunk(), p);
    }

    private long onIsland(UUID island) {
        return byChunk.values().stream().flatMap(m -> m.values().stream()).filter(p -> p.island().equals(island)).count();
    }

    // ------------------------------------------------------------------ catching drops

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSpawn(ItemSpawnEvent event) {
        Item item = event.getEntity();
        if (item.getThrower() != null || !item.canPlayerPickup()) {
            return; // dropped or thrown by a player, or a display item of another plugin
        }
        Map<Long, Placed> world = byChunk.get(item.getWorld().getName());
        if (world == null) {
            return;
        }
        Placed p = world.get(chunkKey(item.getLocation().getBlockX() >> 4, item.getLocation().getBlockZ() >> 4));
        if (p == null) {
            return;
        }
        Block b = item.getWorld().getBlockAt(p.pos().x(), p.pos().y(), p.pos().z());
        if (!(b.getState(false) instanceof Hopper hopper)) {
            return;
        }
        ItemStack stack = item.getItemStack();
        Map<Integer, ItemStack> rest = hopper.getInventory().addItem(stack.clone());
        if (rest.isEmpty()) {
            event.setCancelled(true);
        } else {
            item.setItemStack(rest.values().iterator().next());
        }
    }

    // ------------------------------------------------------------------ place / break

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!ChunkCollectorItem.isItem(event.getItemInHand())) {
            return;
        }
        Player player = event.getPlayer();
        Block b = event.getBlockPlaced();
        Optional<IslandService> islands = module.core().services().optional(IslandService.class);
        UUID profile = module.core().services().optional(ProfileService.class).map(s -> s.profileKey(player)).orElse(player.getUniqueId());
        Optional<IslandService.IslandInfo> island = islands.flatMap(i -> i.islandAt(b.getLocation()));
        if (island.isEmpty() || !islands.get().isMember(island.get().id(), profile)) {
            event.setCancelled(true);
            module.msg().send(player, "collector.not-island");
            return;
        }
        Pos pos = Pos.of(b);
        Map<Long, Placed> world = byChunk.getOrDefault(pos.world(), Map.of());
        if (world.containsKey(pos.chunk())) {
            event.setCancelled(true);
            module.msg().send(player, "collector.one-per-chunk");
            return;
        }
        int max = module.config().collectorsPerIsland;
        if (onIsland(island.get().id()) >= max) {
            event.setCancelled(true);
            module.msg().send(player, "collector.limit", Msg.number("max", max));
            return;
        }
        Placed placed = new Placed(pos, island.get().id(), player.getUniqueId());
        track(placed);
        long now = System.currentTimeMillis();
        module.core().database().transaction(c -> {
            Sql.update(c, "DELETE FROM stacking_chunk_collectors WHERE world = ? AND x = ? AND y = ? AND z = ?", pos.world(), pos.x(), pos.y(), pos.z());
            return Sql.update(c, "INSERT INTO stacking_chunk_collectors (world, x, y, z, island_id, owner_player, placed_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    pos.world(), pos.x(), pos.y(), pos.z(), Sql.toBytes(island.get().id()), Sql.toBytes(player.getUniqueId()), now);
        });
        module.msg().send(player, "collector.placed");
        module.core().audit().logPlayer(AuditCategory.ITEM, "chunk_collector_place", Actor.player(player), player.getUniqueId(),
                player.getName(), null, b.getLocation(), Map.of("island", island.get().id().toString()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Pos pos = Pos.of(event.getBlock());
        Map<Long, Placed> world = byChunk.get(pos.world());
        Placed placed = world == null ? null : world.get(pos.chunk());
        if (placed == null || !placed.pos().equals(pos)) {
            return;
        }
        world.remove(pos.chunk());
        event.setDropItems(false);
        Block b = event.getBlock();
        if (b.getState() instanceof Hopper hopper) {
            for (ItemStack item : hopper.getInventory().getContents()) {
                if (item != null && !item.isEmpty()) {
                    b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), item);
                }
            }
            hopper.getInventory().clear();
        }
        World w = b.getWorld();
        Bukkit.getScheduler().runTask(module.core().plugin(), () ->
                w.dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), ChunkCollectorItem.create(module.core())));
        module.core().database().transaction(c -> Sql.update(c, "DELETE FROM stacking_chunk_collectors WHERE world = ? AND x = ? AND y = ? AND z = ?",
                pos.world(), pos.x(), pos.y(), pos.z()));
    }
}
