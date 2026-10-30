package net.skybornmc.skyblock.progression;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.island.IslandService;
import net.skybornmc.skyblock.common.sell.SellService;
import net.skybornmc.skyblock.common.shop.ShopPrices;
import net.skybornmc.skyblock.common.stacking.StackingService;
import net.skybornmc.skyblock.common.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Block worth of the island level (PHASE-5 M5.2 source 8): live tracking, full rescans and storage.
 * <ul>
 *   <li><b>Live</b> (cheap): place, break, explosions, burning and pistons change the worth of the chunk they happen in,
 *   only inside an island's border. Spawner stacks are counted again from the chunk's spawner blocks one tick later
 *   (adding to a stack cancels the place event, so both cancelled and done events are watched for spawners).</li>
 *   <li><b>Rescan</b> (exact): the island's chunks are loaded a few per tick ({@code blocks.chunks-per-tick}), copied
 *   on the main thread (chunk snapshot + spawner blocks) and counted off the main thread. Nightly for islands that
 *   changed that day, on demand (Recalculate button, 10 min cooldown) and for islands never scanned.</li>
 *   <li><b>Storage:</b> changed chunks are written every {@code blocks.flush-seconds} and on shutdown.</li>
 * </ul>
 * Main thread only, except the block count of a rescan.
 */
final class BlockWorth implements Listener {

    /** One running rescan. */
    private static final class Job {
        final UUID island;
        final World world;
        final Deque<long[]> todo = new ArrayDeque<>();
        final Set<Long> chunks = new HashSet<>();
        final double before;
        final Consumer<Double> done;
        int inFlight;
        boolean aborted;

        Job(UUID island, World world, double before, Consumer<Double> done) {
            this.island = island;
            this.world = world;
            this.before = before;
            this.done = done;
        }
    }

    private record SpawnerCheck(UUID island, String world, int chunkX, int chunkZ) {
    }

    private final Core core;
    private final LevelConfig config;
    private final BlockValuesConfig valuesConfig;
    private final BlockWorthLedger ledger = new BlockWorthLedger();
    private final LevelStore store;
    private final int season;
    private final Consumer<UUID> changed;
    private final Map<UUID, Consumer<Double>> queued = new LinkedHashMap<>();
    private final Set<SpawnerCheck> spawnerChecks = new HashSet<>();
    private volatile double[] worthByOrdinal = new double[0];
    private BlockValues values;
    private Job job;
    private Scheduler.Task tickTask;
    private Scheduler.Task flushTask;
    private CompletableFuture<?> writing = CompletableFuture.completedFuture(null);
    private boolean closed;

    /**
     * @param changed called (main thread) with the island whose worth changed; the level recalculation is debounced there
     */
    BlockWorth(Core core, LevelConfig config, BlockValuesConfig valuesConfig, LevelStore store, int season, Consumer<UUID> changed) {
        this.core = core;
        this.config = config;
        this.valuesConfig = valuesConfig;
        this.store = store;
        this.season = season;
        this.changed = changed;
    }

    BlockWorthLedger ledger() {
        return ledger;
    }

    // ------------------------------------------------------------------ lifecycle

    void start() {
        rebuildValues();
        tickTask = core.scheduler().repeat("progression-blocks", 1L, 1L, this::tick);
        scheduleFlush();
    }

    void applyConfig() {
        rebuildValues();
        if (flushTask != null) {
            flushTask.cancel();
            scheduleFlush();
        }
    }

    private void scheduleFlush() {
        long period = Math.max(2, config.blocksFlushSeconds) * 20L;
        flushTask = core.scheduler().repeat("progression-blocks-flush", period, period, this::flush);
    }

    /** Worth per material from block-values.yml, the shop and the sell prices (main thread; read by rescans off it). */
    void rebuildValues() {
        List<String> problems = new ArrayList<>();
        ShopPrices shop = core.services().optional(ShopPrices.class).orElse(null);
        SellService sell = core.services().optional(SellService.class).orElse(null);
        values = BlockValues.load(valuesConfig,
                id -> shop == null ? OptionalLong.empty() : shop.buyPrice(id),
                id -> sell == null ? OptionalDouble.empty()
                        : sell.price(id).map(p -> OptionalDouble.of(p.base())).orElse(OptionalDouble.empty()),
                problems::add);
        problems.forEach(p -> core.plugin().getLogger().warning(p));
        Material[] all = Material.values();
        double[] table = new double[all.length];
        for (Material m : all) {
            if (m.isLegacy() || !m.isBlock()) {
                continue;
            }
            table[m.ordinal()] = values.worth(m.getKey().getKey());
        }
        worthByOrdinal = table;
    }

    double worth(Material type) {
        double[] table = worthByOrdinal;
        int i = type == null ? -1 : type.ordinal();
        return i >= 0 && i < table.length ? table[i] : 0;
    }

    void shutdown() {
        closed = true;
        if (tickTask != null) {
            tickTask.cancel();
        }
        if (flushTask != null) {
            flushTask.cancel();
        }
        if (job != null) {
            job.aborted = true;
        }
        try {
            writing.get(5, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            // a failed write marked its rows dirty again; written below
        }
        List<LevelStore.WorthRow> rows = ledger.drainDirty();
        if (rows.isEmpty()) {
            return;
        }
        try {
            core.database().blockingTransaction(c -> store.saveWorth(c, season, rows, System.currentTimeMillis()));
        } catch (RuntimeException e) {
            core.plugin().getLogger().log(Level.SEVERE, "Progression: " + rows.size() + " block worth rows could not be saved on shutdown", e);
        }
    }

    void flush() {
        if (closed || !writing.isDone() || !ledger.hasDirty()) {
            return;
        }
        List<LevelStore.WorthRow> rows = ledger.drainDirty();
        long now = System.currentTimeMillis();
        writing = core.database().transaction(c -> store.saveWorth(c, season, rows, now)).whenComplete((n, error) -> {
            if (error != null) {
                core.scheduler().ensureMain("progression-blocks-retry", () -> ledger.markDirty(rows));
                core.plugin().getLogger().log(Level.WARNING, "Progression: writing block worth failed; retrying", Database.cause(error));
            }
        });
    }

    // ------------------------------------------------------------------ live tracking

    private IslandService islands() {
        return core.services().optional(IslandService.class).orElse(null);
    }

    /** Adds {@code delta} worth at {@code location} if it is inside an island's border. */
    private void change(Location location, double delta) {
        if (delta == 0 || location == null) {
            return;
        }
        IslandService islands = islands();
        if (islands == null || location.getWorld() == null || !islands.isIslandWorld(location.getWorld())) {
            return;
        }
        islands.islandAt(location).ifPresent(info -> {
            ledger.add(info.id(), location.getBlockX() >> 4, location.getBlockZ() >> 4, delta);
            changed.accept(info.id());
        });
    }

    private boolean live() {
        return config.trackLive && !closed;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlace(BlockPlaceEvent event) {
        if (!live()) {
            return;
        }
        boolean spawner = event.getItemInHand().getType() == Material.SPAWNER || event.getBlockPlaced().getType() == Material.SPAWNER;
        if (spawner) {
            checkSpawners(event.getBlockPlaced());
            checkSpawners(event.getBlockAgainst());
        }
        if (event.isCancelled() || !event.canBuild()) {
            return;
        }
        if (event instanceof BlockMultiPlaceEvent multi) {
            for (BlockState replaced : multi.getReplacedBlockStates()) {
                Block block = replaced.getBlock();
                change(block.getLocation(), worth(block.getType()) - worth(replaced.getType()));
            }
            return;
        }
        Block block = event.getBlockPlaced();
        change(block.getLocation(), worth(block.getType()) - worth(event.getBlockReplacedState().getType()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBreak(BlockBreakEvent event) {
        if (!live()) {
            return;
        }
        Block block = event.getBlock();
        if (block.getType() == Material.SPAWNER) {
            checkSpawners(block); // stack decrements cancel the break
        }
        if (!event.isCancelled()) {
            change(block.getLocation(), -worth(block.getType()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (live()) {
            exploded(event.blockList());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (live()) {
            exploded(event.blockList());
        }
    }

    private void exploded(List<Block> blocks) {
        for (Block b : blocks) {
            if (b.getType() == Material.SPAWNER) {
                checkSpawners(b);
            }
            change(b.getLocation(), -worth(b.getType()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (live()) {
            change(event.getBlock().getLocation(), -worth(event.getBlock().getType()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (live()) {
            moved(event.getBlocks(), event.getDirection());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (live()) {
            moved(event.getBlocks(), event.getDirection());
        }
    }

    /** Moved blocks leave their chunk and arrive in the next one (or leave the island when pushed over the border). */
    private void moved(List<Block> blocks, BlockFace direction) {
        for (Block b : blocks) {
            double w = worth(b.getType());
            if (w == 0) {
                continue;
            }
            change(b.getLocation(), -w);
            change(b.getRelative(direction).getLocation(), w);
        }
    }

    /** Counts the spawner worth of the block's chunk again next tick (deduplicated). */
    private void checkSpawners(Block block) {
        IslandService islands = islands();
        if (block == null || islands == null || !islands.isIslandWorld(block.getWorld())) {
            return;
        }
        islands.islandAt(block.getLocation()).ifPresent(info ->
                spawnerChecks.add(new SpawnerCheck(info.id(), block.getWorld().getName(), block.getX() >> 4, block.getZ() >> 4)));
    }

    private void runSpawnerChecks() {
        if (spawnerChecks.isEmpty()) {
            return;
        }
        List<SpawnerCheck> list = new ArrayList<>(spawnerChecks);
        spawnerChecks.clear();
        for (SpawnerCheck check : list) {
            World world = Bukkit.getWorld(check.world());
            if (world == null || !world.isChunkLoaded(check.chunkX(), check.chunkZ())) {
                continue;
            }
            ledger.setSpawners(check.island(), check.chunkX(), check.chunkZ(), spawnerWorth(world.getChunkAt(check.chunkX(), check.chunkZ())));
            changed.accept(check.island());
        }
    }

    /** Spawner stacks of a loaded chunk: spawners per stack x the tier's worth (main thread). */
    private double spawnerWorth(Chunk chunk) {
        StackingService stacking = core.services().optional(StackingService.class).orElse(null);
        double sum = 0;
        for (BlockState state : chunk.getTileEntities()) {
            if (!(state instanceof CreatureSpawner spawner)) {
                continue;
            }
            EntityType type = spawner.getSpawnedType();
            if (type == null || stacking == null) {
                continue;
            }
            OptionalInt tier = stacking.spawnerTier(type.getKey().getKey());
            if (tier.isEmpty()) {
                continue;
            }
            int count = Math.max(1, stacking.spawnerCount(state.getBlock()));
            sum += values.spawnerWorth(tier.getAsInt(), count);
        }
        return sum;
    }

    // ------------------------------------------------------------------ rescans

    /**
     * Queues a full rescan of the island (once; a queued island keeps its place).
     *
     * @param done main thread, with the new worth, when the rescan finished (not called if it failed or was cancelled)
     */
    boolean queueRescan(UUID island, Consumer<Double> done) {
        if (island == null || closed) {
            return false;
        }
        if (job != null && job.island.equals(island)) {
            return false;
        }
        Consumer<Double> before = queued.get(island);
        queued.put(island, before == null ? done : (before == done || done == null ? before : before.andThen(done)));
        return before == null;
    }

    boolean scanning(UUID island) {
        return (job != null && job.island.equals(island)) || queued.containsKey(island);
    }

    int queuedRescans() {
        return queued.size() + (job == null ? 0 : 1);
    }

    private void tick() {
        if (closed) {
            return;
        }
        runSpawnerChecks();
        if (job == null) {
            startNext();
        }
        if (job == null) {
            return;
        }
        int limit = Math.max(1, config.chunksPerTick);
        int started = 0;
        while (started < limit && job.inFlight < limit * 4 && !job.todo.isEmpty()) {
            long[] xz = job.todo.poll();
            loadChunk(job, (int) xz[0], (int) xz[1]);
            started++;
        }
        if (job.todo.isEmpty() && job.inFlight == 0) {
            finish(job);
        }
    }

    private void startNext() {
        IslandService islands = islands();
        while (!queued.isEmpty() && islands != null) {
            UUID island = queued.keySet().iterator().next();
            Consumer<Double> done = queued.remove(island);
            IslandService.IslandInfo info = islands.island(island).orElse(null);
            World world = info == null ? null : Bukkit.getWorld(info.bounds().world());
            if (world == null) {
                continue;
            }
            Job next = new Job(island, world, ledger.worth(island), done == null ? w -> { } : done);
            IslandService.Bounds b = info.bounds();
            for (int cx = b.minX() >> 4; cx <= b.maxX() >> 4; cx++) {
                for (int cz = b.minZ() >> 4; cz <= b.maxZ() >> 4; cz++) {
                    next.todo.add(new long[]{cx, cz});
                    next.chunks.add(BlockWorthLedger.chunkKey(cx, cz));
                }
            }
            ledger.clearOutside(island, next.chunks);
            job = next;
            return;
        }
    }

    private void loadChunk(Job j, int cx, int cz) {
        j.inFlight++;
        j.world.getChunkAtAsync(cx, cz, false).whenComplete((chunk, error) -> core.scheduler().ensureMain("progression-scan", () -> {
            if (j.aborted || closed) {
                j.inFlight--;
                return;
            }
            if (error != null || chunk == null) {
                // never generated (or failed): nothing placed there
                ledger.finishScan(j.island, cx, cz, 0, 0);
                j.inFlight--;
                return;
            }
            ledger.beginScan(j.island, cx, cz);
            ChunkSnapshot snapshot = chunk.getChunkSnapshot(false, false, false);
            double spawners = spawnerWorth(chunk);
            int minY = j.world.getMinHeight();
            int maxY = j.world.getMaxHeight();
            double[] table = worthByOrdinal;
            core.scheduler().async(() -> {
                double blocks;
                try {
                    blocks = count(snapshot, table, minY, maxY);
                } catch (RuntimeException e) {
                    core.plugin().getLogger().log(Level.WARNING, "Progression: counting chunk " + cx + "," + cz + " failed", e);
                    blocks = Double.NaN;
                }
                double result = blocks;
                core.scheduler().main("progression-scan-done", () -> {
                    j.inFlight--;
                    if (j.aborted || Double.isNaN(result)) {
                        ledger.abortScan(j.island, cx, cz);
                    } else {
                        ledger.finishScan(j.island, cx, cz, result, spawners);
                    }
                });
            });
        }));
    }

    /** Worth of every block in a chunk copy (off the main thread); empty 16x16x16 sections are skipped. */
    static double count(ChunkSnapshot snapshot, double[] table, int minY, int maxY) {
        double sum = 0;
        int sections = (maxY - minY) >> 4;
        for (int s = 0; s < sections; s++) {
            if (snapshot.isSectionEmpty(s)) {
                continue;
            }
            int baseY = minY + (s << 4);
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        int i = snapshot.getBlockType(x, baseY + y, z).ordinal();
                        if (i < table.length) {
                            sum += table[i];
                        }
                    }
                }
            }
        }
        return sum;
    }

    private void finish(Job j) {
        job = null;
        double after = ledger.worth(j.island);
        changed.accept(j.island);
        try {
            j.done.accept(after);
        } catch (RuntimeException e) {
            core.plugin().getLogger().log(Level.WARNING, "Progression: rescan callback failed", e);
        }
    }

    /** Island removed: forget everything about it (stored rows stay for restores). */
    void forget(UUID island) {
        queued.remove(island);
        if (job != null && job.island.equals(island)) {
            job.aborted = true;
            job = null;
        }
        ledger.forget(island);
    }

    /** Applies stored rows (load at start). */
    void restore(List<LevelStore.WorthRow> rows) {
        for (LevelStore.WorthRow r : rows) {
            ledger.restore(r.island(), r.chunkX(), r.chunkZ(), r.blocks(), r.spawners());
        }
    }
}
