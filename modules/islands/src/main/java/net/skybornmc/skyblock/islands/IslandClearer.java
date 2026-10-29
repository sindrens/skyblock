package net.skybornmc.skyblock.islands;

import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.MainThread;
import net.skybornmc.skyblock.common.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Empties the region of removed islands (M2.7), one slot at a time, in small slices ({@link ClearPlan}): with
 * FastAsyncWorldEdit off the main thread when it is installed, otherwise with a throttled main-thread clearer that skips
 * empty 16×16×16 sections and stops after {@code lifecycle.clear.fallback-blocks-per-tick} positions per tick. After the
 * blocks, every non-player entity in the slice's chunks is removed. Only chunks that were ever generated are touched, at
 * most one slice of chunks is held loaded (plugin chunk tickets, released right after), and when the whole plan is done
 * the slot goes from {@code clearing} to {@code free} ({@link SlotState}) and can take a new island. Jobs that were not
 * finished (restart, crash) are still {@code clearing} in the database and resume on the next start.
 */
final class IslandClearer {

    /** One slot to empty. {@code sizeChunks} = the removed island's size (0 = unknown). */
    record Job(int slotId, int gridX, int gridZ, UUID islandId, int sizeChunks, String reason) {
    }

    /** How far a slot's clearing got (admin overview). {@code total} 0 = waiting in the queue. */
    record Progress(int slotId, int done, int total) {
    }

    private static final int MAX_SLICE_ATTEMPTS = 3;

    private final IslandsModule module;
    private final Core core;
    private final IslandManager islands;
    private final Executor main;
    private final Executor async;
    private final Deque<Job> queue = new ArrayDeque<>();
    private final Set<Integer> queued = new HashSet<>();
    private final List<Chunk> held = new ArrayList<>();
    private boolean running;
    private Scheduler.Task task;

    // The job being worked on (main thread only).
    private Job current;
    private ClearPlan plan;
    private World world;
    private int slice;
    private int attempts;
    private long startedAt;
    private long blocks;
    private int entities;
    private boolean faweFailed;
    private String mode;

    IslandClearer(IslandsModule module, IslandManager islands) {
        this.module = module;
        this.core = module.core();
        this.islands = islands;
        this.main = new MainThread(core.plugin());
        this.async = task -> Bukkit.getScheduler().runTaskAsynchronously(core.plugin(), task);
    }

    private IslandsConfig config() {
        return module.config();
    }

    /** Starts working through the queue (after {@code delayTicks}). */
    void start(long delayTicks) {
        running = true;
        if (current == null) {
            schedule(delayTicks, this::next);
        }
    }

    /** Stops (module disable): releases held chunks; unfinished slots stay {@code clearing} and resume next start. */
    void stop() {
        running = false;
        if (task != null) {
            task.cancel();
            task = null;
        }
        release();
        queue.clear();
        queued.clear();
        current = null;
        plan = null;
    }

    /** Adds a slot to the queue (ignored if it is already queued or being cleared). */
    void enqueue(Job job) {
        if (!queued.add(job.slotId())) {
            return;
        }
        queue.add(job);
        if (running && current == null && (task == null || task.cancelled())) {
            schedule(1, this::next);
        }
    }

    /** Progress of a slot that is queued or being cleared. */
    Optional<Progress> progress(int slotId) {
        if (current != null && current.slotId() == slotId && plan != null) {
            return Optional.of(new Progress(slotId, slice, plan.slices().size()));
        }
        return queued.contains(slotId) ? Optional.of(new Progress(slotId, 0, 0)) : Optional.empty();
    }

    /** Slots waiting or being cleared. */
    int pending() {
        return queued.size();
    }

    private void schedule(long ticks, Runnable action) {
        task = core.scheduler().later("island-clear", Math.max(1, ticks), action);
    }

    // ------------------------------------------------------------------ job flow

    private void next() {
        task = null;
        if (!running || current != null) {
            return;
        }
        Job job = queue.poll();
        if (job == null) {
            return;
        }
        world = Bukkit.getWorld(config().world);
        if (world == null) {
            queue.addFirst(job);
            core.plugin().getLogger().warning("Islands: world '" + config().world + "' is not loaded; clearing slot " + job.slotId()
                    + " waits.");
            schedule(20L * 60, this::next);
            return;
        }
        current = job;
        plan = ClearPlan.of(job.gridX(), job.gridZ(), job.sizeChunks(), config().maxSize(), config().clearExtraRings,
                config().clearChunksPerSlice);
        slice = 0;
        attempts = 0;
        blocks = 0;
        entities = 0;
        faweFailed = false;
        startedAt = System.currentTimeMillis();
        mode = useFawe() ? "fawe" : "bukkit";
        core.audit().log(AuditCategory.ISLAND, "island_clear_start", Actor.system("islands"), idText(job), null, null, null,
                Map.of("slot", job.slotId(), "grid_x", job.gridX(), "grid_z", job.gridZ(), "reason", job.reason(),
                        "chunks", plan.chunks(), "slices", plan.slices().size(), "mode", mode));
        schedule(1, this::step);
    }

    private boolean useFawe() {
        return config().clearUseFawe && !faweFailed && IslandBuilder.faweAvailable();
    }

    private void step() {
        task = null;
        Job job = current;
        if (!running || job == null) {
            return;
        }
        if (slice >= plan.slices().size()) {
            finish(job);
            return;
        }
        ClearPlan.Slice part = plan.slices().get(slice);
        boolean fawe = useFawe();
        CompletableFuture<Void> work = loadSlice(part).thenCompose(chunks -> {
            if (chunks.isEmpty()) {
                return CompletableFuture.completedFuture(null); // never generated: nothing to do
            }
            CompletableFuture<Void> emptied = fawe ? faweBlocks(chunks) : bukkitBlocks(chunks);
            return emptied.thenRunAsync(() -> removeEntities(chunks), main);
        });
        work.whenCompleteAsync((ignored, error) -> {
            release();
            if (!running || current != job) {
                return;
            }
            if (error != null) {
                Throwable cause = Database.cause(error);
                if (fawe) {
                    faweFailed = true; // redo this slice (and the rest) with the built-in clearer
                    mode = "fawe+bukkit";
                    core.plugin().getLogger().log(Level.WARNING, "Islands: FAWE clearing of slot " + job.slotId()
                            + " failed, using the built-in clearer", cause);
                } else if (++attempts >= MAX_SLICE_ATTEMPTS) {
                    core.plugin().getLogger().log(Level.SEVERE, "Islands: clearing slice " + slice + " of slot " + job.slotId()
                            + " failed " + attempts + " times; skipped", cause);
                    core.audit().log(AuditCategory.ISLAND, "island_clear_error", Actor.system("islands"), idText(job), null, null, null,
                            Map.of("slot", job.slotId(), "slice", slice, "error", String.valueOf(cause.getMessage())));
                    attempts = 0;
                    slice++;
                }
                schedule(config().clearTicksBetweenSlices * 10L, this::step);
                return;
            }
            attempts = 0;
            slice++;
            schedule(config().clearTicksBetweenSlices, this::step);
        }, main);
    }

    private void finish(Job job) {
        long millis = System.currentTimeMillis() - startedAt;
        islands.store().freeSlot(job.slotId()).whenCompleteAsync((freed, error) -> {
            if (!running || current != job) {
                return;
            }
            if (error != null) {
                core.plugin().getLogger().log(Level.SEVERE, "Islands: freeing slot " + job.slotId() + " failed; retrying",
                        Database.cause(error));
                schedule(20L * 30, () -> finish(job));
                return;
            }
            if (freed) {
                islands.slotState(job.slotId(), SlotState.FREE);
            } else {
                core.plugin().getLogger().warning("Islands: slot " + job.slotId() + " was no longer clearing when it was emptied.");
            }
            core.audit().log(AuditCategory.ISLAND, "island_slot_freed", Actor.system("islands"), idText(job), null, null, null,
                    Map.of("slot", job.slotId(), "grid_x", job.gridX(), "grid_z", job.gridZ(), "blocks", blocks,
                            "entities", entities, "millis", millis, "mode", mode, "freed", freed));
            queued.remove(job.slotId());
            current = null;
            plan = null;
            schedule(1, this::next);
        }, main);
    }

    private static String idText(Job job) {
        return job.islandId() == null ? "slot:" + job.slotId() : job.islandId().toString();
    }

    /**
     * Loads the slice's chunks that were ever generated (async, never generates; no disk access on the main thread) and
     * holds them with a plugin ticket until {@link #release}. Completes on the main thread.
     */
    private CompletableFuture<List<Chunk>> loadSlice(ClearPlan.Slice part) {
        World target = world;
        List<CompletableFuture<Chunk>> loads = new ArrayList<>(part.chunks());
        for (int cx = part.minChunkX(); cx <= part.maxChunkX(); cx++) {
            loads.add(target.getChunkAtAsync(cx, part.chunkZ(), false));
        }
        return CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new)).thenApplyAsync(ignored -> {
            List<Chunk> chunks = new ArrayList<>(loads.size());
            for (CompletableFuture<Chunk> load : loads) {
                Chunk chunk = load.join();
                if (chunk != null) {
                    chunk.addPluginChunkTicket(core.plugin());
                    held.add(chunk);
                    chunks.add(chunk);
                }
            }
            return chunks;
        }, main);
    }

    // ------------------------------------------------------------------ blocks

    /** FAWE: each chunk column as one cuboid (world height), off the main thread, with a timeout. */
    private CompletableFuture<Void> faweBlocks(List<Chunk> loaded) {
        World target = world;
        List<int[]> chunks = new ArrayList<>(loaded.size());
        for (Chunk chunk : loaded) {
            chunks.add(new int[]{chunk.getX(), chunk.getZ()});
        }
        int minY = target.getMinHeight();
        int maxY = target.getMaxHeight() - 1;
        return CompletableFuture.supplyAsync(() -> {
            long changed = 0;
            for (int[] chunk : chunks) {
                try {
                    changed += FaweSchematics.clear(target, chunk[0] << 4, minY, chunk[1] << 4, (chunk[0] << 4) + 15, maxY,
                            (chunk[1] << 4) + 15);
                } catch (Exception e) {
                    throw new IllegalStateException(e.getMessage(), e);
                }
            }
            return changed;
        }, async).orTimeout(config().clearFaweTimeoutSeconds, TimeUnit.SECONDS).thenAcceptAsync(changed -> blocks += changed, main);
    }

    /** Built-in: one chunk after the other, each emptied over as many ticks as the block budget needs. */
    private CompletableFuture<Void> bukkitBlocks(List<Chunk> chunks) {
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (Chunk chunk : chunks) {
            chain = chain.thenComposeAsync(ignored -> new Wipe(chunk).start(), main);
        }
        return chain;
    }

    /** Empties one chunk with the per-tick block budget (main thread). */
    private final class Wipe {
        private final Chunk chunk;
        private final ChunkSnapshot snapshot;
        private final int minY;
        private final int sections;
        private final CompletableFuture<Void> done = new CompletableFuture<>();
        private int section;

        Wipe(Chunk chunk) {
            this.chunk = chunk;
            this.snapshot = chunk.getChunkSnapshot(false, false, false);
            this.minY = chunk.getWorld().getMinHeight();
            this.sections = (chunk.getWorld().getMaxHeight() - minY) >> 4;
        }

        CompletableFuture<Void> start() {
            tick();
            return done;
        }

        private void tick() {
            if (!running) {
                done.cancel(false);
                return;
            }
            try {
                int budget = config().clearFallbackBlocksPerTick;
                while (section < sections && budget > 0) {
                    if (!snapshot.isSectionEmpty(section)) {
                        budget -= wipeSection(section);
                    }
                    section++;
                }
            } catch (RuntimeException e) {
                done.completeExceptionally(e);
                return;
            }
            if (section >= sections) {
                done.complete(null);
            } else {
                core.scheduler().later("island-clear-chunk", 1, this::tick);
            }
        }

        /** Sets every non-air block of one section to air without physics. Returns the positions checked (4096). */
        private int wipeSection(int index) {
            int baseY = minY + (index << 4);
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        Material type = snapshot.getBlockType(x, baseY + y, z);
                        if (!type.isAir()) {
                            chunk.getBlock(x, baseY + y, z).setType(Material.AIR, false);
                            blocks++;
                        }
                    }
                }
            }
            return 16 * 16 * 16;
        }
    }

    // ------------------------------------------------------------------ entities

    /** Removes every non-player entity in the (loaded, held) chunks. Main thread. */
    private void removeEntities(List<Chunk> chunks) {
        for (Chunk chunk : chunks) {
            for (Entity entity : chunk.getEntities()) {
                if (!(entity instanceof Player) && entity.isValid()) {
                    entity.remove();
                    entities++;
                }
            }
        }
    }

    /** Releases every chunk ticket this clearer holds. */
    private void release() {
        for (Chunk chunk : held) {
            chunk.removePluginChunkTicket(core.plugin());
        }
        held.clear();
    }
}
