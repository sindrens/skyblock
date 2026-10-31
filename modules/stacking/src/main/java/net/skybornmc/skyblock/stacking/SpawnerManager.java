package net.skybornmc.skyblock.stacking;

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.island.IslandService;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.PriorityQueue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Stacked spawner blocks (owner S20–S28). The database row is the truth for each block (see V001__spawners.sql): every
 * change is written first (conditional on the old amount) and only then are items handed out, taken back or the block
 * changed. While a change is in flight the spawner is busy and refuses other changes, so nothing can be duplicated.
 *
 * <p>Spawning: vanilla spawning is switched off for these blocks; a priority queue holds the next spawn tick of every
 * spawner, so a tick only touches the spawners that are due (no scans). A due spawner runs when a player is within
 * {@code spawners.activation-range}, or when the island has fuel and no member is online; it then adds
 * {@code mobs-per-spawner x stack x rate mastery} mobs into stacks next to it (light, space and blocks ignored).</p>
 */
final class SpawnerManager implements Listener {

    /** Vanilla delay large enough that vanilla never spawns (spawner spawns are also cancelled as a backup). */
    private static final int VANILLA_OFF = 1_000_000_000;

    record Pos(String world, int x, int y, int z) {
        static Pos of(Block block) {
            return new Pos(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
        }

        Location center(World w) {
            return new Location(w, x + 0.5, y + 0.5, z + 0.5);
        }
    }

    /** One stacked spawner in memory (mirrors its database row). */
    static final class Spawner {
        final Pos pos;
        final String type;
        final UUID island;
        final UUID placedBy;
        final long placedAt;
        int amount;
        boolean busy;
        boolean configured;
        boolean removed;
        long nextTick;
        double carry;

        Spawner(Pos pos, String type, int amount, UUID island, UUID placedBy, long placedAt) {
            this.pos = pos;
            this.type = type;
            this.amount = amount;
            this.island = island;
            this.placedBy = placedBy;
            this.placedAt = placedAt;
        }
    }

    private record Due(long tick, Spawner spawner) {
    }

    private final StackingModule module;
    private final SpawnerStore store = new SpawnerStore();
    private final Map<Pos, Spawner> spawners = new HashMap<>();
    private final PriorityQueue<Due> queue = new PriorityQueue<>((a, b) -> Long.compare(a.tick(), b.tick()));

    SpawnerManager(StackingModule module) {
        this.module = module;
    }

    // ================================================================== loading and lookups

    /** Startup only: reads every spawner row (blocking is allowed while the module enables). */
    void load() {
        List<SpawnerStore.Row> rows = module.core().database().blocking(store::loadAll);
        long now = Bukkit.getCurrentTick();
        for (SpawnerStore.Row row : rows) {
            Spawner sp = new Spawner(new Pos(row.world(), row.x(), row.y(), row.z()), SpawnerTiers.normalize(row.type()), row.amount(),
                    row.island(), row.placedBy(), row.placedAt());
            spawners.put(sp.pos, sp);
            schedule(sp, now + ThreadLocalRandom.current().nextInt(20, 200)); // spread the first spawns
        }
        module.core().plugin().getLogger().info("Stacking: " + rows.size() + " stacked spawner block(s) loaded.");
    }

    Optional<Spawner> at(Block block) {
        return Optional.ofNullable(spawners.get(Pos.of(block)));
    }

    /** R3-30: spawners stacked on blocks that belong to {@code island} (in memory; one pass over the loaded spawners). */
    long onIsland(UUID island) {
        long sum = 0;
        if (island == null) {
            return 0;
        }
        for (Spawner sp : spawners.values()) {
            if (!sp.removed && island.equals(sp.island)) {
                sum += sp.amount;
            }
        }
        return sum;
    }

    int count(Block block) {
        Spawner sp = spawners.get(Pos.of(block));
        return sp == null ? 0 : sp.amount;
    }

    int size() {
        return spawners.size();
    }

    private void schedule(Spawner sp, long tick) {
        sp.nextTick = tick;
        queue.add(new Due(tick, sp));
    }

    private int interval() {
        StackingConfig c = module.config();
        return StackMath.intervalTicks(c.spawnerMinIntervalSeconds, c.spawnerMaxIntervalSeconds, ThreadLocalRandom.current().nextDouble());
    }

    /** The most spawners this block may hold (owner S20 + Spawner Stack Limit mastery hook). */
    int limit(Spawner sp) {
        OptionalInt mastery = sp.island == null ? OptionalInt.empty()
                : module.islands().map(s -> s.spawnerStackLimit(sp.island)).orElse(OptionalInt.empty());
        return StackMath.stackLimit(mastery, module.config().spawnerStackStart, module.config().spawnerStackMax);
    }

    double rateMultiplier(Spawner sp) {
        return sp.island == null ? 1.0 : module.islands().map(s -> s.spawnerRateMultiplier(sp.island)).orElse(1.0);
    }

    /** Mobs per minute at the average interval (menu). */
    double mobsPerMinute(Spawner sp) {
        StackingConfig c = module.config();
        double avgSeconds = (c.spawnerMinIntervalSeconds + c.spawnerMaxIntervalSeconds) / 2.0;
        return module.config().mobsPerSpawner * sp.amount * rateMultiplier(sp) * 60.0 / Math.max(1, avgSeconds);
    }

    // ================================================================== ticking

    /** Every tick: handles the spawners that are due (at most {@code spawners.due-per-tick}). */
    void tick() {
        if (queue.isEmpty()) {
            return;
        }
        long now = Bukkit.getCurrentTick();
        if (queue.peek().tick() > now) {
            return;
        }
        long start = module.perf().start();
        try {
            int budget = module.config().spawnerDuePerTick;
            while (budget-- > 0 && !queue.isEmpty() && queue.peek().tick() <= now) {
                Due due = queue.poll();
                Spawner sp = due.spawner();
                if (sp.removed || due.tick() != sp.nextTick) {
                    continue; // stale entry (rescheduled or removed)
                }
                run(sp, now);
            }
        } finally {
            module.perf().end(start);
        }
    }

    private void run(Spawner sp, long now) {
        World world = Bukkit.getWorld(sp.pos.world());
        if (world == null || !world.isChunkLoaded(sp.pos.x() >> 4, sp.pos.z() >> 4)) {
            schedule(sp, now + interval());
            return;
        }
        Block block = world.getBlockAt(sp.pos.x(), sp.pos.y(), sp.pos.z());
        if (block.getType() != Material.SPAWNER) {
            if (sp.busy) {
                schedule(sp, now + interval()); // a change (or the first save) is still running: decide after it
                return;
            }
            orphan(sp, "block_gone");
            return;
        }
        if (!sp.configured) {
            configure(block, sp);
        }
        schedule(sp, now + interval());
        EntityType type = module.entityType(sp.type);
        if (sp.busy || type == null || !active(sp, world)) {
            return;
        }
        StackMath.Output out = StackMath.spawnerOutput(module.config().mobsPerSpawner, sp.amount, rateMultiplier(sp), sp.carry);
        sp.carry = out.carry();
        if (out.mobs() > 0) {
            module.mobs().addSpawned(block.getLocation(), type, out.mobs());
        }
    }

    /** Owner S24: a player within the activation range, or island fuel while no member is online. */
    private boolean active(Spawner sp, World world) {
        Location center = sp.pos.center(world);
        if (!world.getNearbyPlayers(center, module.config().activationRange, p -> p.getGameMode() != GameMode.SPECTATOR).isEmpty()) {
            return true;
        }
        if (sp.island == null) {
            return false;
        }
        Optional<IslandService> islands = module.islands();
        if (islands.isEmpty() || !islands.get().hasIslandFuel(sp.island)) {
            return false;
        }
        for (UUID member : islands.get().members(sp.island).keySet()) {
            if (Bukkit.getPlayer(member) != null) {
                return false;
            }
        }
        return true;
    }

    /** Shows the mob in the cage and switches vanilla spawning off for this block. */
    private void configure(Block block, Spawner sp) {
        if (!(block.getState() instanceof CreatureSpawner state)) {
            return;
        }
        EntityType type = module.entityType(sp.type);
        if (type != null) {
            state.setSpawnedType(type);
        }
        state.setRequiredPlayerRange(module.config().activationRange);
        state.setMaxSpawnDelay(VANILLA_OFF);
        state.setMinSpawnDelay(VANILLA_OFF);
        state.setDelay(VANILLA_OFF);
        state.update(true, false);
        sp.configured = true;
    }

    /**
     * SECFIX1 F-07 / R3-13: the block is gone (crash before the chunk was saved, island reset, world edit). The row is
     * frozen, not deleted: it moves to {@code stacking_spawners_frozen} in one transaction, never spawns and can't be
     * taken (no dupe), and staff can give the spawners back from that table and the {@code spawner_frozen} audit row.
     */
    private void orphan(Spawner sp, String reason) {
        sp.removed = true;
        spawners.remove(sp.pos, sp);
        Pos p = sp.pos;
        long now = System.currentTimeMillis();
        then(module.core().database().transaction(c -> store.freeze(c, p.world(), p.x(), p.y(), p.z(), reason, now)), frozen -> {
            if (frozen.isEmpty()) {
                return; // no row any more (a take emptied it meanwhile): nothing to keep
            }
            SpawnerStore.Frozen f = frozen.get();
            module.core().plugin().getLogger().warning("Stacking: the block of a stacked spawner is gone (" + reason + "); "
                    + f.row().amount() + " x " + f.row().type() + " at " + p + " were frozen for staff (stacking_spawners_frozen id "
                    + f.id() + "). Nothing was deleted.");
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("reason", reason);
            details.put("frozen_id", f.id());
            details.put("placed_by", String.valueOf(f.row().placedBy()));
            audit("spawner_frozen", Actor.SYSTEM, sp, f.row().amount(), 0, null, details);
        }, error -> module.core().plugin().getLogger().log(Level.SEVERE, "Stacking: could not freeze the spawner row at " + p
                + "; it stays in stacking_spawners and is checked again after the next restart", error));
    }

    // ================================================================== R3-13: island removal and staff restore

    /** R3-13a: an island removal clears its spawners only for a disband or a reset on purpose (staff delete keeps them). */
    static boolean clearsSpawners(String reason) {
        return net.skybornmc.skyblock.common.island.IslandRemovedEvent.DISBAND.equals(reason)
                || net.skybornmc.skyblock.common.island.IslandRemovedEvent.RESET.equals(reason);
    }

    /**
     * R3-13a: the island was disbanded or reset on purpose. Its spawners are cleared, not frozen for staff: they leave
     * memory at once (no spawn tick can freeze them any more) and one transaction deletes the rows and marks the
     * island's waiting frozen rows {@code cleared}. Audited as {@code spawners_cleared}.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onIslandRemoved(net.skybornmc.skyblock.common.island.IslandRemovedEvent event) {
        if (!clearsSpawners(event.reason()) || event.islandId() == null) {
            return;
        }
        UUID island = event.islandId();
        int inMemory = 0;
        for (java.util.Iterator<Spawner> it = spawners.values().iterator(); it.hasNext(); ) {
            Spawner sp = it.next();
            if (island.equals(sp.island)) {
                sp.removed = true; // a running change fails its conditional update (row gone) and hands nothing out
                it.remove();
                inMemory++;
            }
        }
        int dropped = inMemory;
        then(module.core().database().transaction(c -> store.clearIsland(c, island)), cleared -> {
            if (cleared.rows().isEmpty() && cleared.frozenRows() == 0) {
                return;
            }
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("reason", event.reason());
            details.put("blocks", cleared.rows().size());
            details.put("frozen_cleared", cleared.frozenRows());
            details.put("in_memory", dropped);
            Map<String, Integer> types = new LinkedHashMap<>();
            cleared.rows().forEach(r -> types.merge(r.type(), r.amount(), Integer::sum));
            details.put("types", types);
            module.core().audit().log(AuditCategory.ISLAND, "spawners_cleared", event.actor(), island.toString(), null,
                    (long) cleared.spawners(), null, details);
            module.core().plugin().getLogger().info("Stacking: island " + island + " was " + event.reason() + ": cleared "
                    + cleared.spawners() + " spawner(s) in " + cleared.rows().size() + " block(s) and " + cleared.frozenRows()
                    + " frozen row(s) (R3-13a).");
        }, error -> module.core().plugin().getLogger().log(Level.SEVERE, "Stacking: could not clear the spawners of removed island "
                + island + "; their rows stay and freeze when checked", error));
    }

    /** R3-13b: the frozen spawners a player placed (staff view). Async, completes on any thread. */
    CompletableFuture<List<SpawnerStore.Frozen>> frozenPlacedBy(UUID player, int limit) {
        return module.core().database().async(c -> store.frozenPlacedBy(c, player, limit));
    }

    /**
     * R3-13b staff restore: marks the frozen row restored exactly once (conditional), then sends the spawners to the
     * placer's /box (their active profile; offline-safe, never lost: the box spools). Audited as
     * {@code spawner_restore}. {@code done} runs on the main thread with true when this click restored it.
     */
    void restore(Player staff, SpawnerStore.Frozen frozen, Consumer<Boolean> done) {
        Optional<net.skybornmc.skyblock.common.box.ItemBox> box = net.skybornmc.skyblock.common.box.ItemBox.of(module.core().services());
        if (box.isEmpty()) {
            deny(staff, "frozen.no-box");
            done.accept(false);
            return;
        }
        if (frozen.row().placedBy() == null) {
            deny(staff, "frozen.no-owner");
            done.accept(false);
            return;
        }
        String type = SpawnerTiers.normalize(frozen.row().type());
        if (module.spawnerItems().create(type, 1, "check").isEmpty()) {
            deny(staff, "frozen.unknown-type", mob(type));
            done.accept(false);
            return;
        }
        UUID staffId = staff.getUniqueId();
        String staffName = staff.getName();
        then(module.core().database().transaction(c -> store.restore(c, frozen.id())), restored -> {
            Player online = Bukkit.getPlayer(staffId);
            if (restored.isEmpty()) {
                if (online != null) {
                    deny(online, "frozen.already");
                }
                done.accept(false);
                return;
            }
            SpawnerStore.Row r = restored.get().row();
            List<ItemStack> stacks = new java.util.ArrayList<>();
            for (int left = r.amount(); left > 0; left -= 64) {
                module.spawnerItems().create(type, Math.min(64, left), "restore:" + staffName).ifPresent(stacks::add);
            }
            UUID placer = r.placedBy();
            net.skybornmc.skyblock.common.profile.ProfileService.of(module.core().services()).profileKeyAsync(placer)
                    .thenAccept(profile -> box.get().deliver(profile, stacks, net.skybornmc.skyblock.common.box.BoxSource.SPAWNER_RESTORE,
                            Actor.player(staffId, staffName)));
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("frozen_id", frozen.id());
            details.put("type", type);
            details.put("placed_by", placer.toString());
            details.put("island", String.valueOf(r.island()));
            details.put("pos", r.world() + " " + r.x() + "," + r.y() + "," + r.z());
            module.core().audit().logPlayer(AuditCategory.STAFF, "spawner_restore", Actor.player(staffId, staffName), placer, null,
                    (long) r.amount(), null, details);
            if (online != null) {
                module.msg().send(online, "frozen.restored", Msg.p("amount", r.amount()), mob(type));
                module.core().sounds().play(online, "success");
            }
            Player owner = Bukkit.getPlayer(placer);
            if (owner != null && !owner.getUniqueId().equals(staffId)) {
                module.msg().send(owner, "frozen.returned", Msg.p("amount", r.amount()), mob(type));
            }
            done.accept(true);
        }, error -> {
            Player online = Bukkit.getPlayer(staffId);
            if (online != null) {
                deny(online, "spawner.error");
            }
            done.accept(false);
        });
    }

    // ================================================================== vanilla behaviour

    /** Only stacked spawners spawn in the stacking worlds; vanilla spawner spawns are cancelled. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVanillaSpawn(SpawnerSpawnEvent event) {
        if (event.getSpawner() != null && module.eligible(event.getLocation().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!spawners.isEmpty()) {
            event.blockList().removeIf(b -> b.getType() == Material.SPAWNER && spawners.containsKey(Pos.of(b)));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (!spawners.isEmpty()) {
            event.blockList().removeIf(b -> b.getType() == Material.SPAWNER && spawners.containsKey(Pos.of(b)));
        }
    }

    // ================================================================== placing

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        Optional<String> type = SpawnerItems.typeOf(item);
        if (type.isEmpty()) {
            return;
        }
        Player player = event.getPlayer();
        Block block = event.getBlockPlaced();
        if (!module.eligible(block.getWorld())) {
            event.setCancelled(true);
            deny(player, "spawner.only-islands");
            return;
        }
        if (!module.tiers().allowed(type.get()) || module.entityType(type.get()) == null) {
            event.setCancelled(true);
            deny(player, "spawner.disabled-type");
            return;
        }
        UUID island = null;
        Optional<IslandService> islands = module.islands();
        if (islands.isPresent()) {
            Optional<IslandService.IslandInfo> info = islands.get().islandAt(block.getLocation());
            if (info.isEmpty()) {
                event.setCancelled(true);
                deny(player, "spawner.only-islands");
                return;
            }
            island = info.get().id();
        }
        Pos pos = Pos.of(block);
        if (spawners.containsKey(pos)) {
            event.setCancelled(true);
            deny(player, "spawner.busy");
            return;
        }
        long now = System.currentTimeMillis();
        Spawner sp = new Spawner(pos, type.get(), 1, island, player.getUniqueId(), now);
        sp.busy = true;
        spawners.put(pos, sp);
        // Creative placing uses no item, so nothing is handed back if saving fails.
        ItemStack refund = player.getGameMode() == GameMode.CREATIVE ? null : item.asOne();
        UUID playerId = player.getUniqueId();
        String placerName = player.getName();
        SpawnerStore.Row row = new SpawnerStore.Row(pos.world(), pos.x(), pos.y(), pos.z(), sp.type, 1, island, playerId, now);
        // SECFIX1 F-01b: the server takes the item out of the hand AFTER this event. One tick later the placement is final:
        // the player file is saved without the item, and only then the row is written (a crash loses the spawner at
        // worst, never keeps the item next to a row). A placement cancelled by a later plugin leaves no block: no row.
        module.core().scheduler().later("stacking-spawner-place", 1L, () -> {
            Block b = block.getWorld().getBlockAt(pos.x(), pos.y(), pos.z());
            if (b.getType() != Material.SPAWNER || sp.removed) {
                sp.removed = true;
                spawners.remove(pos, sp);
                return; // cancelled after us: the item was never used
            }
            configure(b, sp);
            Player placer = Bukkit.getPlayer(playerId);
            insertPlaced(sp, block, row, now, refund, playerId, placerName, placer);
        });
    }

    /** Second half of a placement (next tick): save the player file, then write the row. */
    private void insertPlaced(Spawner sp, Block block, SpawnerStore.Row row, long now, ItemStack refund, UUID playerId, String name,
                              Player player) {
        then(net.skybornmc.skyblock.common.player.PlayerSaves.commitAfterSave(player, "placing a spawner",
                () -> module.core().database().async(c -> store.insert(c, row, now))), ok -> {
            sp.busy = false;
            if (Boolean.TRUE.equals(ok)) {
                schedule(sp, Bukkit.getCurrentTick() + interval());
                audit("spawner_place", Actor.player(playerId, name), sp, 0, 1, block.getLocation(), Map.of());
                Player online = Bukkit.getPlayer(playerId);
                if (online != null) {
                    module.msg().send(online, "spawner.placed", mob(sp.type), Msg.p("max", limit(sp)));
                }
            } else {
                failPlace(sp, block, refund, playerId);
            }
        }, error -> failPlace(sp, block, refund, playerId));
    }

    private void failPlace(Spawner sp, Block block, ItemStack refund, UUID playerId) {
        sp.removed = true;
        spawners.remove(sp.pos, sp);
        Block b = block.getWorld().getBlockAt(sp.pos.x(), sp.pos.y(), sp.pos.z());
        if (b.getType() == Material.SPAWNER) {
            b.setType(Material.AIR, false);
        }
        Player online = Bukkit.getPlayer(playerId);
        if (refund != null) {
            give(online, b.getLocation(), refund, 1);
        }
        if (online != null) {
            deny(online, "spawner.error");
        }
    }

    // ================================================================== breaking (owner S22)

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.SPAWNER) {
            return;
        }
        Spawner sp = spawners.get(Pos.of(block));
        if (sp == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        remove(player, sp, StackMath.breakAmount(sp.amount, player.isSneaking()), "break");
    }

    /**
     * Takes {@code amount} spawners out of the block into the player's inventory (the rest of what doesn't fit drops at
     * their feet); the block disappears at 0.
     */
    void remove(Player player, Spawner sp, int amount, String how) {
        if (sp.busy || sp.removed) {
            deny(player, "spawner.busy");
            return;
        }
        int take = Math.min(amount, sp.amount);
        if (take <= 0) {
            return;
        }
        if (net.skybornmc.skyblock.common.box.ItemBox.refuses(module.core().services(), player)) {
            return; // SECFIX1 F-09: the /box is at its hard cap (player told); the spawners stay in the block
        }
        int before = sp.amount;
        int after = before - take;
        sp.busy = true;
        UUID playerId = player.getUniqueId();
        String name = player.getName();
        Pos p = sp.pos;
        then(module.core().database().async(c -> store.change(c, p.world(), p.x(), p.y(), p.z(), sp.type, before, after, System.currentTimeMillis())),
                ok -> {
                    sp.busy = false;
                    if (!Boolean.TRUE.equals(ok)) {
                        Player online = Bukkit.getPlayer(playerId);
                        if (online != null) {
                            deny(online, "spawner.error");
                        }
                        return;
                    }
                    sp.amount = after;
                    World world = Bukkit.getWorld(p.world());
                    Location at = world == null ? null : p.center(world);
                    if (after == 0) {
                        sp.removed = true;
                        spawners.remove(p, sp);
                        if (world != null) {
                            Block b = world.getBlockAt(p.x(), p.y(), p.z());
                            if (b.getType() == Material.SPAWNER) {
                                b.setType(Material.AIR);
                            }
                        }
                    }
                    Player online = Bukkit.getPlayer(playerId);
                    giveSpawners(online, at, sp.type, take, "spawner:" + name);
                    audit("spawner_remove", Actor.player(playerId, name), sp, before, after, at, Map.of("how", how));
                    if (online != null) {
                        module.msg().send(online, after == 0 ? "spawner.removed-all" : "spawner.removed", mob(sp.type),
                                Msg.p("amount", take), Msg.p("left", after));
                        module.core().sounds().play(online, "click");
                    }
                }, error -> {
                    sp.busy = false;
                    Player online = Bukkit.getPlayer(playerId);
                    if (online != null) {
                        deny(online, "spawner.error");
                    }
                });
    }

    // ================================================================== adding (owner S21)

    /** Right-click: add with a spawner item of the same type; sneak-right-click or an empty hand opens the menu. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null
                || event.getClickedBlock().getType() != Material.SPAWNER || !module.eligible(event.getClickedBlock().getWorld())) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack hand = event.getItem();
        // Spawn eggs would change the spawner's mob type.
        if (hand != null && hand.getType().name().endsWith("_SPAWN_EGG")) {
            event.setCancelled(true);
            return;
        }
        Spawner sp = spawners.get(Pos.of(event.getClickedBlock()));
        if (sp == null) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            event.setCancelled(true);
            return;
        }
        Optional<String> type = SpawnerItems.typeOf(hand);
        if (player.isSneaking() || hand == null || hand.isEmpty()) {
            event.setCancelled(true);
            module.core().menus().open(player, new SpawnerMenu(module, sp));
            return;
        }
        if (type.isEmpty()) {
            return; // anything else: vanilla (e.g. placing a block against the spawner)
        }
        event.setCancelled(true);
        if (!type.get().equals(sp.type)) {
            deny(player, "spawner.wrong-type", mob(sp.type));
            return;
        }
        add(player, sp, 1);
    }

    /** Adds up to {@code wanted} spawners of the block's type from the player's inventory (hand first). */
    void add(Player player, Spawner sp, int wanted) {
        if (sp.busy || sp.removed) {
            deny(player, "spawner.busy");
            return;
        }
        int limit = limit(sp);
        int room = Math.max(0, limit - sp.amount);
        if (room <= 0) {
            deny(player, "spawner.full", Msg.p("max", limit));
            return;
        }
        int taken = takeFromInventory(player.getInventory(), sp.type, Math.min(wanted, room));
        if (taken <= 0) {
            deny(player, "spawner.none-in-inventory", mob(sp.type));
            return;
        }
        int before = sp.amount;
        int after = before + taken;
        sp.busy = true;
        UUID playerId = player.getUniqueId();
        String name = player.getName();
        Pos p = sp.pos;
        Location at = player.getLocation();
        Consumer<Throwable> refund = error -> {
            sp.busy = false;
            Player online = Bukkit.getPlayer(playerId);
            giveSpawners(online, at, sp.type, taken, "spawner-refund:" + name);
            if (online != null) {
                deny(online, "spawner.error");
            }
        };
        // SECFIX1 F-01b: the spawner items left the inventory; the player file is saved before the stack grows in the
        // database, so a crash can't give the items back while the stack keeps them. A failed save refunds.
        then(net.skybornmc.skyblock.common.player.PlayerSaves.commitAfterSave(player, "adding spawners",
                        () -> module.core().database().async(c -> store.change(c, p.world(), p.x(), p.y(), p.z(), sp.type, before, after,
                                System.currentTimeMillis()))),
                ok -> {
                    if (!Boolean.TRUE.equals(ok)) {
                        refund.accept(null);
                        return;
                    }
                    sp.busy = false;
                    sp.amount = after;
                    World world = Bukkit.getWorld(p.world());
                    audit("spawner_add", Actor.player(playerId, name), sp, before, after, world == null ? null : p.center(world), Map.of());
                    Player online = Bukkit.getPlayer(playerId);
                    if (online != null) {
                        module.msg().send(online, "spawner.added", mob(sp.type), Msg.p("amount", taken), Msg.p("count", after),
                                Msg.p("max", limit(sp)));
                        module.core().sounds().play(online, "success");
                    }
                }, refund);
    }

    /** Spawner items of {@code type} the player carries. */
    static int countInInventory(PlayerInventory inventory, String type) {
        int n = 0;
        for (ItemStack stack : inventory.getStorageContents()) {
            if (stack != null && SpawnerItems.typeOf(stack).filter(type::equals).isPresent()) {
                n += stack.getAmount();
            }
        }
        ItemStack off = inventory.getItemInOffHand();
        if (SpawnerItems.typeOf(off).filter(type::equals).isPresent()) {
            n += off.getAmount();
        }
        return n;
    }

    /** Removes up to {@code amount} spawner items of {@code type} (main hand first); returns how many were removed. */
    static int takeFromInventory(PlayerInventory inventory, String type, int amount) {
        int left = amount;
        int held = inventory.getHeldItemSlot();
        ItemStack[] contents = inventory.getStorageContents();
        int[] order = new int[contents.length];
        order[0] = held;
        for (int i = 0, j = 1; i < contents.length && j < order.length; i++) {
            if (i != held) {
                order[j++] = i;
            }
        }
        for (int slot : order) {
            if (left <= 0) {
                break;
            }
            ItemStack stack = contents[slot];
            if (stack == null || SpawnerItems.typeOf(stack).filter(type::equals).isEmpty()) {
                continue;
            }
            int use = Math.min(left, stack.getAmount());
            left -= use;
            if (use >= stack.getAmount()) {
                inventory.setItem(slot, null);
            } else {
                stack.setAmount(stack.getAmount() - use);
                inventory.setItem(slot, stack);
            }
        }
        if (left > 0) {
            ItemStack off = inventory.getItemInOffHand();
            if (SpawnerItems.typeOf(off).filter(type::equals).isPresent()) {
                int use = Math.min(left, off.getAmount());
                left -= use;
                off.setAmount(off.getAmount() - use);
                inventory.setItemInOffHand(off.getAmount() <= 0 ? null : off);
            }
        }
        return amount - left;
    }

    // ================================================================== items and staff give

    /** Gives {@code amount} spawner items (stacks of 64); what doesn't fit drops at the player (or at {@code fallback}). */
    void giveSpawners(Player player, Location fallback, String type, int amount, String source) {
        int left = amount;
        while (left > 0) {
            int n = Math.min(64, left);
            Optional<ItemStack> item = module.spawnerItems().create(type, n, source);
            if (item.isEmpty()) {
                module.core().plugin().getLogger().warning("Stacking: spawner type '" + type + "' is no longer in stacking.yml; "
                        + amount + " spawner item(s) could not be created for " + (player == null ? "an offline player" : player.getName()));
                return;
            }
            give(player, fallback, item.get(), n);
            left -= n;
        }
    }

    private void give(Player player, Location fallback, ItemStack item, int amount) {
        if (player != null) {
            // Owner answer 7: never dropped; what does not fit goes to /box (the box tells the player).
            net.skybornmc.skyblock.common.box.ItemBox.give(module.core().services(), player, net.skybornmc.skyblock.common.box.BoxSource.SPAWNER, item);
        } else if (fallback != null && fallback.getWorld() != null) {
            fallback.getWorld().dropItem(fallback, item);
        } else {
            module.core().plugin().getLogger().warning("Stacking: " + amount + " item(s) could not be handed out (no player, no place)");
        }
    }

    /** {@code /spawner give}: staff hand out spawner items (approved item-give pattern), audited. */
    void staffGive(org.bukkit.command.CommandSender sender, Player target, String type, int amount) {
        int tier = module.tiers().tier(type).orElse(0);
        giveSpawners(target, target.getLocation(), type, amount, "admin:" + sender.getName());
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("type", type);
        details.put("tier", tier);
        module.core().audit().logPlayer(AuditCategory.STAFF, "spawner_give", Actor.of(sender), target.getUniqueId(), target.getName(),
                (long) amount, target.getLocation(), details);
        module.msg().send(sender, "give.done", Msg.p("player", target.getName()), Msg.p("amount", amount), mob(type), Msg.p("tier", tier));
        if (!target.equals(sender)) {
            module.msg().send(target, "give.received", Msg.p("amount", amount), mob(type));
        }
    }

    // ================================================================== helpers

    static TagResolver mob(String type) {
        return Msg.mm("mob", SpawnerItems.mobTag(type));
    }

    private void deny(Player player, String key, TagResolver... placeholders) {
        module.msg().send(player, key, placeholders);
        module.core().sounds().play(player, "error");
    }

    private void audit(String action, Actor actor, Spawner sp, int before, int after, Location at, Map<String, Object> extra) {
        Map<String, Object> details = new LinkedHashMap<>(extra);
        details.put("type", sp.type);
        details.put("before", before);
        details.put("after", after);
        details.put("world", sp.pos.world());
        details.put("pos", sp.pos.x() + "," + sp.pos.y() + "," + sp.pos.z());
        module.core().audit().log(AuditCategory.ISLAND, action, actor, sp.island == null ? null : sp.island.toString(), null,
                (long) Math.abs(after - before), at, details);
    }

    /** Runs {@code ok} or {@code fail} on the main thread when the database work finishes. */
    private <T> void then(CompletableFuture<T> future, Consumer<T> ok, Consumer<Throwable> fail) {
        future.whenComplete((value, error) -> module.core().scheduler().main("stacking-spawner", () -> {
            if (error != null) {
                module.core().plugin().getLogger().log(Level.WARNING, "Stacking: spawner database change failed", error);
                fail.accept(error);
            } else {
                ok.accept(value);
            }
        }));
    }

    /** Text for the menu: seconds to the next spawn. */
    long secondsToNext(Spawner sp) {
        return Math.max(0, (sp.nextTick - Bukkit.getCurrentTick()) / 20);
    }

    boolean isActive(Spawner sp) {
        World world = Bukkit.getWorld(sp.pos.world());
        return world != null && active(sp, world);
    }

    static String number(double value) {
        return value >= 100 ? Format.number(Math.round(value)) : String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}
