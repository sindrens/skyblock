package net.skybornmc.skyblock.crates;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.builds.BuildPoint;
import net.skybornmc.skyblock.common.builds.BuildPoints;
import net.skybornmc.skyblock.common.npc.HologramTexts;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.LatestLoad;
import net.skybornmc.skyblock.common.util.Scheduler;
import net.skybornmc.skyblock.common.util.SpawnQueue;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The crate yard (M10.3): the crate blocks on the pedestals (real vanilla blocks, so Bedrock sees them), a hologram
 * above each, the key board text, and the in-world show of an open at a pedestal (rarity particles, the reward floating
 * up with its name, a firework for top rewards). Everything is placed lazily when a pedestal's chunk is loaded (never
 * loads a chunk, PERF3) and the entities are never saved: they are removed when the chunk unloads and on disable.
 * Main thread. PERF10: the pedestals, block data and texts are planned off the main thread ({@link YardPlan}); the
 * main thread swaps the plan in ({@code crates:yard-apply}) and sets up the pedestals a few per tick
 * ({@code crates:yard-place}, {@code physical.place-per-tick} / {@code physical.place-budget-ms}).
 */
final class CrateYard implements Listener {

    /** Scoreboard tag on every entity of the crate yard (clean-up of left-overs). */
    static final String TAG = "phoenix_crates";
    /** Ticks between two build-up steps. */
    private static final int STEP_TICKS = 2;

    private final CratesModule m;
    private final NamespacedKey crateKey;
    private List<Pedestals.Pedestal> pedestals = List.of();
    /** PERF10: the applied plan (the lookups below are its maps). */
    private YardPlan plan = YardPlan.empty();
    private Map<String, Map<Long, List<Pedestals.Pedestal>>> byChunk = Map.of();
    private Map<String, Map<Long, Pedestals.Pedestal>> byBlock = Map.of();
    private final Map<String, TextDisplay> holograms = new HashMap<>();
    private final Map<String, Show> shows = new HashMap<>();
    private final Set<String> warned = new HashSet<>();
    private Map<String, String> blocks = Map.of();
    private List<Integer> offsets = List.of(0);
    /** PERF10: plans off the main thread; only the newest plan is applied. */
    private final LatestLoad<Planned> loader;
    /** PERF10: the loader's steps and the place task body, made once (nothing to link for the first time in a tick). */
    private final java.util.function.Supplier<Planned> planner = this::computePlan;
    private final java.util.function.Consumer<Planned> applier = this::apply;
    private final java.util.function.BiConsumer<Boolean, Throwable> failed = this::failed;
    private final Runnable drainBody = this::drain;

    /** A plan plus the task that writes its log lines (made on the async thread). */
    private record Planned(YardPlan plan, Runnable log) {
    }
    /** PERF10: pedestals waiting to be set up (crate block + hologram), a few per tick. */
    private final SpawnQueue<Pedestals.Pedestal> queue = new SpawnQueue<>();
    private Scheduler.Task placeTask;
    /** PERF10: the left-over walk runs in the first place tick of a plan, alone in that tick. */
    private boolean purgePending;

    /** The running show of one pedestal. */
    private final class Show {
        final Pedestals.Pedestal pedestal;
        final List<Entity> entities = new ArrayList<>();
        final List<Scheduler.Task> tasks = new ArrayList<>();
        boolean building;

        Show(Pedestals.Pedestal pedestal) {
            this.pedestal = pedestal;
        }

        void stop() {
            tasks.forEach(Scheduler.Task::cancel);
            tasks.clear();
            entities.forEach(Entity::remove);
            entities.clear();
            TextDisplay hologram = holograms.get(pedestal.crateId());
            if (hologram != null && hologram.isValid()) {
                hologram.setVisibleByDefault(true);
            }
        }
    }

    CrateYard(CratesModule module) {
        this.m = module;
        this.crateKey = new NamespacedKey(module.core.plugin(), "crate_yard");
        this.loader = new LatestLoad<>(r -> module.core.scheduler().async(r),
                r -> module.core.scheduler().main("crates:yard-apply", r));
    }

    // ================================================================== lifecycle

    /**
     * (Re)reads the pedestals and places crates and holograms in the loaded chunks (over the next ticks). PERF10: this
     * only starts the plan ({@link YardPlan}: settings, build points, block data and texts are read on an async thread).
     * The current pedestals stay until the plan is applied ({@code crates:yard-apply}), which does what the old one-tick
     * start did (remove the old entities, swap the lookups, log, key board) and queues the pedestals.
     */
    void start() {
        loader.load(planner, applier).whenComplete(failed);
    }

    private void failed(Boolean applied, Throwable error) {
        if (error != null) {
            m.core.plugin().getLogger().log(Level.WARNING, "Crates: could not set up the crate yard", error);
        }
    }

    /** Async thread: the plan (config fields, the service registry, messages and the catalog are safe to read here). */
    private Planned computePlan() {
        YardPlan.Inputs in = YardPlan.Inputs.of(m.settings, m.catalog());
        List<BuildPoint> points = m.core.services().optional(BuildPoints.class).map(b -> b.points(Pedestals.CATEGORY)).orElse(null);
        long generation = m.core.messages().generation();
        boolean cache = !m.core.messages().usesPlayerPlaceholders(CratesModule.ID);
        YardPlan plan = YardPlan.compute(in, points, Bukkit::createBlockData, new YardPlan.Texts() {
            @Override
            public Component hologram(CrateCatalog.Crate crate) {
                return hologramText(crate);
            }

            @Override
            public List<Component> keyBoard(CrateCatalog catalog) {
                return keyBoardLines(catalog);
            }
        }, generation, cache);
        List<String> problems = plan.problems();
        String summary = plan.summary();
        Logger log = m.core.plugin().getLogger();
        return new Planned(plan, () -> {
            problems.forEach(log::warning);
            log.info(summary);
        });
    }

    /**
     * Main thread: takes over a plan, removes the old entities and queues every pedestal. No lambda is created here (the
     * log task comes with the plan), so nothing is linked for the first time in this tick.
     */
    private void apply(Planned planned) {
        YardPlan next = planned.plan();
        clear();
        plan = next;
        blocks = next.blocks();
        offsets = next.offsets();
        pedestals = next.pedestals();
        byChunk = next.byChunk();
        byBlock = next.byBlock();
        m.core.scheduler().async(planned.log());
        purgePending = true;
        queue.addAll(next.pedestals());
        drainLater();
        long start = System.nanoTime();
        pushKeyBoard(next);
        m.core.scheduler().timer().record("crates:yard-keyboard", System.nanoTime() - start);
    }

    /** Removes every entity of the crate yard and forgets the pedestals; a plan still being built is dropped (disable). */
    void stop() {
        loader.cancel();
        clear();
    }

    /** Removes every entity of the crate yard and forgets the pedestals (the crate blocks stay: they are the build). */
    private void clear() {
        if (placeTask != null) {
            placeTask.cancel();
            placeTask = null;
        }
        queue.clear();
        purgePending = false;
        new ArrayList<>(shows.values()).forEach(Show::stop);
        shows.clear();
        holograms.values().forEach(Entity::remove);
        holograms.clear();
        plan = YardPlan.empty();
        byChunk = Map.of();
        byBlock = Map.of();
        pedestals = List.of();
        m.core.services().optional(HologramTexts.class).ifPresent(t -> t.clearAll(CratesModule.ID));
    }

    List<Pedestals.Pedestal> pedestals() {
        return pedestals;
    }

    /** The pedestal whose crate block (or a block {@code physical.y-offsets} above its point) is this block. */
    Optional<Pedestals.Pedestal> at(Block block) {
        Map<Long, Pedestals.Pedestal> world = byBlock.get(block.getWorld().getName());
        if (world == null) {
            return Optional.empty();
        }
        for (int dy : offsets) {
            Pedestals.Pedestal p = world.get(Pedestals.Pedestal.blockKey(block.getX(), block.getY() - dy, block.getZ()));
            if (p != null) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    // ================================================================== placing

    /** The pedestal's world if its chunk and its entities are loaded (never loads a chunk), else null. */
    private static World loaded(Pedestals.Pedestal p) {
        World w = Bukkit.getWorld(p.world());
        if (w == null || !w.isChunkLoaded(p.chunkX(), p.chunkZ())) {
            return null;
        }
        return w.getChunkAt(p.chunkX(), p.chunkZ()).isEntitiesLoaded() ? w : null;
    }

    /** Starts the per-tick place task (from the next tick) unless it runs or nothing waits. */
    private void drainLater() {
        if (placeTask == null && (purgePending || !queue.isEmpty())) {
            placeTask = m.core.scheduler().repeat("crates:yard-place", 1, 1, drainBody);
        }
    }

    /** One place tick: the left-over walk alone, else pedestals within the budget; stops when nothing is left. */
    private void drain() {
        if (purgePending) {
            purgePending = false;
            long start = System.nanoTime();
            purgeLeftovers();
            m.core.scheduler().timer().record("crates:yard-purge", System.nanoTime() - start);
        } else {
            queue.drain(m.settings.placePerTick, m.settings.placeBudgetMs * 1_000_000L, System::nanoTime, this::placeIfWanted);
        }
        if (!purgePending && queue.isEmpty() && placeTask != null) {
            placeTask.cancel();
            placeTask = null;
        }
    }

    /** Sets up a queued pedestal if it still belongs to the applied plan and its chunk is loaded; true when it did. */
    private boolean placeIfWanted(Pedestals.Pedestal p) {
        Map<Long, Pedestals.Pedestal> world = byBlock.get(p.world());
        return world != null && world.get(p.blockKey()) == p && place(p);
    }

    /** Puts the crate block and the hologram of a pedestal (only while its chunk is loaded); true when it did. */
    private boolean place(Pedestals.Pedestal p) {
        World w = loaded(p);
        if (w == null || !m.settings.physicalEnabled) {
            return false;
        }
        placeBlock(w, p);
        placeHologram(w, p);
        return true;
    }

    /** The plan's look of a crate while the plan was built from the catalog in use, else null (computed here then). */
    private YardPlan.Look look(String crateId) {
        return plan.inputs().catalog() == m.catalog() ? plan.looks().get(crateId) : null;
    }

    private void placeBlock(World w, Pedestals.Pedestal p) {
        Optional<CrateCatalog.Crate> crate = m.catalog().crate(p.crateId());
        if (crate.isEmpty()) {
            return;
        }
        YardPlan.Look look = look(p.crateId());
        String spec = look != null ? look.spec() : Pedestals.blockData(p.crateId(), blocks, crate.get().icon());
        BlockData data = look != null ? look.data() : null;
        boolean invalid = look != null && look.invalid();
        if (data == null && !invalid) {
            try {
                data = Bukkit.createBlockData(spec);
            } catch (IllegalArgumentException e) {
                invalid = true;
            }
        }
        if (invalid) {
            warnOnce("data:" + p.crateId(), "crates-settings.yml → physical.blocks: '" + spec + "' (crate " + p.crateId()
                    + ") is not a block; the crate block was not placed");
            return;
        }
        Block block = w.getBlockAt(p.x(), p.y(), p.z());
        if (block.getBlockData().matches(data)) {
            return;
        }
        String current = block.getType().getKey().getKey();
        if (block.getType() != data.getMaterial() && m.settings.physicalReplace.stream().noneMatch(r -> r.equalsIgnoreCase(current))) {
            warnOnce("block:" + p.crateId(), "Crates: the pedestal '" + p.pointId() + "' at " + p.x() + " " + p.y() + " " + p.z()
                    + " holds " + current + ", which is not in crates-settings.yml → physical.replace; the " + p.crateId()
                    + " crate block was not placed there");
            return;
        }
        block.setBlockData(data, false);
    }

    /** The hologram text above a crate (message parsing only, any thread). */
    private Component hologramText(CrateCatalog.Crate crate) {
        String key = crate.hidden() ? "hologram.coming-soon" : "hologram.lines";
        return m.msg.component(Audience.empty(), key, Msg.mm("crate", m.crateMini(crate)));
    }

    private void placeHologram(World w, Pedestals.Pedestal p) {
        TextDisplay old = holograms.remove(p.crateId());
        if (old != null) {
            old.remove();
        }
        if (!m.settings.hologramEnabled) {
            return;
        }
        Optional<CrateCatalog.Crate> crate = m.catalog().crate(p.crateId());
        if (crate.isEmpty()) {
            return;
        }
        Location at = new Location(w, p.x() + 0.5, p.y() + 1 + m.settings.hologramHeight, p.z() + 0.5);
        YardPlan.Look look = look(p.crateId());
        // PERF10: the plan's text while no messages were reloaded since it was built (and no player placeholders).
        Component planned = look != null && plan.cacheTexts() && plan.generation() == m.core.messages().generation()
                ? look.hologram() : null;
        Component text = planned != null ? planned : hologramText(crate.get());
        TextDisplay display = w.spawn(at, TextDisplay.class, d -> {
            d.text(text);
            d.setBillboard(Display.Billboard.CENTER);
            d.setViewRange((float) m.settings.hologramViewRange);
            d.setAlignment(TextDisplay.TextAlignment.CENTER);
            d.setShadowed(true);
            d.setDefaultBackground(false);
            d.setBackgroundColor(Color.fromARGB(Math.max(0, Math.min(255, m.settings.hologramBackgroundAlpha)), 0, 0, 0));
            tag(d, p);
        });
        holograms.put(p.crateId(), display);
        Show show = shows.get(p.crateId());
        if (show != null && !show.entities.isEmpty()) {
            display.setVisibleByDefault(false); // a reward floats there right now
        }
    }

    private void tag(Entity e, Pedestals.Pedestal p) {
        e.setPersistent(false);
        e.addScoreboardTag(TAG);
        e.getPersistentDataContainer().set(crateKey, PersistentDataType.STRING, p.crateId());
    }

    /** True for one of the entities this run placed. */
    private boolean owned(Entity e) {
        if (holograms.containsValue(e)) {
            return true;
        }
        for (Show s : shows.values()) {
            if (s.entities.contains(e)) {
                return true;
            }
        }
        return false;
    }

    private void purgeLeftovers() {
        Set<String> worlds = new HashSet<>();
        pedestals.forEach(p -> worlds.add(p.world()));
        for (String name : worlds) {
            World w = Bukkit.getWorld(name);
            if (w == null) {
                continue;
            }
            for (Entity e : w.getEntitiesByClasses(TextDisplay.class, ItemDisplay.class)) {
                if (e.getScoreboardTags().contains(TAG) && !owned(e)) {
                    e.remove();
                }
            }
        }
    }

    private void warnOnce(String key, String text) {
        if (warned.add(key)) {
            m.core.plugin().getLogger().warning(text);
        }
    }

    // ================================================================== chunk events

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity e : event.getEntities()) {
            if ((e instanceof TextDisplay || e instanceof ItemDisplay) && e.getScoreboardTags().contains(TAG) && !owned(e)) {
                e.remove();
            }
        }
        List<Pedestals.Pedestal> here = here(event.getWorld(), event.getChunk());
        if (!here.isEmpty()) {
            // Next tick, outside the chunk-load event, and only if the chunk is still there (PERF10: through the queue).
            here.forEach(queue::add);
            drainLater();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesUnload(EntitiesUnloadEvent event) {
        for (Pedestals.Pedestal p : here(event.getWorld(), event.getChunk())) {
            Show show = shows.remove(p.crateId());
            if (show != null) {
                show.stop();
            }
            TextDisplay hologram = holograms.remove(p.crateId());
            if (hologram != null) {
                hologram.remove();
            }
        }
    }

    private List<Pedestals.Pedestal> here(World world, Chunk chunk) {
        Map<Long, List<Pedestals.Pedestal>> chunks = byChunk.get(world.getName());
        if (chunks == null) {
            return List.of();
        }
        return chunks.getOrDefault(Pedestals.Pedestal.chunkKey(chunk.getX(), chunk.getZ()), List.of());
    }

    /** The crate blocks can't be broken (they come back on the next chunk load anyway). */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (m.settings.physicalEnabled) {
            Map<Long, Pedestals.Pedestal> world = byBlock.get(event.getBlock().getWorld().getName());
            Block b = event.getBlock();
            if (world != null && world.containsKey(Pedestals.Pedestal.blockKey(b.getX(), b.getY(), b.getZ()))) {
                event.setCancelled(true);
            }
        }
    }

    /** Our top-reward fireworks are only a show: they never hurt anyone. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFireworkDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Firework f && f.getScoreboardTags().contains(TAG)) {
            event.setCancelled(true);
        }
    }

    // ================================================================== key board

    /**
     * Writes the key board (how to get keys + the top-reward chance of every crate) through the npcs module. PERF10: the
     * id and lines come from the plan (built off the main thread); they are built here only when messages were reloaded
     * since the plan was made.
     */
    private void pushKeyBoard(YardPlan p) {
        Optional<HologramTexts> texts = m.core.services().optional(HologramTexts.class);
        if (texts.isEmpty()) {
            return;
        }
        if (p.keyBoardId() == null) {
            texts.get().clearAll(CratesModule.ID);
            return;
        }
        boolean current = p.cacheTexts() && p.generation() == m.core.messages().generation() && p.keyBoard() != null;
        texts.get().set(CratesModule.ID, p.keyBoardId(), current ? p.keyBoard() : keyBoardLines(m.catalog()));
    }

    /** The key board lines (message parsing only, any thread). */
    private List<Component> keyBoardLines(CrateCatalog catalog) {
        List<Component> lines = new ArrayList<>();
        Audience none = Audience.empty();
        lines.add(m.msg.component(none, "key-board.title"));
        for (String raw : rawList("key-board.how")) {
            lines.add(m.msg.component(none, "text", Msg.mm("text", raw)));
        }
        lines.add(m.msg.component(none, "key-board.odds-header"));
        for (CrateCatalog.Crate crate : catalog.visible()) {
            if (crate.hasTop()) {
                lines.add(m.msg.component(none, "key-board.odds-line", Msg.mm("crate", m.crateMini(crate)),
                        Msg.p("top", CrateMath.percent(crate.topPercent()))));
            }
        }
        lines.add(m.msg.component(none, "key-board.footer"));
        return lines;
    }

    /** The lines of a list message (lists are stored joined with {@code <newline>}). */
    private List<String> rawList(String key) {
        String raw = m.core.messages().raw(CratesModule.ID, key);
        return raw == null ? List.of() : List.of(raw.split("<newline>", -1));
    }

    // ================================================================== in-world show

    /**
     * The show of a committed single open at a pedestal: build-up (rarity particles and rising ticks, skipped with Fast
     * crate opening), then the reward floats up above the crate with its name for everyone nearby, a burst, and a
     * firework for a top reward. The result message comes at the reveal (also when the opener left meanwhile).
     */
    void play(Player player, OpenPlan.Plan plan, Pedestals.Pedestal pedestal, boolean fast) {
        World w = pedestal == null ? null : loaded(pedestal);
        Show running = pedestal == null ? null : shows.get(pedestal.crateId());
        if (w == null || (running != null && running.building)) {
            // Chunk gone, or someone else's build-up runs at this crate: the result at once (chat and title).
            reveal(player, player.getName(), plan, null, null);
            return;
        }
        if (running != null) {
            running.stop(); // a previous reward still floats: make room
        }
        Show show = new Show(pedestal);
        shows.put(pedestal.crateId(), show);
        Location center = new Location(w, pedestal.x() + 0.5, pedestal.y() + 1.0, pedestal.z() + 0.5);
        String name = player.getName();
        int build = fast ? 0 : m.settings.buildTicks;
        if (build <= 0) {
            reveal(player, name, plan, show, center);
            return;
        }
        m.show().started(player.getUniqueId());
        show.building = true;
        Color color = color(plan.crate().rarity());
        int steps = Math.max(1, build / STEP_TICKS);
        int[] step = {0};
        show.tasks.add(m.core.scheduler().repeat("crates:yard-build", 0, STEP_TICKS, () -> {
            if (shows.get(pedestal.crateId()) != show) {
                return;
            }
            buildStep(center, color, step[0], steps);
            if (++step[0] >= steps) {
                show.building = false;
                show.tasks.forEach(Scheduler.Task::cancel);
                show.tasks.clear();
                reveal(player, name, plan, show, center);
            }
        }));
    }

    /** One step of the build-up: a ring of rarity dust rising around the crate and a tick that gets higher. */
    private void buildStep(Location center, Color color, int step, int steps) {
        World w = center.getWorld();
        double progress = (double) step / steps;
        int count = m.settings.particlesPerStep;
        Particle.DustOptions dust = new Particle.DustOptions(color, 1.0f);
        for (int i = 0; i < count; i++) {
            double angle = (step * 0.6) + (Math.PI * 2 * i / Math.max(1, count));
            double radius = 0.75 - 0.35 * progress;
            Location at = center.clone().add(Math.cos(angle) * radius, -0.6 + 1.2 * progress, Math.sin(angle) * radius);
            w.spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, dust);
        }
        CrateShow.sound(m.settings.spinnerTickSound).ifPresent(s ->
                w.playSound(net.kyori.adventure.sound.Sound.sound(s.name(), s.source(), s.volume(), (float) Math.min(2.0, 0.8 + progress)),
                        center.getX(), center.getY(), center.getZ()));
    }

    /** Shows the reward: float + burst + firework (if a show is given), then the result message and title. */
    private void reveal(Player player, String name, OpenPlan.Plan plan, Show show, Location center) {
        OpenPlan.Grant grant = plan.grants().getFirst();
        if (show != null && center != null && shows.get(show.pedestal.crateId()) == show) {
            floatReward(player, plan, grant, show, center);
        }
        m.show().revealed(player, name, plan);
        if (player.isOnline() && m.settings.titleAtPedestal) {
            m.msg.title(player, grant.top() ? "world.title-top" : "world.title",
                    Msg.c("reward", m.opener().grantName(player, plan.crate(), grant)), Msg.mm("crate", m.crateMini(plan.crate())));
        }
    }

    private void floatReward(Player player, OpenPlan.Plan plan, OpenPlan.Grant grant, Show show, Location center) {
        World w = center.getWorld();
        Color color = color(plan.crate().rarity());
        TextDisplay hologram = holograms.get(show.pedestal.crateId());
        if (hologram != null && hologram.isValid()) {
            hologram.setVisibleByDefault(false);
        }
        double rise = m.settings.floatRise;
        Location start = center.clone().add(0, 0.15, 0);
        ItemDisplay item = w.spawn(start, ItemDisplay.class, d -> {
            d.setItemStack(m.show().grantItem(player, plan.crate(), grant));
            d.setBillboard(Display.Billboard.VERTICAL);
            d.setViewRange((float) m.settings.hologramViewRange);
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.6f, 0.6f, 0.6f), new AxisAngle4f()));
            d.setTeleportDuration(20);
            tag(d, show.pedestal);
        });
        show.entities.add(item);
        Component label = m.msg.component(Audience.empty(), grant.top() ? "world.label-top" : "world.label",
                Msg.c("reward", m.opener().grantName(player, plan.crate(), grant)), Msg.p("player", name(player)));
        TextDisplay text = w.spawn(center.clone().add(0, rise + 0.55, 0), TextDisplay.class, d -> {
            d.text(label);
            d.setBillboard(Display.Billboard.CENTER);
            d.setViewRange((float) m.settings.hologramViewRange);
            d.setAlignment(TextDisplay.TextAlignment.CENTER);
            d.setShadowed(true);
            d.setDefaultBackground(false);
            d.setBackgroundColor(Color.fromARGB(Math.max(0, Math.min(255, m.settings.hologramBackgroundAlpha)), 0, 0, 0));
            tag(d, show.pedestal);
        });
        show.entities.add(text);
        // Teleport a moment after the spawn so clients get the smooth rise (teleport-duration interpolation).
        show.tasks.add(m.core.scheduler().later("crates:yard-float", 2, () -> {
            if (item.isValid()) {
                item.teleport(start.clone().add(0, rise, 0));
            }
        }));
        if (m.settings.burstParticles > 0) {
            w.spawnParticle(Particle.DUST, center.clone().add(0, 0.3, 0), m.settings.burstParticles, 0.35, 0.35, 0.35, 0,
                    new Particle.DustOptions(color, 1.3f));
            w.spawnParticle(Particle.END_ROD, center.clone().add(0, 0.3, 0), Math.max(1, m.settings.burstParticles / 3), 0.25, 0.4,
                    0.25, 0.02);
        }
        if (grant.top() && m.settings.fireworkTop) {
            firework(center.clone().add(0, rise + 1.2, 0), color);
        }
        show.tasks.add(m.core.scheduler().later("crates:yard-float-end", m.settings.floatTicks, () -> {
            if (shows.get(show.pedestal.crateId()) == show) {
                shows.remove(show.pedestal.crateId());
                show.stop();
            }
        }));
    }

    private static String name(Player player) {
        return player == null ? "" : player.getName();
    }

    private void firework(Location at, Color color) {
        Firework fw = at.getWorld().spawn(at, Firework.class, f -> {
            FireworkMeta meta = f.getFireworkMeta();
            meta.clearEffects();
            meta.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.BALL_LARGE).withColor(color).withFade(Color.WHITE)
                    .trail(false).flicker(true).build());
            meta.setPower(0);
            f.setFireworkMeta(meta);
            f.setPersistent(false);
            f.addScoreboardTag(TAG);
        });
        m.core.scheduler().later("crates:yard-firework", 1, () -> {
            if (fw.isValid()) {
                fw.detonate();
            }
        });
    }

    /** The rarity's color from style.yml (white if missing). */
    private Color color(String rarity) {
        var colors = m.core.style().colors().get("rarity");
        var c = colors == null ? null : colors.get(rarity == null ? "" : rarity.toLowerCase(Locale.ROOT));
        return c == null ? Color.WHITE : Color.fromRGB(c.hex().value());
    }
}
