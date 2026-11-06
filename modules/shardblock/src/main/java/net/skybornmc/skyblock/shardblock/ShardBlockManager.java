package net.skybornmc.skyblock.shardblock;

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.box.BoxSource;
import net.skybornmc.skyblock.common.box.ItemBox;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.economy.AccountRef;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.economy.EconomyService;
import net.skybornmc.skyblock.common.economy.Transaction;
import net.skybornmc.skyblock.common.economy.TxReason;
import net.skybornmc.skyblock.common.economy.TxResult;
import net.skybornmc.skyblock.common.island.IslandRemovedEvent;
import net.skybornmc.skyblock.common.island.IslandService;
import net.skybornmc.skyblock.common.player.PlayerSaves;
import net.skybornmc.skyblock.common.profile.ProfileService;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import net.skybornmc.skyblock.common.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Placed shard blocks at runtime (ECON-SHARDS step 9 and 10): memory index of every block, place/break/right-click
 * handlers, the one place that pays shards ({@link #collect}), the regrow queue, protection against pistons, explosions,
 * fluids, endermen and fire, the pick-up and the upgrades. Main thread; the database work runs async.
 *
 * <p>Vanilla breaking never happens on a tracked position (H3): the event is always cancelled and the block is swapped by
 * the plugin. The database is the truth: a block without a row is a plain block and pays nothing.</p>
 */
final class ShardBlockManager implements Listener {

    /** Block position. */
    record Pos(String world, int x, int y, int z) {
        static Pos of(Block b) {
            return new Pos(b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
        }

        long chunk() {
            return ((long) (x >> 4) << 32) ^ ((z >> 4) & 0xffffffffL);
        }
    }

    /** One placed block in memory. {@code busy} = a database step for it is running (one at a time). */
    static final class Placed {
        final Pos pos;
        long id;
        final UUID island;
        final UUID placedBy;
        int respawnLevel;
        int doubleLevel;
        long readyAt;
        long breaks;
        long shardsPaid;
        boolean busy;
        boolean removed;

        Placed(Pos pos, long id, UUID island, UUID placedBy, int respawnLevel, int doubleLevel, long readyAt, long breaks, long shardsPaid) {
            this.pos = pos;
            this.id = id;
            this.island = island;
            this.placedBy = placedBy;
            this.respawnLevel = respawnLevel;
            this.doubleLevel = doubleLevel;
            this.readyAt = readyAt;
            this.breaks = breaks;
            this.shardsPaid = shardsPaid;
        }

        boolean ready(long now) {
            return readyAt <= now;
        }
    }

    private record Due(long at, Placed block) {
    }

    private final ShardBlockModule module;
    private final Core core;
    private final ShardBlockStore store;
    private final Map<Pos, Placed> blocks = new HashMap<>();
    private final Map<Long, List<Placed>> byChunk = new HashMap<>();
    private final PriorityQueue<Due> due = new PriorityQueue<>((a, b) -> Long.compare(a.at(), b.at()));
    private final Map<UUID, Long> lastBreak = new HashMap<>();
    private final Map<UUID, Long> lastMessage = new HashMap<>();
    /** Profile → [day, shards paid that day] (cache of the daily cap; the database decides). */
    private final Map<UUID, long[]> daily = new HashMap<>();
    /** Profile → [breaks, shards] of the running audit batch, with the player's name. */
    private final Map<UUID, long[]> auditBatch = new LinkedHashMap<>();
    private final Map<UUID, String> auditNames = new HashMap<>();
    private final List<Scheduler.Task> tasks = new ArrayList<>();
    private volatile boolean loaded;

    ShardBlockManager(ShardBlockModule module, ShardBlockStore store) {
        this.module = module;
        this.core = module.core();
        this.store = store;
    }

    // ------------------------------------------------------------------ lifecycle

    void start() {
        store.loadAll().whenComplete((rows, error) -> core.scheduler().main("shardblock-load", () -> {
            if (error != null) {
                core.plugin().getLogger().log(Level.SEVERE, "Shard blocks: could not load the placed blocks; they pay nothing until a restart",
                        Database.cause(error));
                return;
            }
            for (ShardBlockStore.Row r : rows) {
                Placed p = new Placed(new Pos(r.world(), r.x(), r.y(), r.z()), r.blockId(), r.island(), r.placedBy(), r.respawnLevel(),
                        r.doubleLevel(), ShardBlockStore.READY.equals(r.state()) ? 0 : r.readyAt(), r.breaks(), r.shardsPaid());
                track(p);
                showState(p);
            }
            loaded = true;
        }));
        restartTasks();
    }

    void restartTasks() {
        tasks.forEach(Scheduler.Task::cancel);
        tasks.clear();
        long regrow = 20L * module.config().regrowCheckSeconds;
        tasks.add(core.scheduler().repeat("shardblock-regrow", regrow, regrow, this::regrowDue));
        long audit = 20L * module.config().auditBatchSeconds;
        tasks.add(core.scheduler().repeat("shardblock-audit", audit, audit, this::flushAudit));
        long week = 20L * 60 * 60 * 24 * 7;
        tasks.add(core.scheduler().asyncRepeat(20L * 120, week, () -> store.purgeDays(today() - module.config().keepDays)
                .exceptionally(e -> {
                    core.plugin().getLogger().log(Level.WARNING, "Shard blocks: purging old daily rows failed", Database.cause(e));
                    return 0;
                })));
    }

    void stop() {
        tasks.forEach(Scheduler.Task::cancel);
        tasks.clear();
        flushAudit();
    }

    private void track(Placed p) {
        blocks.put(p.pos, p);
        byChunk.computeIfAbsent(p.pos.chunk() ^ p.pos.world.hashCode(), k -> new ArrayList<>()).add(p);
        if (p.readyAt > System.currentTimeMillis()) {
            due.add(new Due(p.readyAt, p));
        }
    }

    private void untrack(Placed p) {
        p.removed = true;
        blocks.remove(p.pos, p);
        List<Placed> list = byChunk.get(p.pos.chunk() ^ p.pos.world.hashCode());
        if (list != null) {
            list.remove(p);
        }
    }

    // ------------------------------------------------------------------ queries

    Optional<Placed> at(Block block) {
        return Optional.ofNullable(blocks.get(Pos.of(block)));
    }

    int countOn(UUID island) {
        int n = 0;
        for (Placed p : blocks.values()) {
            if (island.equals(p.island) && !p.removed) {
                n++;
            }
        }
        return n;
    }

    boolean loaded() {
        return loaded;
    }

    private Material active() {
        Material m = Material.matchMaterial(module.config().activeMaterial);
        return m == null || !m.isBlock() ? Material.INFESTED_STONE : m;
    }

    private Material depleted() {
        Material m = Material.matchMaterial(module.config().depletedMaterial);
        return m == null || !m.isBlock() ? Material.BEDROCK : m;
    }

    private ZoneId zone() {
        return ShardRules.zone(module.config().capZone);
    }

    int today() {
        return ShardRules.dayKey(Instant.now(), zone());
    }

    /** Sets the block to its look (active or depleted) when its chunk is loaded. */
    private void showState(Placed p) {
        World world = Bukkit.getWorld(p.pos.world);
        if (world == null || !world.isChunkLoaded(p.pos.x >> 4, p.pos.z >> 4)) {
            return;
        }
        Block b = world.getBlockAt(p.pos.x, p.pos.y, p.pos.z);
        Material want = p.ready(System.currentTimeMillis()) ? active() : depleted();
        if (b.getType() != want) {
            b.setType(want, false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        long key = (((long) event.getChunk().getX()) << 32 ^ (event.getChunk().getZ() & 0xffffffffL)) ^ event.getWorld().getName().hashCode();
        List<Placed> list = byChunk.get(key);
        if (list != null) {
            for (Placed p : new ArrayList<>(list)) {
                showState(p); // a crash between payout and block swap: the row decides the look
            }
        }
    }

    private void regrowDue() {
        long now = System.currentTimeMillis();
        int budget = module.config().regrowPerTick;
        while (budget-- > 0 && !due.isEmpty() && due.peek().at() <= now) {
            Due d = due.poll();
            Placed p = d.block();
            if (p.removed || p.readyAt != d.at()) {
                continue; // removed or broken again meanwhile (a newer entry exists)
            }
            showState(p);
            String sound = module.config().soundRegrow;
            if (sound != null && !sound.isBlank()) {
                World w = Bukkit.getWorld(p.pos.world);
                if (w != null) {
                    Location loc = new Location(w, p.pos.x + 0.5, p.pos.y + 0.5, p.pos.z + 0.5);
                    for (Player near : w.getNearbyPlayers(loc, 8)) {
                        core.sounds().play(near, sound);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ placing

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (!module.items().is(item)) {
            return;
        }
        Player player = event.getPlayer();
        Block block = event.getBlockPlaced();
        String refusal = placeProblem(player, block);
        if (refusal != null) {
            event.setCancelled(true);
            deny(player, refusal, Msg.number("max", module.config().maxPerIsland));
            return;
        }
        UUID island = module.islands().flatMap(i -> i.islandAt(block.getLocation())).map(IslandService.IslandInfo::id).orElseThrow();
        UUID profile = ProfileService.of(core.services()).profileKey(player);
        int resp = module.items().respawnLevel(item);
        int dbl = module.items().doubleLevel(item);
        Pos pos = Pos.of(block);
        Placed p = new Placed(pos, -1, island, profile, resp, dbl, 0, 0, 0);
        p.busy = true;
        track(p);
        ItemStack refund = player.getGameMode() == GameMode.CREATIVE ? null : item.asOne();
        UUID playerId = player.getUniqueId();
        String name = player.getName();
        Material active = active();
        // The spawner pattern (SECFIX1 F-01b): the hand loses the item after this event. One tick later the placement is
        // final: the player file is saved without the item, then the row is written. A crash loses the block at worst.
        core.scheduler().later("shardblock-place", 1L, () -> {
            Block b = block.getWorld().getBlockAt(pos.x(), pos.y(), pos.z());
            if (b.getType() != active || p.removed) {
                untrack(p);
                return; // cancelled after us: the item was never used
            }
            Player placer = Bukkit.getPlayer(playerId);
            long now = System.currentTimeMillis();
            PlayerSaves.commitAfterSave(placer, "placing a shard block",
                            () -> store.insert(pos.world(), pos.x(), pos.y(), pos.z(), island, profile, resp, dbl, now))
                    .whenComplete((id, error) -> core.scheduler().main("shardblock-placed", () -> {
                        p.busy = false;
                        Player online = Bukkit.getPlayer(playerId);
                        if (error != null && !(Database.cause(error) instanceof PlayerSaves.SaveFailed)) {
                            // Unclear database result: only the row decides (never refund a block that has a row).
                            store.at(pos.world(), pos.x(), pos.y(), pos.z()).whenComplete((row, e2) -> core.scheduler().main("shardblock-placed-check", () -> {
                                if (e2 == null && row.isPresent()) {
                                    p.id = row.get().blockId();
                                    audit("shard_block_place", Actor.player(playerId, name), playerId, name, p, Map.of("recovered", true));
                                } else {
                                    failedPlace(p, block, active, refund, profile, playerId, name, e2 == null ? error : e2);
                                }
                            }));
                            return;
                        }
                        if (error != null || id == null) {
                            failedPlace(p, block, active, refund, profile, playerId, name, error);
                            return;
                        }
                        p.id = id;
                        audit("shard_block_place", Actor.player(playerId, name), playerId, name, p, Map.of());
                        if (online != null) {
                            module.messages().send(online, "place.ok", Msg.number("amount", module.config().perBreak));
                            sound(online, module.config().soundBreak);
                        }
                    }));
        });
    }

    /** A placement that left no row: block removed, the item back (inventory, else /box). */
    private void failedPlace(Placed p, Block block, Material active, ItemStack refund, UUID profile, UUID playerId, String name, Throwable error) {
        Pos pos = p.pos;
        Player online = Bukkit.getPlayer(playerId);
        core.plugin().getLogger().log(Level.WARNING, "Shard blocks: placing at " + pos + " by " + name + " failed",
                error == null ? null : Database.cause(error));
        untrack(p);
        Block now = block.getWorld().getBlockAt(pos.x(), pos.y(), pos.z());
        if (now.getType() == active) {
            now.setType(Material.AIR, false);
        }
        if (refund != null) {
            if (online != null) {
                ItemBox.give(core.services(), online, BoxSource.SHARD_BLOCK, refund);
            } else {
                ItemBox.of(core.services()).ifPresent(box -> box.deliver(profile, List.of(refund), BoxSource.SHARD_BLOCK,
                        Actor.system("shardblock")));
            }
        }
        if (online != null) {
            module.messages().send(online, "place.failed");
        }
    }

    /** Why a shard block can't be placed here (message key), or null. */
    private String placeProblem(Player player, Block block) {
        if (!module.config().enabled || !loaded) {
            return "place.disabled";
        }
        if (!player.hasPermission(ShardBlockModule.PERM_USE)) {
            return "place.no-perm";
        }
        if (module.config().worlds.stream().noneMatch(w -> w.equalsIgnoreCase(block.getWorld().getName()))) {
            return "place.world";
        }
        Optional<IslandService> islands = module.islands();
        Optional<IslandService.IslandInfo> info = islands.flatMap(i -> i.islandAt(block.getLocation()));
        if (info.isEmpty()) {
            return "place.world";
        }
        if (!islands.get().hasIslandPermission(info.get().id(), player.getUniqueId(), IslandService.PERM_SHARD_BLOCKS)) {
            return "place.no-perm";
        }
        if (ProfileService.of(core.services()).isSwitching(player.getUniqueId())) {
            return "place.busy";
        }
        if (countOn(info.get().id()) >= module.config().maxPerIsland) {
            return "place.limit";
        }
        if (blocks.containsKey(Pos.of(block))) {
            return "place.busy";
        }
        return null;
    }

    // ------------------------------------------------------------------ breaking (the only shard payout)

    @EventHandler(priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent event) {
        Placed p = blocks.get(Pos.of(event.getBlock()));
        if (p == null) {
            return;
        }
        event.setCancelled(true); // H3: vanilla breaking never happens on a shard block
        event.setDropItems(false);
        event.setExpToDrop(0);
        collect(event.getPlayer(), p);
    }

    /**
     * Pays one break (ECON-SHARDS step 10). Main thread start; the shard, the block state and the daily counter commit in
     * one database transaction ({@link ShardBlockStore#collectGuard}); the block is swapped on the main thread afterwards.
     */
    void collect(Player player, Placed p) {
        long now = System.currentTimeMillis();
        if (!module.config().enabled || p.busy || p.id < 0) {
            return;
        }
        if (!player.hasPermission(ShardBlockModule.PERM_USE) || module.islands().isEmpty()
                || !module.islands().get().hasIslandPermission(p.island, player.getUniqueId(), IslandService.PERM_SHARD_BLOCKS)) {
            bar(player, "break.no-perm");
            return;
        }
        ProfileService profiles = ProfileService.of(core.services());
        if (profiles.isSwitching(player.getUniqueId())) {
            return;
        }
        if (!p.ready(now)) {
            bar(player, "break.regrowing", Msg.p("time", Format.duration(Duration.ofMillis(p.readyAt - now))));
            return;
        }
        Long last = lastBreak.get(player.getUniqueId());
        if (last != null && now - last < module.config().breakGapMs) {
            bar(player, "break.slow");
            return;
        }
        lastBreak.put(player.getUniqueId(), now);
        Optional<EconomyService> economy = core.services().optional(EconomyService.class);
        if (economy.isEmpty()) {
            bar(player, "break.off");
            return;
        }
        UUID profile = profiles.profileKey(player);
        int day = today();
        int cap = module.config().dailyCap;
        long used = usedToday(profile, day);
        long remaining = ShardRules.capRemaining(used, cap);
        if (remaining <= 0) {
            bar(player, "break.cap", Msg.number("cap", cap));
            return;
        }
        ShardRules rules = module.rules();
        int amount = (int) Math.min(remaining, ShardRules.rollAmount(module.config().perBreak, rules.doublePercent(p.doubleLevel),
                ThreadLocalRandom.current()));
        long readyAt = now + rules.respawnSeconds(p.respawnLevel) * 1000L;
        long breaks = p.breaks;
        long id = p.id;
        UUID playerId = player.getUniqueId();
        String name = player.getName();
        p.busy = true;
        Transaction tx = Transaction.deposit(AccountRef.player(profile), Currency.SHARDS, amount, TxReason.SHARD_BLOCK)
                .idempotencyKey("shard_block:" + id + ":" + breaks)
                .actor(Actor.player(player))
                .reference("shard_block:" + id)
                .detail("target_name", name)
                .detail("block", id);
        economy.get().executeGuarded(tx, c -> ShardBlockStore.collectGuard(c, id, breaks, now, readyAt, amount, profile, day, cap))
                .whenComplete((result, error) -> core.scheduler().main("shardblock-collect", () -> {
                    p.busy = false;
                    Player online = Bukkit.getPlayer(playerId);
                    if (error == null && result != null && result.ok()) {
                        p.breaks = breaks + 1;
                        p.readyAt = readyAt;
                        p.shardsPaid += amount;
                        long[] d = daily.computeIfAbsent(profile, k -> new long[]{day, 0});
                        if (d[0] != day) {
                            d[0] = day;
                            d[1] = 0;
                        }
                        d[1] += amount;
                        due.add(new Due(readyAt, p));
                        showState(p);
                        batch(profile, name, amount);
                        if (online != null) {
                            bar(online, "break.bar", Msg.number("amount", amount), Msg.p("s", amount == 1 ? "" : "s"),
                                    Msg.p("time", Format.duration(Duration.ofMillis(readyAt - now))));
                            sound(online, module.config().soundBreak);
                            World w = Bukkit.getWorld(p.pos.world);
                            if (w != null) {
                                w.spawnParticle(Particle.HAPPY_VILLAGER, p.pos.x + 0.5, p.pos.y + 0.8, p.pos.z + 0.5, 8, 0.3, 0.3, 0.3, 0);
                            }
                        }
                        return;
                    }
                    if (error != null) {
                        core.plugin().getLogger().log(Level.WARNING, "Shard blocks: payout of block " + id + " failed", Database.cause(error));
                    }
                    // DUPLICATE: the block changed or the daily cap was reached: re-read and tell the real reason.
                    explainRefusal(online, p, profile, day, cap);
                }));
    }

    private void explainRefusal(Player online, Placed p, UUID profile, int day, int cap) {
        store.dailyUsed(profile, day).thenCombine(store.get(p.id), (used, row) -> {
            core.scheduler().main("shardblock-refused", () -> {
                daily.put(profile, new long[]{day, used});
                row.ifPresent(r -> {
                    p.breaks = r.breaks();
                    p.readyAt = ShardBlockStore.READY.equals(r.state()) ? 0 : r.readyAt();
                    p.respawnLevel = r.respawnLevel();
                    p.doubleLevel = r.doubleLevel();
                    if (p.readyAt > System.currentTimeMillis()) {
                        due.add(new Due(p.readyAt, p));
                    }
                    showState(p);
                });
                if (online == null) {
                    return;
                }
                if (cap > 0 && used >= cap) {
                    bar(online, "break.cap", Msg.number("cap", cap));
                } else if (row.isPresent() && !p.ready(System.currentTimeMillis())) {
                    bar(online, "break.regrowing", Msg.p("time", Format.duration(Duration.ofMillis(p.readyAt - System.currentTimeMillis()))));
                } else {
                    bar(online, "break.failed");
                }
            });
            return null;
        }).exceptionally(e -> {
            core.plugin().getLogger().log(Level.WARNING, "Shard blocks: re-reading block " + p.id + " failed", Database.cause(e));
            return null;
        });
    }

    /** Shards this profile got today (cache; loaded from the database in the background when missing). */
    private long usedToday(UUID profile, int day) {
        long[] d = daily.get(profile);
        if (d != null && d[0] == day) {
            return d[1];
        }
        daily.put(profile, new long[]{day, 0});
        store.dailyUsed(profile, day).thenAccept(n -> core.scheduler().main("shardblock-daily", () -> {
            long[] now = daily.get(profile);
            if (now != null && now[0] == day) {
                now[1] = Math.max(now[1], n);
            }
        }));
        return 0; // the payout transaction checks the real number
    }

    // ------------------------------------------------------------------ left and right clicks

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(BlockDamageEvent event) {
        Placed p = blocks.get(Pos.of(event.getBlock()));
        long now = System.currentTimeMillis();
        if (p != null && !p.ready(now)) {
            bar(event.getPlayer(), "break.regrowing", Msg.p("time", Format.duration(Duration.ofMillis(p.readyAt - now))));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Placed p = blocks.get(Pos.of(block));
        if (p == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            if (!p.ready(now)) {
                bar(event.getPlayer(), "break.regrowing", Msg.p("time", Format.duration(Duration.ofMillis(p.readyAt - now))));
            }
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        event.setCancelled(true); // never places against it, never opens anything vanilla
        Player player = event.getPlayer();
        if (!player.hasPermission(ShardBlockModule.PERM_USE) || module.islands().isEmpty()
                || !module.islands().get().hasIslandPermission(p.island, player.getUniqueId(), IslandService.PERM_SHARD_BLOCKS)) {
            module.messages().send(player, "menu.no-perm");
            return;
        }
        if (p.id < 0) {
            return; // still being placed
        }
        core.menus().open(player, new ShardBlockMenu(module, p));
    }

    // ------------------------------------------------------------------ upgrades and pick-up (from the menu)

    /** Buys the next level of {@code kind} for this block: the price and the level in one transaction (H6). */
    void upgrade(Player player, Placed p, ShardRules.Kind kind) {
        if (p.busy || p.removed || p.id < 0) {
            module.messages().send(player, "upgrade.busy");
            return;
        }
        int from = kind == ShardRules.Kind.RESPAWN ? p.respawnLevel : p.doubleLevel;
        ShardRules.Cost cost = module.rules().cost(kind, from + 1);
        if (cost == null) {
            module.messages().send(player, "upgrade.maxed");
            return;
        }
        Optional<EconomyService> economy = core.services().optional(EconomyService.class);
        if (economy.isEmpty() || ProfileService.of(core.services()).isSwitching(player.getUniqueId())) {
            module.messages().send(player, "upgrade.busy");
            return;
        }
        UUID profile = ProfileService.of(core.services()).profileKey(player);
        UUID playerId = player.getUniqueId();
        String name = player.getName();
        long id = p.id;
        long now = System.currentTimeMillis();
        p.busy = true;
        Transaction tx = Transaction.withdraw(AccountRef.player(profile), cost.currency(), cost.amount(), TxReason.SHARD_BLOCK_UPGRADE)
                .idempotencyKey("sbu:" + id + ":" + kind.key() + ":" + (from + 1))
                .actor(Actor.player(player))
                .reference("shard_block:" + id)
                .detail("target_name", name)
                .detail("kind", kind.key())
                .detail("level", from + 1);
        economy.get().executeGuarded(tx, c -> ShardBlockStore.upgradeGuard(c, id, kind, from, now))
                .whenComplete((result, error) -> core.scheduler().main("shardblock-upgrade", () -> {
                    p.busy = false;
                    Player online = Bukkit.getPlayer(playerId);
                    if (error == null && result != null && result.ok()) {
                        if (kind == ShardRules.Kind.RESPAWN) {
                            p.respawnLevel = from + 1;
                        } else {
                            p.doubleLevel = from + 1;
                        }
                        audit("shard_block_upgrade", Actor.player(playerId, name), playerId, name, p,
                                Map.of("kind", kind.key(), "level", from + 1, "currency", cost.currency().key(), "price", cost.amount()));
                        if (online != null) {
                            module.messages().send(online, "upgrade.ok", Msg.c("upgrade", module.messages().component(online, "menu." + kind.key() + ".name")),
                                    Msg.number("level", from + 1), Msg.p("price", module.price(cost)));
                            sound(online, module.config().soundUpgrade);
                            refreshMenu(online);
                        }
                        return;
                    }
                    if (online == null) {
                        return;
                    }
                    TxResult.Status status = result == null ? TxResult.Status.ERROR : result.status();
                    if (status == TxResult.Status.INSUFFICIENT_FUNDS) {
                        long have = core.services().optional(EconomyService.class)
                                .map(e -> e.cached(playerId, cost.currency()).orElse(0)).orElse(0L);
                        module.messages().send(online, "upgrade.poor", Msg.p("price", module.price(cost)),
                                Msg.p("have", module.price(new ShardRules.Cost(cost.currency(), have))));
                    } else if (status == TxResult.Status.DUPLICATE) {
                        module.messages().send(online, "upgrade.changed");
                        store.get(id).thenAccept(row -> core.scheduler().main("shardblock-reread", () -> row.ifPresent(r -> {
                            p.respawnLevel = r.respawnLevel();
                            p.doubleLevel = r.doubleLevel();
                            refreshMenu(online);
                        })));
                    } else {
                        module.messages().send(online, "upgrade.failed");
                    }
                    sound(online, "error");
                }));
    }

    /** Picks the block up (H7): the row is archived first (exactly once), then the item with its levels is given. */
    void pickUp(Player player, Placed p) {
        if (p.busy || p.removed || p.id < 0) {
            module.messages().send(player, "upgrade.busy");
            return;
        }
        UUID profile = ProfileService.of(core.services()).profileKey(player);
        boolean placer = profile.equals(p.placedBy);
        boolean manager = module.islands().map(i -> i.hasIslandPermission(p.island, player.getUniqueId(), IslandService.PERM_SETTINGS)).orElse(false);
        if (!placer && !manager) {
            module.messages().send(player, "pickup.not-yours");
            return;
        }
        if (ItemBox.refuses(core.services(), player)) {
            return; // /box at its hard cap: told by the box
        }
        p.busy = true;
        UUID playerId = player.getUniqueId();
        String name = player.getName();
        store.archive(p.id, "pickup", playerId.toString(), System.currentTimeMillis()).whenComplete((row, error) ->
                core.scheduler().main("shardblock-pickup", () -> {
                    p.busy = false;
                    Player online = Bukkit.getPlayer(playerId);
                    if (error != null || row == null || row.isEmpty()) {
                        if (error != null) {
                            core.plugin().getLogger().log(Level.WARNING, "Shard blocks: pick-up of block " + p.id + " failed", Database.cause(error));
                        }
                        if (online != null) {
                            module.messages().send(online, "upgrade.changed");
                        }
                        return;
                    }
                    ShardBlockStore.Row r = row.get();
                    untrack(p);
                    setAir(p);
                    ItemStack item = module.items().create(1, "pickup", r.respawnLevel(), r.doubleLevel());
                    boolean boxed;
                    if (online != null && ProfileService.of(core.services()).profileKey(online).equals(profile)) {
                        boxed = !ItemBox.give(core.services(), online, BoxSource.SHARD_BLOCK, item).isEmpty();
                        PlayerSaves.save(online, "a shard block pick-up");
                    } else {
                        boxed = true;
                        ItemBox.of(core.services()).ifPresent(b -> b.deliver(profile, List.of(item), BoxSource.SHARD_BLOCK, Actor.player(playerId, name)));
                    }
                    audit("shard_block_pickup", Actor.player(playerId, name), playerId, name, p, Map.of("box", boxed));
                    if (online != null) {
                        module.messages().send(online, boxed ? "pickup.box" : "pickup.ok");
                        if (core.menus().openMenu(online) instanceof ShardBlockMenu) {
                            online.closeInventory();
                        }
                    }
                }));
    }

    // ------------------------------------------------------------------ staff

    /** Staff removal: archived (reason staff), block to air, the item with its levels to the placer's /box. */
    void staffRemove(Player staff, Placed p) {
        if (p.busy || p.id < 0) {
            module.messages().send(staff, "upgrade.busy");
            return;
        }
        p.busy = true;
        UUID staffId = staff.getUniqueId();
        String staffName = staff.getName();
        store.archive(p.id, "staff", staffId.toString(), System.currentTimeMillis()).whenComplete((row, error) ->
                core.scheduler().main("shardblock-remove", () -> {
                    p.busy = false;
                    Player online = Bukkit.getPlayer(staffId);
                    if (error != null || row == null || row.isEmpty()) {
                        if (online != null) {
                            module.messages().send(online, "upgrade.changed");
                        }
                        return;
                    }
                    ShardBlockStore.Row r = row.get();
                    untrack(p);
                    setAir(p);
                    ItemStack item = module.items().create(1, "staff_remove", r.respawnLevel(), r.doubleLevel());
                    ItemBox.of(core.services()).ifPresent(b -> b.deliver(r.placedBy(), List.of(item), BoxSource.SHARD_BLOCK,
                            Actor.player(staffId, staffName)));
                    audit("shard_block_remove", Actor.player(staffId, staffName), r.placedBy(), null, p, Map.of());
                    if (online != null) {
                        module.messages().send(online, "admin.removed", Msg.number("id", r.blockId()));
                    }
                }));
    }

    private void setAir(Placed p) {
        World w = Bukkit.getWorld(p.pos.world);
        if (w != null && w.isChunkLoaded(p.pos.x >> 4, p.pos.z >> 4)) {
            w.getBlockAt(p.pos.x, p.pos.y, p.pos.z).setType(Material.AIR, false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onIslandRemoved(IslandRemovedEvent event) {
        UUID island = event.islandId();
        List<Placed> gone = new ArrayList<>();
        for (Placed p : blocks.values()) {
            if (island.equals(p.island)) {
                gone.add(p);
            }
        }
        gone.forEach(p -> {
            untrack(p);
            setAir(p);
        });
        store.archiveIsland(island, "island_removed", event.reason(), System.currentTimeMillis()).whenComplete((rows, error) -> {
            if (error != null) {
                core.plugin().getLogger().log(Level.WARNING, "Shard blocks: archiving the blocks of removed island " + island + " failed",
                        Database.cause(error));
                return;
            }
            if (!rows.isEmpty()) {
                Map<String, Object> details = new LinkedHashMap<>();
                details.put("island", island.toString());
                details.put("blocks", rows.size());
                details.put("reason", event.reason());
                core.audit().log(AuditCategory.ISLAND, "shard_block_archive", Actor.system("shardblock"), island.toString(), null,
                        (long) rows.size(), null, details);
            }
        });
    }

    // ------------------------------------------------------------------ protection (H3: nothing but a player break changes it)

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(b -> blocks.containsKey(Pos.of(b)));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(b -> blocks.containsKey(Pos.of(b)));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block b : event.getBlocks()) {
            if (blocks.containsKey(Pos.of(b)) || blocks.containsKey(Pos.of(b.getRelative(event.getDirection())))) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block b : event.getBlocks()) {
            if (blocks.containsKey(Pos.of(b)) || blocks.containsKey(Pos.of(b.getRelative(event.getDirection())))) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (blocks.containsKey(Pos.of(event.getBlock()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFade(BlockFadeEvent event) {
        if (blocks.containsKey(Pos.of(event.getBlock()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChange(EntityChangeBlockEvent event) {
        if (blocks.containsKey(Pos.of(event.getBlock()))) {
            event.setCancelled(true); // endermen, withers, falling blocks, silverfish
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        if (blocks.containsKey(Pos.of(event.getToBlock()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (blocks.containsKey(Pos.of(event.getBlock())) || blocks.containsKey(Pos.of(event.getBlockClicked()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (blocks.containsKey(Pos.of(event.getBlockClicked()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDispense(org.bukkit.event.block.BlockDispenseEvent event) {
        if (module.items().is(event.getItem())) {
            event.setCancelled(true); // a dispenser can't place a shard block (no row, no owner)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        lastBreak.remove(id);
        lastMessage.remove(id);
    }

    // ------------------------------------------------------------------ audit, messages

    private void batch(UUID profile, String name, int amount) {
        long[] b = auditBatch.computeIfAbsent(profile, k -> new long[2]);
        b[0]++;
        b[1] += amount;
        auditNames.put(profile, name);
    }

    /** One audit row per profile with the breaks and shards since the last flush (not one per break). */
    void flushAudit() {
        if (auditBatch.isEmpty()) {
            return;
        }
        auditBatch.forEach((profile, b) -> {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("profile", profile.toString());
            details.put("breaks", b[0]);
            details.put("shards", b[1]);
            core.audit().logPlayer(AuditCategory.ISLAND, "shard_block_break", Actor.system("shardblock"),
                    ProfileService.of(core.services()).ownerOf(profile), auditNames.get(profile), b[1], null, details);
        });
        auditBatch.clear();
        auditNames.clear();
    }

    void audit(String action, Actor actor, UUID target, String targetName, Placed p, Map<String, Object> extra) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("block", p.id);
        details.put("island", String.valueOf(p.island));
        details.put("placed_by", String.valueOf(p.placedBy));
        details.put("respawn_level", p.respawnLevel);
        details.put("double_level", p.doubleLevel);
        details.put("pos", p.pos.world + " " + p.pos.x + " " + p.pos.y + " " + p.pos.z);
        details.putAll(extra);
        World w = Bukkit.getWorld(p.pos.world);
        core.audit().logPlayer(AuditCategory.ISLAND, action, actor, target, targetName, null,
                w == null ? null : new Location(w, p.pos.x, p.pos.y, p.pos.z), details);
    }

    private void bar(Player player, String key, TagResolver... tags) {
        long now = System.currentTimeMillis();
        Long last = lastMessage.get(player.getUniqueId());
        if (!key.equals("break.bar") && last != null && now - last < module.config().messageGapMs) {
            return;
        }
        lastMessage.put(player.getUniqueId(), now);
        module.messages().actionBar(player, key, tags);
    }

    private void deny(Player player, String key, TagResolver... tags) {
        module.messages().send(player, key, tags);
        sound(player, "error");
    }

    void sound(Player player, String key) {
        if (key != null && !key.isBlank()) {
            core.sounds().play(player, key);
        }
    }

    private void refreshMenu(Player player) {
        if (core.menus().openMenu(player) instanceof ShardBlockMenu menu) {
            menu.refresh();
        }
    }

    /** Blocks the staff member looks at (null = none). */
    Placed looked(Player player) {
        Block b = player.getTargetBlockExact(module.config().lookRange);
        return b == null ? null : blocks.get(Pos.of(b));
    }

    ShardBlockStore store() {
        return store;
    }
}
