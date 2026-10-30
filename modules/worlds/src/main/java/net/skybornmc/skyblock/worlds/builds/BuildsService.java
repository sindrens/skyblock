package net.skybornmc.skyblock.worlds.builds;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.builds.BuildBox;
import net.skybornmc.skyblock.common.builds.BuildPoints;
import net.skybornmc.skyblock.common.builds.BuildRegion;
import net.skybornmc.skyblock.common.builds.DepthsLayout;
import net.skybornmc.skyblock.common.builds.DepthsLayoutHook;
import net.skybornmc.skyblock.common.config.ConfigEdits;
import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.SettingInfo;
import net.skybornmc.skyblock.common.db.MainThread;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import net.skybornmc.skyblock.common.util.Scheduler;
import net.skybornmc.skyblock.worlds.WorldSettings;
import net.skybornmc.skyblock.worlds.WorldsConfig;
import net.skybornmc.skyblock.worlds.WorldsModule;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@code /skyblock builds}: the catalog of finished builds, pasting them with FastAsyncWorldEdit at their metadata
 * origin (preview + confirm, chunks loaded first, progress, timing, audit), {@code paste-all} in a safe order, and
 * {@code apply}: the build's metadata wired into the server (world spawn, Depths zones, island starter schematic, the
 * {@link BuildPoints} store). File work runs off the main thread; world and config changes on it. One paste at a time.
 */
public final class BuildsService {

    /** Island schematic setting changed by applying the starter island build. */
    static final String ISLANDS_FILE = "islands.yml";
    static final String ISLANDS_SCHEMATIC = "creation.schematic";

    private record Pending(String action, String id, long expiresAt) {
    }

    private final Core core;
    private final WorldsModule worlds;
    private final WorldsConfig config;
    private final ModuleMessages msg;
    private final Logger log;
    private final Executor main;
    private final Executor async;
    private final BuildPointsStore points = new BuildPointsStore();
    private final BuildState state = new BuildState();
    private final Map<String, Pending> pending = new ConcurrentHashMap<>();
    private volatile BuildCatalog catalog;
    private volatile String running;

    public BuildsService(Core core, WorldsModule worlds, WorldsConfig config) {
        this.core = core;
        this.worlds = worlds;
        this.config = config;
        this.msg = core.messages("worlds");
        this.log = core.plugin().getLogger();
        this.main = new MainThread(core.plugin());
        this.async = task -> Bukkit.getScheduler().runTaskAsynchronously(core.plugin(), task);
        this.catalog = BuildCatalog.empty(folder());
    }

    /** Loads the stores, publishes {@link BuildPoints} and scans the catalog in the background. */
    public void enable() {
        points.load(pointsFile(), log::warning);
        state.load(stateFile());
        core.services().register("worlds", BuildPoints.class, points);
        refresh(true);
    }

    public void reload() {
        points.load(pointsFile(), log::warning);
        state.load(stateFile());
        pending.clear();
        refresh(true);
    }

    public BuildPoints points() {
        return points;
    }

    /** The store behind {@link #points()} (portals watch it for applies). */
    public BuildPointsStore pointsStore() {
        return points;
    }

    // ------------------------------------------------------------------ files

    /** {@code builds.folder}: relative to the server folder (two levels above the plugin folder) or absolute. */
    public File folder() {
        File f = new File(config.buildsFolder);
        if (f.isAbsolute()) {
            return f;
        }
        File root = core.plugin().getDataFolder().getAbsoluteFile().getParentFile().getParentFile();
        return new File(root, config.buildsFolder);
    }

    private File pointsFile() {
        return new File(core.plugin().getDataFolder(), config.pointsFile);
    }

    private File stateFile() {
        return new File(core.plugin().getDataFolder(), config.stateFile);
    }

    // ------------------------------------------------------------------ catalog

    /** Ids for tab completion: every build ({@code info}). */
    public List<String> ids() {
        return new ArrayList<>(catalog.builds().keySet());
    }

    /** Ids that can be pasted: valid and not a template world. */
    public List<String> pasteIds() {
        return catalog.builds().values().stream().filter(e -> e.ok() && !template(e)).map(BuildEntry::id).toList();
    }

    /** Ids that can be applied: valid. */
    public List<String> applyIds() {
        return catalog.validIds();
    }

    private boolean template(BuildEntry e) {
        return e.meta().world() != null && config.templateWorlds.stream().anyMatch(w -> w.equalsIgnoreCase(e.meta().world()));
    }

    @SuppressWarnings("deprecation")
    private BuildValidator.Context context() {
        Map<String, Double> borders = new HashMap<>();
        for (WorldSettings s : worlds.worlds()) {
            borders.put(s.name(), s.border() / 2.0);
        }
        Set<String> names = Set.copyOf(borders.keySet());
        int dataVersion;
        try {
            dataVersion = Bukkit.getUnsafe().getDataVersion();
        } catch (RuntimeException | LinkageError e) {
            dataVersion = 0;
        }
        return new BuildValidator.Context(names::contains, dataVersion, w -> borders.getOrDefault(w, 0.0) > 0,
                w -> borders.getOrDefault(w, 0.0));
    }

    /** Re-scans the folder off the main thread; completes on the main thread. */
    public CompletableFuture<BuildCatalog> refresh(boolean logProblems) {
        BuildValidator.Context ctx = context();
        File folder = folder();
        return CompletableFuture.supplyAsync(() -> BuildCatalog.scan(folder, ctx), async).thenApplyAsync(c -> {
            catalog = c;
            if (logProblems) {
                c.problems().forEach(p -> log.warning("Builds: " + p));
                for (BuildEntry e : c.builds().values()) {
                    for (BuildProblem p : e.problems()) {
                        log.log(p.error() ? Level.WARNING : Level.INFO, "Builds: " + e.id() + ": " + (p.error() ? "ERROR " : "") + p.text());
                    }
                }
                log.info("Builds: " + c.builds().size() + " build(s) in " + folder.getPath() + ", " + c.validIds().size() + " valid"
                        + (c.layout() == null ? ", no depths_layout.yml" : ", Depths layout with " + c.layout().zones().size() + " zones"));
            }
            return c;
        }, main).exceptionally(error -> {
            log.log(Level.WARNING, "Builds: scanning " + folder.getPath() + " failed", error);
            return catalog;
        });
    }

    // ------------------------------------------------------------------ list / info

    public void list(CommandSender sender) {
        refresh(false).thenAccept(c -> {
            if (!c.problems().isEmpty()) {
                c.problems().forEach(p -> msg.send(sender, "builds.folder-problem", Msg.p("problem", p)));
            }
            if (c.builds().isEmpty()) {
                msg.send(sender, "builds.none", Msg.p("folder", c.folder().getPath()));
                return;
            }
            msg.send(sender, "builds.header", Msg.p("folder", c.folder().getPath()), Msg.number("count", c.builds().size()),
                    Msg.number("valid", c.validIds().size()));
            for (BuildEntry e : c.builds().values()) {
                String key = !e.ok() ? "builds.line-error" : template(e) ? "builds.line-template" : "builds.line-ok";
                msg.send(sender, key, Msg.p("id", e.id()), Msg.p("world", String.valueOf(e.meta().world())),
                        Msg.p("blocks", e.info() == null ? "?" : Format.number(e.info().nonAir())), Msg.number("errors", e.errors()),
                        Msg.number("warnings", e.warnings()), Msg.c("state", stateTag(sender, e.id())));
            }
            msg.send(sender, c.layout() == null ? "builds.layout-missing" : "builds.layout-ok",
                    Msg.number("zones", c.layout() == null ? 0 : c.layout().zones().size()));
        });
    }

    private Component stateTag(CommandSender sender, String id) {
        if (state.applied(id).isPresent()) {
            return msg.component(sender, "builds.state.applied");
        }
        if (state.pasted(id).isPresent()) {
            return msg.component(sender, "builds.state.pasted");
        }
        return msg.component(sender, "builds.state.new");
    }

    public void info(CommandSender sender, String id) {
        refresh(false).thenAccept(c -> {
            BuildEntry e = c.builds().get(id);
            if (e == null) {
                unknown(sender, id, c);
                return;
            }
            BuildMeta m = e.meta();
            msg.send(sender, "builds.info.header", Msg.p("id", id));
            msg.send(sender, "builds.info.schematic", Msg.p("file", String.valueOf(m.schematic())),
                    Msg.p("version", e.info() == null ? "?" : String.valueOf(e.info().version())),
                    Msg.p("data", e.info() == null ? "?" : String.valueOf(e.info().dataVersion())));
            msg.send(sender, "builds.info.world", Msg.p("world", String.valueOf(m.world())),
                    Msg.p("origin", m.origin() == null ? "?" : m.originX() + ", " + m.originY() + ", " + m.originZ()));
            BuildBox area = e.pasteBox();
            msg.send(sender, "builds.info.area", Msg.p("area", area == null ? "?" : area.toString()),
                    Msg.p("bounds", m.bounds() == null ? "?" : m.bounds().toString()),
                    Msg.number("chunks", area == null ? 0 : area.chunkCount()));
            msg.send(sender, "builds.info.blocks", Msg.p("blocks", e.info() == null ? "?" : Format.number(e.info().nonAir())),
                    Msg.p("write", e.blocksToPaste() < 0 ? "?" : Format.number(e.blocksToPaste())),
                    Msg.p("air", yesNo(sender, m.pasteAir())), Msg.p("biomes", yesNo(sender, m.pasteBiomes())));
            if (m.playerSpawn() != null) {
                BuildMeta.Spot s = m.playerSpawn();
                msg.send(sender, "builds.info.spawn", Msg.p("position", pos(s.feetX(), s.y(), s.feetZ())), Msg.p("yaw", num(s.yaw())));
            }
            Map<String, Integer> counts = new LinkedHashMap<>();
            m.points().forEach(p -> counts.merge(p.category(), 1, Integer::sum));
            if (!counts.isEmpty()) {
                msg.send(sender, "builds.info.points", Msg.p("list", join(counts)));
            }
            Map<String, Integer> regions = new LinkedHashMap<>();
            m.regions().forEach(r -> regions.merge(r.category(), 1, Integer::sum));
            if (!regions.isEmpty()) {
                msg.send(sender, "builds.info.regions", Msg.p("list", join(regions)));
            }
            if (template(e)) {
                msg.send(sender, "builds.info.template");
            }
            if (c.layout() != null && c.layout().isGate(id)) {
                msg.send(sender, "builds.info.depths-gate");
            } else if (c.layout() != null && c.layout().zoneOf(id) != null) {
                msg.send(sender, "builds.info.depths-zone", Msg.p("zone", c.layout().zoneOf(id).id()));
            }
            state.pasted(id).ifPresent(mark -> msg.send(sender, "builds.info.pasted", Msg.p("at", mark.at()), Msg.p("by", mark.by()),
                    Msg.p("details", mark.details())));
            state.applied(id).ifPresent(mark -> msg.send(sender, "builds.info.applied", Msg.p("at", mark.at()), Msg.p("by", mark.by())));
            if (e.problems().isEmpty()) {
                msg.send(sender, "builds.info.no-problems");
            }
            for (BuildProblem p : e.problems()) {
                msg.send(sender, p.error() ? "builds.info.error" : "builds.info.warning", Msg.p("problem", p.text()));
            }
        });
    }

    private String yesNo(CommandSender sender, boolean value) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(msg.component(sender, value ? "builds.yes" : "builds.no"));
    }

    private static String join(Map<String, Integer> counts) {
        StringBuilder sb = new StringBuilder();
        counts.forEach((k, v) -> sb.append(sb.isEmpty() ? "" : ", ").append(k).append(' ').append(v));
        return sb.toString();
    }

    private void unknown(CommandSender sender, String id, BuildCatalog c) {
        msg.send(sender, "builds.unknown", Msg.p("id", id), Msg.p("list", String.join(", ", c.builds().keySet())));
    }

    // ------------------------------------------------------------------ paste

    private static String key(CommandSender sender) {
        return sender instanceof Player p ? p.getUniqueId().toString() : "console:" + sender.getName();
    }

    /** Checks shared by paste and paste-all; sends the reason and returns false when pasting can't start. */
    private boolean canPaste(CommandSender sender) {
        if (running != null) {
            msg.send(sender, "builds.busy", Msg.p("id", running));
            return false;
        }
        if (!FaweBuildPaste.available()) {
            msg.send(sender, "builds.no-fawe");
            return false;
        }
        return true;
    }

    /** Problem that stops pasting this build, as a message key, or {@code null} if it may be pasted. */
    private String refusal(BuildEntry e) {
        if (!e.ok()) {
            return "builds.has-errors";
        }
        if (template(e)) {
            return "builds.template";
        }
        if (Bukkit.getWorld(e.meta().world()) == null) {
            return "builds.no-world";
        }
        return null;
    }

    public void paste(CommandSender sender, String id, boolean confirm) {
        refresh(false).thenAccept(c -> {
            BuildEntry e = c.builds().get(id);
            if (e == null) {
                unknown(sender, id, c);
                return;
            }
            String refusal = refusal(e);
            if (refusal != null) {
                msg.send(sender, refusal, Msg.p("id", id), Msg.p("world", String.valueOf(e.meta().world())),
                        Msg.number("errors", e.errors()));
                return;
            }
            if (!canPaste(sender)) {
                return;
            }
            String k = key(sender);
            if (!confirm) {
                pending.put(k, new Pending("paste", id, System.currentTimeMillis() + config.confirmSeconds * 1000L));
                BuildBox area = e.pasteBox();
                msg.send(sender, "builds.paste.preview", Msg.p("id", id), Msg.p("world", e.meta().world()),
                        Msg.p("origin", e.meta().originX() + ", " + e.meta().originY() + ", " + e.meta().originZ()),
                        Msg.p("area", area.toString()), Msg.p("blocks", Format.number(e.blocksToPaste())),
                        Msg.p("air", yesNo(sender, e.meta().pasteAir())), Msg.p("biomes", yesNo(sender, e.meta().pasteBiomes())),
                        Msg.number("chunks", area.chunkCount()), Msg.number("warnings", e.warnings()));
                msg.send(sender, "builds.paste.backup", Msg.p("world", e.meta().world()));
                sendConfirm(sender, "/skyblock builds paste " + id + " confirm");
                return;
            }
            if (!takePending(sender, k, "paste", id)) {
                return;
            }
            runPaste(sender, e);
        });
    }

    private void sendConfirm(CommandSender sender, String command) {
        Component button = msg.component(sender, "builds.confirm.button")
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(msg.component(sender, "builds.confirm.hover", Msg.p("command", command))));
        msg.send(sender, "builds.confirm.line", Msg.c("button", button), Msg.p("command", command),
                Msg.number("seconds", config.confirmSeconds));
    }

    private boolean takePending(CommandSender sender, String k, String action, String id) {
        Pending p = pending.remove(k);
        if (p == null || !p.action().equals(action) || !p.id().equals(id)) {
            msg.send(sender, "builds.confirm.missing", Msg.p("command", "/skyblock builds " + action + (id.isEmpty() ? "" : " " + id)));
            return false;
        }
        if (p.expiresAt() < System.currentTimeMillis()) {
            msg.send(sender, "builds.confirm.expired", Msg.p("command", "/skyblock builds " + action + (id.isEmpty() ? "" : " " + id)));
            return false;
        }
        return true;
    }

    /** Sends to the sender if still online, otherwise to the console. */
    private void tell(CommandSender sender, String key, net.kyori.adventure.text.minimessage.tag.resolver.TagResolver... placeholders) {
        CommandSender to = sender instanceof Player p && !p.isOnline() ? Bukkit.getConsoleSender() : sender;
        msg.send(to, key, placeholders);
    }

    /** Pastes one validated build; completes (main thread) with true on success. */
    CompletableFuture<Boolean> runPaste(CommandSender sender, BuildEntry e) {
        World world = Bukkit.getWorld(e.meta().world());
        if (world == null || !world.getName().equalsIgnoreCase(e.meta().world())) {
            tell(sender, "builds.no-world", Msg.p("id", e.id()), Msg.p("world", String.valueOf(e.meta().world())));
            return CompletableFuture.completedFuture(false);
        }
        if (running != null) {
            tell(sender, "builds.busy", Msg.p("id", running));
            return CompletableFuture.completedFuture(false);
        }
        File schematic = e.schematic();
        if (schematic == null || !schematic.isFile()) {
            tell(sender, "builds.paste.failed", Msg.p("id", e.id()), Msg.p("reason", "schematic file missing"));
            return CompletableFuture.completedFuture(false);
        }
        running = e.id();
        BuildBox area = e.pasteBox();
        long started = System.nanoTime();
        log.info("Builds: pasting " + e.id() + " in " + world.getName() + " at " + e.meta().originX() + "," + e.meta().originY() + ","
                + e.meta().originZ() + " (" + area + ", " + e.blocksToPaste() + " blocks, air " + e.meta().pasteAir() + ", biomes "
                + e.meta().pasteBiomes() + ") by " + sender.getName());
        List<Chunk> held = Collections.synchronizedList(new ArrayList<>());
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        Scheduler.Task[] progress = new Scheduler.Task[1];
        loadChunks(sender, e.id(), world, area, held)
                .thenComposeAsync(ignored -> {
                    tell(sender, "builds.paste.pasting", Msg.p("id", e.id()), Msg.number("chunks", held.size()));
                    long pasteStart = System.nanoTime();
                    progress[0] = core.scheduler().repeat("builds-progress", config.progressSeconds * 20L, config.progressSeconds * 20L,
                            () -> tell(sender, "builds.paste.progress", Msg.p("id", e.id()),
                                    Msg.p("time", Format.duration(Duration.ofNanos(System.nanoTime() - pasteStart)))));
                    return CompletableFuture.supplyAsync(() -> {
                        try {
                            return FaweBuildPaste.paste(schematic, world, e.meta().originX(), e.meta().originY(), e.meta().originZ(),
                                    e.meta().pasteAir(), e.meta().pasteBiomes());
                        } catch (IOException ex) {
                            throw new IllegalStateException(ex.getMessage(), ex);
                        }
                    }, async).orTimeout(config.pasteTimeoutMinutes, TimeUnit.MINUTES);
                }, main)
                .whenCompleteAsync((changed, error) -> {
                    if (progress[0] != null) {
                        progress[0].cancel();
                    }
                    synchronized (held) {
                        held.forEach(chunk -> chunk.removePluginChunkTicket(core.plugin()));
                        held.clear();
                    }
                    running = null;
                    Duration took = Duration.ofNanos(System.nanoTime() - started);
                    Map<String, Object> details = new LinkedHashMap<>();
                    details.put("world", world.getName());
                    details.put("origin", e.meta().originX() + "," + e.meta().originY() + "," + e.meta().originZ());
                    details.put("area", area.toString());
                    details.put("paste_air", e.meta().pasteAir());
                    details.put("paste_biomes", e.meta().pasteBiomes());
                    details.put("seconds", took.toSeconds());
                    if (error != null) {
                        Throwable cause = error.getCause() != null ? error.getCause() : error;
                        String reason = cause instanceof java.util.concurrent.TimeoutException
                                ? "timed out after " + config.pasteTimeoutMinutes + " min" : String.valueOf(cause.getMessage());
                        log.log(Level.SEVERE, "Builds: pasting " + e.id() + " failed: " + reason, cause);
                        tell(sender, "builds.paste.failed", Msg.p("id", e.id()), Msg.p("reason", reason));
                        details.put("error", reason);
                        core.audit().log(AuditCategory.WORLD, "build_paste_failed", Actor.of(sender), e.id(), null, null, details);
                        result.complete(false);
                        return;
                    }
                    details.put("changed", changed);
                    log.info("Builds: pasted " + e.id() + " in " + took.toMillis() + " ms (" + changed + " blocks changed)");
                    tell(sender, "builds.paste.done", Msg.p("id", e.id()), Msg.p("time", Format.duration(took)),
                            Msg.p("blocks", Format.number(changed == null ? 0 : changed)));
                    core.audit().log(AuditCategory.WORLD, "build_paste", Actor.of(sender), e.id(), changed, null, details);
                    state.markPasted(e.id(), new BuildState.Mark(now(), sender.getName(), changed + " blocks, " + took.toSeconds() + " s"));
                    saveState();
                    result.complete(true);
                }, main);
        return result;
    }

    /** Loads every chunk of the area a few per tick and holds it with a plugin ticket. Completes on the main thread. */
    private CompletableFuture<Void> loadChunks(CommandSender sender, String id, World world, BuildBox area, List<Chunk> held) {
        List<int[]> chunks = new ArrayList<>();
        for (int cx = area.minChunkX(); cx <= area.maxChunkX(); cx++) {
            for (int cz = area.minChunkZ(); cz <= area.maxChunkZ(); cz++) {
                chunks.add(new int[]{cx, cz});
            }
        }
        CompletableFuture<Void> done = new CompletableFuture<>();
        if (chunks.isEmpty()) {
            done.complete(null);
            return done;
        }
        AtomicInteger next = new AtomicInteger();
        AtomicInteger loaded = new AtomicInteger();
        long[] lastReport = {System.currentTimeMillis()};
        tell(sender, "builds.paste.loading", Msg.p("id", id), Msg.number("chunks", chunks.size()));
        Scheduler.Task[] task = new Scheduler.Task[1];
        task[0] = core.scheduler().repeat("builds-chunks", 0, 1, () -> {
            if (done.isDone()) {
                task[0].cancel();
                return;
            }
            for (int i = 0; i < config.chunksPerTick && next.get() < chunks.size(); i++) {
                int[] c = chunks.get(next.getAndIncrement());
                world.getChunkAtAsync(c[0], c[1], true).whenComplete((chunk, error) -> {
                    if (error != null || chunk == null) {
                        done.completeExceptionally(error != null ? error : new IllegalStateException("chunk " + c[0] + "," + c[1] + " failed to load"));
                        return;
                    }
                    chunk.addPluginChunkTicket(core.plugin());
                    held.add(chunk);
                    if (loaded.incrementAndGet() == chunks.size()) {
                        done.complete(null);
                    }
                });
            }
            if (next.get() >= chunks.size()) {
                task[0].cancel();
            }
            if (System.currentTimeMillis() - lastReport[0] >= config.progressSeconds * 1000L) {
                lastReport[0] = System.currentTimeMillis();
                tell(sender, "builds.paste.loading-progress", Msg.p("id", id), Msg.number("done", loaded.get()),
                        Msg.number("total", chunks.size()));
            }
        });
        return done.thenApplyAsync(v -> v, main);
    }

    // ------------------------------------------------------------------ paste-all

    public void pasteAll(CommandSender sender, boolean confirm) {
        refresh(false).thenAccept(c -> {
            List<String> order = PasteOrder.sort(new ArrayList<>(c.builds().keySet()), config.pasteOrder);
            List<BuildEntry> todo = new ArrayList<>();
            List<String> skipped = new ArrayList<>();
            for (String id : order) {
                BuildEntry e = c.builds().get(id);
                if (template(e)) {
                    continue;
                }
                if (refusal(e) != null) {
                    skipped.add(id);
                } else {
                    todo.add(e);
                }
            }
            if (todo.isEmpty()) {
                msg.send(sender, "builds.paste-all.nothing");
                return;
            }
            if (!canPaste(sender)) {
                return;
            }
            String k = key(sender);
            if (!confirm) {
                pending.put(k, new Pending("paste-all", "", System.currentTimeMillis() + config.confirmSeconds * 1000L));
                msg.send(sender, "builds.paste-all.preview", Msg.number("count", todo.size()));
                long total = 0;
                int step = 0;
                for (BuildEntry e : todo) {
                    step++;
                    total += Math.max(0, e.blocksToPaste());
                    msg.send(sender, "builds.paste-all.line", Msg.number("step", step), Msg.p("id", e.id()), Msg.p("world", e.meta().world()),
                            Msg.p("area", String.valueOf(e.pasteBox())), Msg.p("blocks", Format.number(e.blocksToPaste())));
                }
                if (!skipped.isEmpty()) {
                    msg.send(sender, "builds.paste-all.skipped", Msg.p("list", String.join(", ", skipped)));
                }
                msg.send(sender, "builds.paste-all.total", Msg.p("blocks", Format.number(total)));
                msg.send(sender, "builds.paste.backup", Msg.p("world", String.join(", ", todo.stream().map(e -> e.meta().world()).distinct().toList())));
                sendConfirm(sender, "/skyblock builds paste-all confirm");
                return;
            }
            if (!takePending(sender, k, "paste-all", "")) {
                return;
            }
            core.audit().log(AuditCategory.WORLD, "build_paste_all", Actor.of(sender), "builds", (long) todo.size(), null,
                    Map.of("order", todo.stream().map(BuildEntry::id).toList(), "skipped", skipped));
            long started = System.nanoTime();
            pasteChain(sender, todo, 0, started);
        });
    }

    private void pasteChain(CommandSender sender, List<BuildEntry> todo, int index, long started) {
        if (index >= todo.size()) {
            tell(sender, "builds.paste-all.done", Msg.number("count", todo.size()),
                    Msg.p("time", Format.duration(Duration.ofNanos(System.nanoTime() - started))));
            return;
        }
        BuildEntry e = todo.get(index);
        tell(sender, "builds.paste-all.step", Msg.number("step", index + 1), Msg.number("count", todo.size()), Msg.p("id", e.id()));
        runPaste(sender, e).thenAccept(ok -> {
            if (ok) {
                pasteChain(sender, todo, index + 1, started);
            } else {
                tell(sender, "builds.paste-all.stopped", Msg.p("id", e.id()), Msg.number("done", index), Msg.number("count", todo.size()));
            }
        });
    }

    // ------------------------------------------------------------------ apply

    public void apply(CommandSender sender, String id) {
        refresh(false).thenAccept(c -> {
            BuildEntry e = c.builds().get(id);
            if (e == null) {
                unknown(sender, id, c);
                return;
            }
            if (!e.ok()) {
                msg.send(sender, "builds.has-errors", Msg.p("id", id), Msg.number("errors", e.errors()));
                return;
            }
            BuildMeta meta = e.meta();
            msg.send(sender, "builds.apply.header", Msg.p("id", id));
            if (state.pasted(id).isEmpty() && !template(e)) {
                msg.send(sender, "builds.apply.not-pasted", Msg.p("id", id));
            }
            points.put(BuildPointsStore.fromMeta(meta, now(), sender.getName()));
            boolean island = template(e) && id.startsWith("islands_");
            File islandFolder = new File(core.plugin().getDataFolder(), config.islandsSchematicsFolder);
            CompletableFuture.supplyAsync(() -> {
                List<String> errors = new ArrayList<>();
                try {
                    points.save(pointsFile());
                } catch (IOException ex) {
                    errors.add("points:" + ex.getMessage());
                }
                if (island) {
                    try {
                        copyIslandFiles(e, islandFolder);
                    } catch (IOException ex) {
                        errors.add("island:" + ex.getMessage());
                    }
                }
                return errors;
            }, async).thenAcceptAsync(errors -> finishApply(sender, c, e, island, islandFolder, errors), main);
        });
    }

    static void copyIslandFiles(BuildEntry e, File folder) throws IOException {
        Files.createDirectories(folder.toPath());
        Files.copy(e.schematic().toPath(), new File(folder, e.id() + ".schem").toPath(), StandardCopyOption.REPLACE_EXISTING);
        Files.copy(e.yml().toPath(), new File(folder, e.id() + ".yml").toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    private void finishApply(CommandSender sender, BuildCatalog c, BuildEntry e, boolean island, File islandFolder, List<String> ioErrors) {
        BuildMeta meta = e.meta();
        String id = e.id();
        Map<String, Object> details = new LinkedHashMap<>();
        // 1. spots and regions -> build-points.yml
        boolean pointsSaved = ioErrors.stream().noneMatch(s -> s.startsWith("points:"));
        msg.send(sender, pointsSaved ? "builds.apply.points" : "builds.apply.points-failed", Msg.number("points", meta.points().size()),
                Msg.number("regions", meta.regions().size()), Msg.p("file", config.pointsFile));
        details.put("points", meta.points().size());
        details.put("regions", meta.regions().size());
        // 2. player_spawn -> the world's spawn (worlds.yml; /spawn and first join use the spawn world's spawn)
        if (meta.playerSpawn() != null && !template(e)) {
            BuildMeta.Spot s = meta.playerSpawn();
            boolean saved = worlds.setSpawn(meta.world(), s.feetX(), s.y(), s.feetZ(), s.yaw(), s.pitch());
            msg.send(sender, saved ? "builds.apply.spawn" : "builds.apply.spawn-failed", Msg.p("world", meta.world()),
                    Msg.p("position", pos(s.feetX(), s.y(), s.feetZ())), Msg.p("yaw", num(s.yaw())));
            details.put("spawn", meta.world() + " " + pos(s.feetX(), s.y(), s.feetZ()) + " yaw " + num(s.yaw()));
        }
        // 3. protected regions: spawn protection covers whole worlds; elsewhere they are stored for later
        List<BuildRegion> protectedRegions = meta.regions("regions").stream().filter(r -> r.kind().equalsIgnoreCase("protected")).toList();
        if (!protectedRegions.isEmpty()) {
            boolean covered = protectedWorlds().contains(meta.world());
            msg.send(sender, covered ? "builds.apply.regions-covered" : "builds.apply.regions-stored",
                    Msg.number("count", protectedRegions.size()), Msg.p("world", meta.world()));
        }
        if (!meta.regions("portals").isEmpty()) {
            net.skybornmc.skyblock.worlds.portals.PortalService portals = worlds.portals();
            if (portals != null && config.portalsEnabled) {
                msg.send(sender, "builds.apply.portals", Msg.number("count", meta.regions("portals").size()),
                        Msg.p("list", portals.describe(meta.regions("portals"))));
            } else {
                msg.send(sender, "builds.apply.portals-stored", Msg.number("count", meta.regions("portals").size()));
            }
        }
        Map<String, Integer> stored = new LinkedHashMap<>();
        meta.points().forEach(p -> stored.merge(p.category(), 1, Integer::sum));
        if (!stored.isEmpty()) {
            msg.send(sender, "builds.apply.stored", Msg.p("list", join(stored)));
        }
        // 4. Depths: zones.yml follows the layout and this build
        if (c.layout() != null && DepthsWiring.isDepthsBuild(c.layout(), id)) {
            applyDepths(sender, c.layout(), meta, details);
        } else if (c.layout() == null && "depths".equalsIgnoreCase(meta.world())) {
            msg.send(sender, "builds.apply.depths-no-layout", Msg.p("file", LayoutFile.FILE));
        }
        // 5. island starter -> the /is create schematic
        if (island) {
            applyIsland(sender, e, islandFolder, ioErrors, details);
        }
        state.markApplied(id, new BuildState.Mark(now(), sender.getName(), ""));
        saveState();
        core.audit().log(AuditCategory.WORLD, "build_apply", Actor.of(sender), id, null, null, details);
        log.info("Builds: applied " + id + " by " + sender.getName() + " " + details);
        msg.send(sender, "builds.apply.done", Msg.p("id", id));
    }

    private void applyDepths(CommandSender sender, LayoutFile layout, BuildMeta meta, Map<String, Object> details) {
        DepthsLayoutHook hook = core.services().optional(DepthsLayoutHook.class).orElse(null);
        if (hook == null) {
            msg.send(sender, "builds.apply.depths-missing");
            return;
        }
        DepthsLayout data = DepthsWiring.layout(layout, meta);
        DepthsLayoutHook.Result r;
        try {
            r = hook.merge(data);
        } catch (RuntimeException ex) {
            log.log(Level.SEVERE, "Builds: Depths merge failed", ex);
            msg.send(sender, "builds.apply.depths-failed", Msg.p("reason", String.valueOf(ex.getMessage())));
            return;
        }
        if (!r.ok()) {
            msg.send(sender, "builds.apply.depths-failed", Msg.p("reason", String.join("; ", r.problems())));
            return;
        }
        msg.send(sender, "builds.apply.depths", Msg.number("changes", r.changes().size()),
                Msg.p("backup", r.backup() == null ? "-" : r.backup()));
        r.changes().forEach(line -> msg.send(sender, "builds.apply.depths-change", Msg.p("change", line)));
        r.problems().forEach(line -> msg.send(sender, "builds.apply.depths-problem", Msg.p("problem", line)));
        details.put("depths_changes", r.changes().size());
        details.put("depths_backup", r.backup());
    }

    private void applyIsland(CommandSender sender, BuildEntry e, File folder, List<String> ioErrors, Map<String, Object> details) {
        String copyError = ioErrors.stream().filter(s -> s.startsWith("island:")).findFirst().orElse(null);
        if (copyError != null) {
            msg.send(sender, "builds.apply.island-failed", Msg.p("reason", copyError.substring("island:".length())));
            return;
        }
        ModuleConfig islands = core.configs().loaded().get(ISLANDS_FILE);
        if (islands == null) {
            msg.send(sender, "builds.apply.island-copied", Msg.p("folder", folder.getName()), Msg.p("name", e.id()));
            return;
        }
        ConfigEdits.Result r = ConfigEdits.apply(core, sender, islands, ISLANDS_SCHEMATIC, e.id());
        if (!r.ok()) {
            msg.send(sender, "builds.apply.island-failed", Msg.p("reason", String.valueOf(r.error())));
            return;
        }
        msg.send(sender, "builds.apply.island", Msg.p("name", e.id()), Msg.p("old", String.valueOf(r.oldValue())));
        details.put("island_schematic", e.id());
    }

    /** essentials.yml → protection.worlds (worlds protected as a whole), empty if essentials is not running. */
    private List<String> protectedWorlds() {
        ModuleConfig essentials = core.configs().loaded().get("essentials.yml");
        if (essentials == null) {
            return List.of();
        }
        for (SettingInfo info : core.configs().describe(essentials)) {
            if (info.path().equals("protection.worlds") && info.value() instanceof List<?> list) {
                return list.stream().map(o -> String.valueOf(o).toLowerCase(Locale.ROOT)).toList();
            }
        }
        return List.of();
    }

    // ------------------------------------------------------------------ helpers

    private void saveState() {
        String text = state.snapshot();
        File file = stateFile();
        async.execute(() -> {
            try {
                BuildState.save(file, text);
            } catch (IOException ex) {
                log.warning("Builds: could not save " + file.getName() + ": " + ex.getMessage());
            }
        });
    }

    private static String now() {
        return ZonedDateTime.now(Format.zone()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    static String pos(double x, double y, double z) {
        return num(x) + ", " + num(y) + ", " + num(z);
    }

    static String num(double v) {
        return v == Math.rint(v) ? Long.toString((long) v) : String.format(Locale.ROOT, "%.1f", v);
    }
}
