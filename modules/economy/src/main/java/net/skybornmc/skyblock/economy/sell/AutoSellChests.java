package net.skybornmc.skyblock.economy.sell;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.db.Sql;
import net.skybornmc.skyblock.common.island.IslandService;
import net.skybornmc.skyblock.common.item.ItemKeys;
import net.skybornmc.skyblock.common.profile.ProfileService;
import net.skybornmc.skyblock.common.sell.SellRequest;
import net.skybornmc.skyblock.common.sell.SellResult;
import net.skybornmc.skyblock.common.sell.SellService;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Auto-Sell Chests (owner 4 Nov 2026, "Coming soon" built for real; bought on the Black Market): a chest placed on your
 * island sells what is in it every {@code autosell.interval-seconds} at the normal /sell prices (hoppers can feed it). The
 * money goes to the profile that placed it. At most {@code autosell.max-per-island} per island. Breaking it gives the
 * chest item back. Balance: the same, already lowered prices of auto-farm crops apply, so big farms stay modest.
 *
 * <p>Positions live in {@code economy_autosell_chests} (loaded at start, small). The timer only sells chests whose chunk
 * is loaded; items leave the chest before the sale and unsold ones go back (as /sell).</p>
 */
public final class AutoSellChests implements Listener {

    record Pos(String world, int x, int y, int z) {
        static Pos of(Block b) {
            return new Pos(b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
        }
    }

    record Placed(Pos pos, UUID island, UUID profile, UUID player) {
    }

    private final Core core;
    private final ModuleMessages msg;
    private final int intervalSeconds;
    private final int maxPerIsland;
    private final Map<Pos, Placed> chests = new ConcurrentHashMap<>();
    private Scheduler.Task task;

    public AutoSellChests(Core core, ModuleMessages msg, int intervalSeconds, int maxPerIsland) {
        this.core = core;
        this.msg = msg;
        this.intervalSeconds = Math.max(5, intervalSeconds);
        this.maxPerIsland = Math.max(1, maxPerIsland);
    }

    /** A new Auto-Sell Chest item. */
    public static ItemStack create(Core core) {
        return net.skybornmc.skyblock.common.sell.AutoSellChestItem.create(core);
    }

    static boolean isItem(ItemStack item) {
        return net.skybornmc.skyblock.common.sell.AutoSellChestItem.isItem(item);
    }

    public void start() {
        core.database().async(c -> Sql.query(c, "SELECT world, x, y, z, island_id, owner_profile, owner_player FROM economy_autosell_chests",
                rs -> new Placed(new Pos(rs.getString("world"), rs.getInt("x"), rs.getInt("y"), rs.getInt("z")),
                        Sql.uuid(rs, "island_id"), Sql.uuid(rs, "owner_profile"), Sql.uuid(rs, "owner_player"))))
                .whenComplete((rows, error) -> {
                    if (error != null) {
                        core.plugin().getLogger().warning("Auto-Sell Chests could not be loaded: " + error.getMessage());
                        return;
                    }
                    rows.forEach(r -> chests.put(r.pos(), r));
                });
        task = core.scheduler().repeat("economy:autosell", 20L * intervalSeconds, 20L * intervalSeconds, this::sellAll);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
        }
    }

    /** Pure: may another chest be placed on an island that has {@code placed} already. */
    static boolean roomFor(long placed, int max) {
        return placed < max;
    }

    // ------------------------------------------------------------------ place / break

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!isItem(event.getItemInHand())) {
            return;
        }
        Player p = event.getPlayer();
        Block b = event.getBlockPlaced();
        Optional<IslandService> islands = core.services().optional(IslandService.class);
        UUID profile = core.services().optional(ProfileService.class).map(s -> s.profileKey(p)).orElse(p.getUniqueId());
        Optional<IslandService.IslandInfo> island = islands.flatMap(i -> i.islandAt(b.getLocation()));
        if (island.isEmpty() || !islands.get().isMember(island.get().id(), profile)) {
            event.setCancelled(true);
            msg.send(p, "autosell.not-island");
            return;
        }
        UUID islandId = island.get().id();
        long here = chests.values().stream().filter(c -> c.island().equals(islandId)).count();
        if (!roomFor(here, maxPerIsland)) {
            event.setCancelled(true);
            msg.send(p, "autosell.limit", Msg.number("max", maxPerIsland));
            return;
        }
        if (b.getBlockData() instanceof org.bukkit.block.data.type.Chest data && data.getType() != org.bukkit.block.data.type.Chest.Type.SINGLE) {
            event.setCancelled(true); // never joins a normal chest into a double chest
            msg.send(p, "autosell.not-double");
            return;
        }
        Placed placed = new Placed(Pos.of(b), islandId, profile, p.getUniqueId());
        chests.put(placed.pos(), placed);
        long now = System.currentTimeMillis();
        core.database().transaction(c -> {
            Sql.update(c, "DELETE FROM economy_autosell_chests WHERE world = ? AND x = ? AND y = ? AND z = ?",
                    placed.pos().world(), placed.pos().x(), placed.pos().y(), placed.pos().z());
            Sql.update(c, "INSERT INTO economy_autosell_chests (world, x, y, z, island_id, owner_profile, owner_player, placed_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)", placed.pos().world(), placed.pos().x(), placed.pos().y(), placed.pos().z(),
                    Sql.toBytes(islandId), Sql.toBytes(profile), Sql.toBytes(p.getUniqueId()), now);
            return null;
        });
        msg.send(p, "autosell.placed", Msg.number("seconds", intervalSeconds));
        core.audit().logPlayer(AuditCategory.ECONOMY, "autosell_chest_place", Actor.player(p), p.getUniqueId(), p.getName(), null,
                b.getLocation(), Map.of("island", islandId.toString()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Pos pos = Pos.of(event.getBlock());
        Placed placed = chests.remove(pos);
        if (placed == null) {
            return;
        }
        event.setDropItems(false); // the chest itself comes back as the Auto-Sell Chest item; the contents drop as usual
        Block b = event.getBlock();
        if (b.getState() instanceof Chest chest) {
            for (ItemStack item : chest.getBlockInventory().getContents()) {
                if (item != null && !item.isEmpty()) {
                    b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), item);
                }
            }
            chest.getBlockInventory().clear();
        }
        b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), create(core));
        core.database().transaction(c -> Sql.update(c, "DELETE FROM economy_autosell_chests WHERE world = ? AND x = ? AND y = ? AND z = ?",
                pos.world(), pos.x(), pos.y(), pos.z()));
    }

    // ------------------------------------------------------------------ the timer

    private void sellAll() {
        Optional<SellService> sell = core.services().optional(SellService.class);
        if (sell.isEmpty() || chests.isEmpty()) {
            return;
        }
        for (Placed placed : List.copyOf(chests.values())) {
            World world = Bukkit.getWorld(placed.pos().world());
            if (world == null || !world.isChunkLoaded(placed.pos().x() >> 4, placed.pos().z() >> 4)) {
                continue;
            }
            Block b = world.getBlockAt(placed.pos().x(), placed.pos().y(), placed.pos().z());
            if (!(b.getState() instanceof Chest chest)) {
                continue; // gone some other way (explosion, staff): nothing to sell
            }
            Inventory inv = chest.getBlockInventory();
            List<ItemStack> taken = new ArrayList<>();
            ItemStack[] contents = inv.getContents();
            for (int i = 0; i < contents.length; i++) {
                if (contents[i] != null && !contents[i].isEmpty()) {
                    taken.add(contents[i].clone());
                    inv.setItem(i, null);
                }
            }
            if (taken.isEmpty()) {
                continue;
            }
            SellRequest request = new SellRequest(placed.player(), null, placed.profile(), taken, 0, "autochest",
                    Actor.system("autosell_chest"));
            Location at = b.getLocation();
            sell.get().sell(request).whenComplete((result, error) -> Bukkit.getScheduler().runTask(core.plugin(), () -> {
                List<ItemStack> back = error != null || result == null ? taken : result.giveBack();
                Block now = at.getBlock();
                Inventory target = now.getState() instanceof Chest c ? c.getBlockInventory() : null;
                for (ItemStack item : back) {
                    Map<Integer, ItemStack> rest = target == null ? Map.of(0, item) : target.addItem(item);
                    rest.values().forEach(r -> at.getWorld().dropItemNaturally(at.clone().add(0.5, 1, 0.5), r));
                }
                if (result != null && result.status() == SellResult.Status.SOLD) {
                    Player owner = Bukkit.getPlayer(placed.player());
                    if (owner != null) {
                        net.skybornmc.skyblock.common.hud.ActionBars.show(owner, "autosell-chest", msg.component(owner, "autosell.sold",
                                Msg.number("items", result.quote().items()), Msg.p("amount", net.skybornmc.skyblock.common.util.Format.money(result.quote().total()))),
                                net.skybornmc.skyblock.common.hud.ActionBars.PAYOUT, 2500);
                    }
                }
            }));
        }
    }
}
