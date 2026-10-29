package net.skybornmc.skyblock.depths.zones;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.audience.Audience;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Scheduler;
import net.skybornmc.skyblock.depths.DepthsConfig;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builds placeholder parts ({@link Shapes.Part}) in throttled slices: chunks of a part are loaded asynchronously and
 * held with a plugin ticket, then at most {@code generate.blocks-per-tick} blocks are changed per tick (and at most
 * {@code generate.max-millis-per-tick} ms spent). Blocks that already match are skipped, so a second run changes
 * nothing. One build at a time.
 */
public final class ZoneGenerator {

    private enum Stage { LOADING, BUILDING }

    private static final class Job {
        final List<Shapes.Part> parts;
        final World world;
        final UUID player;
        final Actor actor;
        final String target;
        final long started = System.currentTimeMillis();
        int part = -1;
        Stage stage = Stage.LOADING;
        final List<Chunk> held = new ArrayList<>();
        int pendingChunks;
        int x;
        int y;
        int z;
        long changed;
        long partChanged;
        long visited;
        long totalPositions;
        long lastProgress = System.currentTimeMillis();
        Map<Piece, BlockData> blocks;
        Biome biome;

        Job(List<Shapes.Part> parts, World world, UUID player, Actor actor, String target) {
            this.parts = parts;
            this.world = world;
            this.player = player;
            this.actor = actor;
            this.target = target;
            for (Shapes.Part p : parts) {
                totalPositions += p.box().volume();
            }
        }

        Shapes.Part current() {
            return parts.get(part);
        }
    }

    private final Core core;
    private final DepthsConfig config;
    private final ModuleMessages msg;
    private Job job;
    private Scheduler.Task task = Scheduler.Task.NONE;

    public ZoneGenerator(Core core, DepthsConfig config, ModuleMessages msg) {
        this.core = core;
        this.config = config;
        this.msg = msg;
    }

    public boolean running() {
        return job != null;
    }

    /** "done/total parts, percent" of the running build. */
    public int[] progress() {
        Job j = job;
        if (j == null) {
            return new int[]{0, 0, 0};
        }
        return new int[]{Math.max(0, j.part), j.parts.size(), percent(j)};
    }

    private static int percent(Job j) {
        return j.totalPositions == 0 ? 100 : (int) Math.min(100, j.visited * 100 / j.totalPositions);
    }

    /**
     * Starts building {@code parts}. Returns false if a build is already running.
     *
     * @param player who started it (gets progress on the action bar), {@code null} for the console
     */
    public boolean start(List<Shapes.Part> parts, World world, Player player, Actor actor, String target) {
        if (job != null) {
            return false;
        }
        job = new Job(List.copyOf(parts), world, player == null ? null : player.getUniqueId(), actor, target);
        core.audit().log(AuditCategory.WORLD, "depths_generate_start", actor, target, null, null, null,
                Map.of("parts", parts.size(), "positions", job.totalPositions, "world", world.getName()));
        nextPart(job);
        task = core.scheduler().repeat("depths-generate", 1, 1, this::tick);
        return true;
    }

    /** Stops a running build (plugin disable); releases held chunks. */
    public void stop() {
        Job j = job;
        if (j == null) {
            return;
        }
        release(j);
        task.cancel();
        task = Scheduler.Task.NONE;
        job = null;
        core.audit().log(AuditCategory.WORLD, "depths_generate_stopped", j.actor, j.target, null, j.changed, null,
                Map.of("parts_done", Math.max(0, j.part), "parts", j.parts.size()));
        core.plugin().getLogger().warning("Depths: placeholder build '" + j.target + "' stopped at part "
                + Math.max(0, j.part) + "/" + j.parts.size() + " (" + j.changed + " blocks changed)");
    }

    private Audience audience(Job j) {
        if (j.player == null) {
            return Bukkit.getConsoleSender();
        }
        Player p = Bukkit.getPlayer(j.player);
        return p == null ? Audience.empty() : p;
    }

    // ------------------------------------------------------------------ parts

    private void nextPart(Job j) {
        release(j);
        j.part++;
        if (j.part >= j.parts.size()) {
            finish(j);
            return;
        }
        Shapes.Part part = j.current();
        j.blocks = resolve(j, part);
        j.biome = biome(j, part);
        j.partChanged = 0;
        Box b = part.box();
        j.x = b.minX();
        j.y = b.minY();
        j.z = b.minZ();
        j.stage = Stage.LOADING;
        int cx0 = b.minX() >> 4;
        int cx1 = b.maxX() >> 4;
        int cz0 = b.minZ() >> 4;
        int cz1 = b.maxZ() >> 4;
        j.pendingChunks = (cx1 - cx0 + 1) * (cz1 - cz0 + 1);
        for (int cx = cx0; cx <= cx1; cx++) {
            for (int cz = cz0; cz <= cz1; cz++) {
                j.world.getChunkAtAsync(cx, cz, true).whenComplete((chunk, error) ->
                        core.scheduler().ensureMain("depths-generate-chunk", () -> chunkLoaded(j, chunk)));
            }
        }
    }

    private void chunkLoaded(Job j, Chunk chunk) {
        if (job != j) {
            return; // build was stopped meanwhile
        }
        if (chunk != null && chunk.addPluginChunkTicket(core.plugin())) {
            j.held.add(chunk);
        }
        if (--j.pendingChunks <= 0) {
            j.stage = Stage.BUILDING;
        }
    }

    private void release(Job j) {
        for (Chunk chunk : j.held) {
            chunk.removePluginChunkTicket(core.plugin());
        }
        j.held.clear();
    }

    /** Piece → block for one part: its palette, then the fallback role, then the piece's default block. */
    private Map<Piece, BlockData> resolve(Job j, Shapes.Part part) {
        Map<Piece, BlockData> out = new EnumMap<>(Piece.class);
        Map<String, String> warned = new LinkedHashMap<>();
        for (Piece piece : Piece.values()) {
            String text = null;
            if (piece == Piece.RESOURCE) {
                text = part.resource();
            } else if (piece.paletteKey() != null) {
                text = part.palette().get(piece.paletteKey());
                if (text == null && piece.fallbackKey() != null) {
                    text = part.palette().get(piece.fallbackKey());
                }
            }
            BlockData data = text == null ? null : ZoneManager.blockData(text);
            if (data == null && text != null) {
                warned.put(text, piece.defaultBlock());
            }
            if (data == null) {
                data = ZoneManager.blockData(piece.defaultBlock());
            }
            out.put(piece, data == null ? Material.STONE.createBlockData() : data);
        }
        warned.forEach((bad, used) -> msg.send(audience(j), "zones.generate.bad-block", Msg.p("part", part.label()),
                Msg.p("block", bad), Msg.p("used", used)));
        return out;
    }

    private Biome biome(Job j, Shapes.Part part) {
        if (part.biome() == null) {
            return null;
        }
        NamespacedKey key = NamespacedKey.fromString(part.biome().contains(":") ? part.biome() : "minecraft:" + part.biome());
        Biome biome = key == null ? null : RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME).get(key);
        if (biome == null) {
            msg.send(audience(j), "zones.generate.bad-biome", Msg.p("part", part.label()), Msg.p("biome", part.biome()));
        }
        return biome;
    }

    // ------------------------------------------------------------------ tick

    private void tick() {
        Job j = job;
        if (j == null) {
            task.cancel();
            return;
        }
        long deadline = System.nanoTime() + config.generateMaxMillisPerTick * 1_000_000L;
        int changedBudget = config.generateBlocksPerTick;
        int visitBudget = config.generatePositionsPerTick;
        while (job == j && j.stage == Stage.BUILDING && changedBudget > 0 && visitBudget > 0) {
            Shapes.Part part = j.current();
            Box b = part.box();
            if (place(j, part, j.x, j.y, j.z)) {
                changedBudget--;
            }
            visitBudget--;
            j.visited++;
            if (++j.y > b.maxY()) {
                j.y = b.minY();
                if (++j.z > b.maxZ()) {
                    j.z = b.minZ();
                    if (++j.x > b.maxX()) {
                        nextPart(j);
                        continue;
                    }
                }
                if (System.nanoTime() > deadline) {
                    break;
                }
            }
        }
        if (job == j && System.currentTimeMillis() - j.lastProgress >= config.generateProgressSeconds * 1000L) {
            j.lastProgress = System.currentTimeMillis();
            Audience a = audience(j);
            String key = j.player == null ? "zones.generate.progress-console" : "zones.generate.progress";
            var placeholders = new net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[]{
                    Msg.p("percent", percent(j)), Msg.p("part", j.part < j.parts.size() ? j.current().label() : "-"),
                    Msg.p("done", j.part), Msg.p("total", j.parts.size()), Msg.p("changed", j.changed)};
            if (j.player == null) {
                msg.send(a, key, placeholders);
            } else {
                msg.actionBar(a, key, placeholders);
            }
        }
    }

    /** Puts the shape's block at one position if it differs. Returns true if the world changed. */
    private boolean place(Job j, Shapes.Part part, int x, int y, int z) {
        if (j.biome != null && (x & 3) == 0 && (y & 3) == 0 && (z & 3) == 0 && j.world.getBiome(x, y, z) != j.biome) {
            j.world.setBiome(x, y, z, j.biome);
        }
        Piece piece = part.shape().at(x, y, z);
        if (piece == null) {
            return false;
        }
        BlockData target = j.blocks.get(piece);
        Block block = j.world.getBlockAt(x, y, z);
        Material current = block.getType();
        if (current == target.getMaterial()) {
            if (current.isAir() || block.getBlockData().equals(target)) {
                return false;
            }
        } else if (current.isAir() && target.getMaterial().isAir()) {
            return false;
        }
        block.setBlockData(target, false);
        j.changed++;
        j.partChanged++;
        return true;
    }

    private void finish(Job j) {
        task.cancel();
        task = Scheduler.Task.NONE;
        job = null;
        long seconds = Math.max(1, (System.currentTimeMillis() - j.started) / 1000);
        core.audit().log(AuditCategory.WORLD, "depths_generate", j.actor, j.target, null, j.changed, null,
                Map.of("parts", j.parts.size(), "seconds", seconds, "world", j.world.getName()));
        msg.send(audience(j), "zones.generate.done", Msg.p("parts", j.parts.size()), Msg.p("changed", j.changed),
                Msg.p("seconds", seconds));
        if (j.player != null) {
            core.plugin().getLogger().info("Depths: placeholder build '" + j.target + "' by " + j.actor.name() + " done: "
                    + j.parts.size() + " parts, " + j.changed + " blocks changed in " + seconds + " s");
        }
    }
}
