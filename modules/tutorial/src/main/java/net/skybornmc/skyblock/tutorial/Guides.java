package net.skybornmc.skyblock.tutorial;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.island.IslandService;
import net.skybornmc.skyblock.common.profile.ProfileService;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.text.TextMemo;
import net.skybornmc.skyblock.common.tutorial.TutorialService.State;
import net.skybornmc.skyblock.common.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The guides of a running tutorial, for Java and Bedrock (TUTORIAL.md §9.3): boss bar compass (Bedrock-safe arrows),
 * the walkable dotted guide trail and a small dust column at the target ({@link Trail}, per-player particles), dust
 * markers on farm spots and ore, Pip's target and route ({@link Pip}), and a hologram over the target that only this
 * player sees (text display, or a name-tag armor stand for Bedrock viewers, TQ32). One repeating task for every tutorial
 * player. Main thread.
 *
 * <p>TUT-FIX2, one place per message (owner 31 Oct: the same instruction reached the player in up to six places): the
 * boss bar always carries the current instruction; chat gets one line when a step is done or once as a hint after
 * {@code guide.hint-after-seconds} with nothing done; a title only at the start of a step; a hologram only at the target
 * of {@code guide.hologram-steps}. The action bar hint ({@code guide.action-bar-hint}) and Pip's chat lines
 * ({@code pip.chat}) are off by default.</p>
 *
 * <p>PERF7: the boss bar, action bar hint and hologram texts are parsed once per distinct value (shared
 * {@link TextMemo}, keyed by every input and the messages generation) instead of on every run (every
 * {@code guide.update-ticks}); the marker scans walk the box nearest first and stop when they have enough markers
 * ({@link MarkerScan#order}, cached per island home / standing spot); a scan never loads a chunk; a refused Pip or
 * hologram spawn is retried after {@code guide.spawn-retry-millis} instead of on every run. The parts are timed on their own
 * ({@code tutorial-guides-bar}, {@code -markers}, {@code -hologram}, {@code -pip}) so the log names the slow one.</p>
 */
final class Guides {

    /** Per-player guide state. */
    private static final class View {
        BossBar bar;
        /** The bar is hidden while a camera shot plays (same object, shown again on the next draw). */
        boolean barHidden;
        Entity hologram;
        String hologramFor;
        List<Location> markers = new ArrayList<>();
        String markersFor;
        long lastMarkerScan;
        long lastHint;
        /** Nearest-first scan order of the last marker centre (island home or standing spot) and its key. */
        MarkerScan.OrderKey orderKey;
        List<MarkerScan.Hit> order = List.of();
        /** No hologram spawn before this time (the last one was refused). */
        long hologramRetryAt;
        /** The walkable guide trail of this player (TUT-FIX2). */
        final Trail.State trail = new Trail.State();
        /** The step whose wait hint was already sent in chat (once per step). */
        String hinted;
    }

    private final TutorialModule module;
    private final Core core;
    private final NamespacedKey tag;
    private final Map<UUID, View> views = new HashMap<>();
    /** Parsed bar / hint / hologram texts by every input (PERF7); the compass distance makes a few hundred at most. */
    private final TextMemo<Component> texts = new TextMemo<>(512);
    private final Trail trail;
    private Scheduler.Task task;
    private long ticks;

    Guides(TutorialModule module) {
        this.module = module;
        this.core = module.core();
        this.tag = new NamespacedKey(core.plugin(), "tutorial_hologram");
        this.trail = new Trail(module);
    }

    Trail trail() {
        return trail;
    }

    void start() {
        stop();
        int every = Math.max(2, module.config().updateTicks);
        task = core.scheduler().repeat("tutorial-guides", every, every, () -> tick(every));
    }

    void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    // ------------------------------------------------------------------ show / hide

    void show(Player player, Progress p) {
        views.computeIfAbsent(player.getUniqueId(), k -> new View());
        refresh(player, p);
    }

    void hide(Player player) {
        View v = views.remove(player.getUniqueId());
        if (v == null) {
            return;
        }
        if (v.bar != null) {
            player.hideBossBar(v.bar);
        }
        removeHologram(v);
        module.pip().hide(player);
    }

    /** Redraws bar and hologram right away (step changed, counter moved). */
    void refresh(Player player, Progress p) {
        View v = views.get(player.getUniqueId());
        if (v == null || p.state != State.ACTIVE) {
            return;
        }
        v.markersFor = null;
        v.lastHint = 0;
        draw(player, p, v, true);
    }

    // ------------------------------------------------------------------ tick

    private void tick(int every) {
        ticks += every;
        boolean particles = module.config().particles && ticks % Math.max(every, module.config().particleTicks) < every;
        long now = System.currentTimeMillis();
        trail.newRun();
        for (Map.Entry<UUID, View> e : new ArrayList<>(views.entrySet())) {
            Player player = Bukkit.getPlayer(e.getKey());
            Progress p = module.engine().progress(e.getKey());
            if (player == null || p == null || p.state != State.ACTIVE || !module.config().enabled) {
                if (player != null) {
                    hide(player);
                } else {
                    removeHologram(e.getValue());
                    views.remove(e.getKey());
                }
                continue;
            }
            if (module.cameras().running(player.getUniqueId())) {
                p.lastActivity = now; // watching a camera is not idle
                View v = e.getValue();
                if (v.bar != null && !v.barHidden) {
                    player.hideBossBar(v.bar); // A3-R1-02: no bar over a camera shot; shown again on the next tick after it
                    v.barHidden = true;
                }
                continue;
            }
            View v = e.getValue();
            Optional<Location> target = draw(player, p, v, particles);
            Route route = p.step != null && p.step.equals(v.trail.step) ? v.trail.route : null; // never an old step's way
            if (pipStep(module.config().pipSteps, p.step)) {
                module.pip().guide(player, p.step, perch(player, p, v, target), route, v.trail.cursor);
            } else {
                module.pip().hide(player); // owner 3 Nov 2026: on the island Kindle guides; Pip comes along off the island
            }
            module.listener().tick(player, p);
            waitHint(player, p, v, now);
            idle(player, p, now);
        }
        module.pip().prune();
    }

    /** Where Pip lands for this step: the first farm spot / ore marker on the counted steps, else the target. */
    private Optional<Location> perch(Player player, Progress p, View v, Optional<Location> target) {
        if ((Steps.CROP.equals(p.step) || Steps.MINE.equals(p.step)) && p.step.equals(v.markersFor) && !v.markers.isEmpty()) {
            Location m = v.markers.getFirst();
            if (sameWorld(player, m)) {
                // Farm spots are the dirt block itself; ore markers sit one block under the ore (see ores()).
                return Optional.of(m.clone().add(0.5, Steps.MINE.equals(p.step) ? 2.0 : 1.0, 0.5));
            }
        }
        return target;
    }

    /**
     * TUT-FIX2: a player who did nothing on the step for {@code guide.hint-after-seconds} gets the hint once as one chat
     * line (the boss bar already shows the instruction all the time).
     */
    private void waitHint(Player player, Progress p, View v, long now) {
        int after = module.config().hintAfterSeconds;
        String step = p.step;
        if (after <= 0 || step == null || step.equals(v.hinted) || !module.msg().has("steps." + step + ".hint")) {
            return;
        }
        long since = Math.max(p.stepStartedAt, p.lastActivity);
        if (since <= 0 || now - since < after * 1000L) {
            return;
        }
        v.hinted = step;
        module.msg().send(player, "hint.chat", Msg.c("hint", module.msg().component(player, "steps." + step + ".hint", counts(p, step))));
        core.sounds().play(player, "notify");
    }

    private void idle(Player player, Progress p, long now) {
        int minutes = module.config().idleReminderMinutes;
        if (minutes <= 0 || p.idleReminded || now - p.lastActivity < minutes * 60_000L) {
            return;
        }
        p.idleReminded = true;
        // TUT-FIX2: one chat line (titles are only for the start of a step) and Pip's call.
        module.msg().send(player, "idle.chat", Msg.c("step", module.engine().stepName(player, p.step)));
        core.sounds().play(player, "notify");
        module.pip().say(player, "idle");
        module.engine().event(player, p, "idle");
    }

    private Optional<Location> draw(Player player, Progress p, View v, boolean particles) {
        String step = p.step;
        if (step == null) {
            return Optional.empty();
        }
        Optional<Location> target = module.targets().target(player, p);
        if (module.config().bossbar && !module.cameras().running(player.getUniqueId())) {
            core.scheduler().timer().time("tutorial-guides-bar", () -> bar(player, p, v, target));
        }
        long now = System.currentTimeMillis();
        if (module.config().actionBarHint && now - v.lastHint >= 2000 && module.msg().has("steps." + step + ".hint")) {
            v.lastHint = now;
            int have = p.count(step);
            int needed = needed(step);
            player.sendActionBar(text(player, "hint|" + step + '|' + have + '|' + needed,
                    () -> module.msg().component(player, "steps." + step + ".hint", counts(p, step))));
        }
        core.scheduler().timer().time("tutorial-guides-hologram", () -> hologram(player, p, v, target));
        trail.update(player, step, target, v.trail);
        if (particles) {
            particles(player, p, v, target);
        }
        return target;
    }

    /**
     * A tutorial text built by {@code build}, cached under {@code key} (which must hold every input of the text besides
     * the viewer's platform and the messages generation, both added here). Never cached while any tutorial text uses a
     * player placeholder ({@code <ph:...>}), whose value could change without the key changing.
     */
    private Component text(Player viewer, String key, java.util.function.Supplier<Component> build) {
        long generation = core.messages().generation();
        if (generation != placeholderGeneration) {
            placeholderTexts = core.messages().usesPlayerPlaceholders(TutorialModule.ID); // a walk over every text: once per reload
            placeholderGeneration = generation;
        }
        if (placeholderTexts) {
            return build.get();
        }
        String full = module.engine().bedrock(viewer) + "|" + key;
        return texts.get(generation, full, k -> build.get());
    }

    private long placeholderGeneration = Long.MIN_VALUE;
    private boolean placeholderTexts;

    // ------------------------------------------------------------------ boss bar compass

    /**
     * A3-R1-02: one yellow bar, {@code Tutorial 3/11 · instruction}; the fill is always steps done out of the total
     * ({@link TutorialBar}), so it only changes when a step is done (the distance never moves it).
     */
    private void bar(Player player, Progress p, View v, Optional<Location> target) {
        String step = p.step;
        TutorialFlow flow = module.engine().flow();
        Component text;
        int needed = needed(step);
        int number = flow.number(step);
        int total = flow.size();
        float progress = TutorialBar.fill(p.doneSet(), flow);
        // The texts are parsed only when one of their inputs changed (PERF7); base is built lazily inside the builds.
        String head = step + '|' + number + '|' + total + '|';
        java.util.function.Supplier<TagResolver[]> base = () -> new TagResolver[]{
                Msg.c("objective", module.msg().component(player, "steps." + step + ".objective")),
                Msg.number("number", number), Msg.number("total", total),
                Msg.c("head", module.msg().component(player, "bar.head", Msg.number("number", number), Msg.number("total", total)))};
        if (needed > 0) {
            int have = p.count(step);
            text = text(player, "bar.count|" + head + have + '|' + needed, () -> module.msg().component(player, "bar.count",
                    concat(base.get(), Msg.number("count", have), Msg.number("needed", needed))));
        } else if (target.isPresent() && sameWorld(player, target.get())) {
            Location to = target.get();
            Location at = player.getLocation();
            double dx = to.getX() - at.getX();
            double dz = to.getZ() - at.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            Compass.Direction dir = Compass.direction(at.getYaw(), dx, dz, module.config().aheadDegrees, module.config().behindDegrees, 2.5);
            String key = "compass." + dir.name().toLowerCase(Locale.ROOT);
            long distance = Math.round(dist);
            text = text(player, key + '|' + head + distance, () -> {
                Component name = module.msg().has("steps." + step + ".target")
                        ? module.msg().component(player, "steps." + step + ".target") : module.engine().stepName(player, step);
                return module.msg().component(player, key, concat(base.get(), Msg.c("target", name), Msg.number("distance", distance)));
            });
        } else if (!module.targets().atPlace(player, step)) {
            String key = Targets.place(step) == Targets.Place.ISLAND ? "bar.away-island" : "bar.away-depths";
            text = text(player, key + '|' + head, () -> module.msg().component(player, key, base.get()));
        } else {
            text = text(player, "bar.plain|" + head, () -> module.msg().component(player, "bar.plain", base.get()));
        }
        if (v.bar == null) {
            v.bar = BossBar.bossBar(text, progress, color(), overlay());
            player.showBossBar(v.bar);
            v.barHidden = false;
        } else {
            v.bar.name(text);
            v.bar.progress(progress);
            v.bar.color(color());
            v.bar.overlay(overlay());
            if (v.barHidden) {
                player.showBossBar(v.bar);
                v.barHidden = false;
            }
        }
    }

    private BossBar.Color color() {
        try {
            return BossBar.Color.valueOf(module.config().bossbarColor.strip().toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            return BossBar.Color.YELLOW;
        }
    }

    /** {@code guide.bossbar-overlay} as the Adventure overlay (A3-R1-02). */
    private BossBar.Overlay overlay() {
        return module.config().bossbarOverlay.adventure();
    }

    private int needed(String step) {
        return switch (step) {
            case Steps.CROP -> module.config().cropCount;
            case Steps.MINE -> module.config().mineCount;
            default -> 0;
        };
    }

    /**
     * Owner 3 Nov 2026 ("tutorial text in chat is hard to see when a GUI is open, add the text inside the GUI"): the
     * current step as one book in a free bottom-row slot of every menu (common MenuService hints). Null when no step.
     */
    org.bukkit.inventory.ItemStack menuHint(Player player) {
        if (!module.config().menuHint) {
            return null;
        }
        Progress p = module.engine().progress(player.getUniqueId());
        if (p == null || p.state != State.ACTIVE || p.step == null) {
            return null;
        }
        String step = p.step;
        TutorialFlow flow = module.engine().flow();
        Component hint = module.msg().has("steps." + step + ".hint")
                ? module.msg().component(player, "steps." + step + ".hint", counts(p, step)) : Component.empty();
        return module.core().menus().item(Material.KNOWLEDGE_BOOK, player, TutorialModule.ID, "menu-hint",
                Msg.number("number", flow.number(step)), Msg.number("total", flow.size()),
                Msg.c("objective", module.msg().component(player, "steps." + step + ".objective")),
                Msg.c("hint", hint), Msg.number("count", p.count(step)), Msg.number("needed", needed(step)));
    }

    private TagResolver[] counts(Progress p, String step) {
        return new TagResolver[]{Msg.number("count", p.count(step)), Msg.number("needed", needed(step))};
    }

    private static TagResolver[] concat(TagResolver[] a, TagResolver... b) {
        TagResolver[] out = java.util.Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    private static boolean sameWorld(Player player, Location l) {
        return l.getWorld() != null && l.getWorld().equals(player.getWorld());
    }

    // ------------------------------------------------------------------ particles

    private void particles(Player player, Progress p, View v, Optional<Location> target) {
        String step = p.step;
        TutorialConfig c = module.config();
        trail.draw(player, step, v.trail);
        if (c.column) {
            target.filter(t -> sameWorld(player, t) && t.distanceSquared(player.getLocation()) < 96 * 96)
                    .ifPresent(t -> column(player, t, c.columnHeight, trail.dust(step)[0]));
        }
        if (step.equals(Steps.CROP) || step.equals(Steps.MINE)) {
            long now = System.currentTimeMillis();
            if (!step.equals(v.markersFor) || now - v.lastMarkerScan > 2000) {
                v.markers = core.scheduler().timer().time("tutorial-guides-markers",
                        () -> step.equals(Steps.CROP) ? farmSpots(player, v) : ores(player, v));
                v.markersFor = step;
                v.lastMarkerScan = now;
            }
            for (Location m : v.markers) {
                if (sameWorld(player, m)) {
                    player.spawnParticle(Particle.HAPPY_VILLAGER, m.getX() + 0.5, m.getY() + 1.15, m.getZ() + 0.5, 3, 0.25, 0.1, 0.25, 0);
                }
            }
        }
    }

    /** TUT-FIX2: a soft dust column in the trail colour (no fire): one dot per block, from the target up. */
    private static void column(Player player, Location t, int height, Particle.DustOptions dust) {
        for (int y = 1; y <= height; y++) {
            player.spawnParticle(Particle.DUST, t.getX(), t.getY() + y, t.getZ(), 1, 0.05, 0.05, 0.05, 0, dust);
        }
    }

    /**
     * Up to 3 dirt/grass/farmland blocks with air above near the island home (first-crop step). PERF7: walks the box
     * nearest first (order cached per home) and stops at {@code crop-count}; same spots as the old full scan + sort.
     */
    private List<Location> farmSpots(Player player, View v) {
        List<Location> out = new ArrayList<>();
        Optional<Location> home = module.targets().home(player);
        if (home.isEmpty() || !sameWorld(player, home.get())) {
            return out;
        }
        Location h = home.get();
        int r = module.config().cropRadius;
        World world = h.getWorld();
        if (!loaded(world, h.getBlockX(), h.getBlockZ(), r)) {
            return out; // never load chunks for markers: the home is out of sight anyway
        }
        Optional<IslandService> islands = core.services().optional(IslandService.class);
        UUID key = ProfileService.of(core.services()).profileKey(player);
        List<MarkerScan.Hit> order = order(v, new MarkerScan.OrderKey(world.getName(), h.getBlockX(), h.getBlockY(), h.getBlockZ(),
                h.getX(), h.getY(), h.getZ(), r, -3, 2, MarkerScan.Loop.X_Z_Y));
        for (MarkerScan.Hit hit : MarkerScan.firstMatches(order, hit -> {
            Material m = world.getType(hit.x(), hit.y(), hit.z());
            if ((m != Material.GRASS_BLOCK && m != Material.DIRT && m != Material.FARMLAND)
                    || !world.getType(hit.x(), hit.y() + 1, hit.z()).isAir()) {
                return false;
            }
            Location loc = new Location(world, hit.x(), hit.y(), hit.z());
            return islands.isEmpty() || !islands.get().islandAt(loc).map(i -> !i.owner().equals(key)
                    && !islands.get().isMember(i.id(), key)).orElse(true);
        }, module.config().cropCount)) {
            out.add(new Location(world, hit.x(), hit.y(), hit.z()));
        }
        return out;
    }

    /**
     * The nearest blocks matching {@code guide.markers.mine-blocks} around the player (mine step). PERF7: nearest first
     * (order cached while the player stands on the same spot, which is how people mine), stops at {@code mine-count}.
     */
    private List<Location> ores(Player player, View v) {
        List<Location> out = new ArrayList<>();
        Location at = player.getLocation();
        int r = module.config().mineRadius;
        World world = at.getWorld();
        if (!loaded(world, at.getBlockX(), at.getBlockZ(), r)) {
            return out;
        }
        MarkerScan.Matcher matcher = mineMatcher();
        Map<Material, Boolean> seen = new java.util.EnumMap<>(Material.class);
        List<MarkerScan.Hit> order = order(v, new MarkerScan.OrderKey(world.getName(), at.getBlockX(), at.getBlockY(), at.getBlockZ(),
                at.getX(), at.getY(), at.getZ(), r, -r, r, MarkerScan.Loop.X_Y_Z));
        for (MarkerScan.Hit hit : MarkerScan.firstMatches(order, hit -> seen.computeIfAbsent(world.getType(hit.x(), hit.y(), hit.z()),
                k -> matcher.matches(k.getKey().getKey())), module.config().mineMarkers)) {
            out.add(new Location(world, hit.x(), hit.y() - 1, hit.z()));
        }
        return out;
    }

    /** The view's cached nearest-first order for {@code key}, recomputed when the centre or box changed. */
    private static List<MarkerScan.Hit> order(View v, MarkerScan.OrderKey key) {
        if (!key.equals(v.orderKey)) {
            v.order = MarkerScan.order(key.bx(), key.by(), key.bz(), key.cx(), key.cy(), key.cz(), key.r(), key.dyMin(), key.dyMax(),
                    key.loop());
            v.orderKey = key;
        }
        return v.order;
    }

    /** True when every chunk of the box {@code (x, z) ± r} is loaded (a block read would load it on the main thread). */
    private static boolean loaded(World world, int x, int z, int r) {
        int[] xs = MarkerScan.chunkSpan(x, r);
        int[] zs = MarkerScan.chunkSpan(z, r);
        for (int cx = xs[0]; cx <= xs[1]; cx++) {
            for (int cz = zs[0]; cz <= zs[1]; cz++) {
                if (!world.isChunkLoaded(cx, cz)) {
                    return false;
                }
            }
        }
        return true;
    }

    private List<String> mineSource;
    private MarkerScan.Matcher mineMatcher = MarkerScan.Matcher.compile(List.of());

    /** {@code guide.markers.mine-blocks}, compiled once per config list (PERF4). */
    private MarkerScan.Matcher mineMatcher() {
        List<String> raw = module.config().mineBlocks;
        if (raw != mineSource) {
            mineMatcher = MarkerScan.Matcher.compile(raw);
            mineSource = raw;
        }
        return mineMatcher;
    }

    // ------------------------------------------------------------------ hologram (only this player sees it)

    private void hologram(Player player, Progress p, View v, Optional<Location> target) {
        String step = p.step;
        String key = "hologram." + step;
        boolean wanted = module.config().holograms && target.isPresent() && module.msg().has(key)
                && module.config().hologramSteps.stream().anyMatch(h -> h.strip().equalsIgnoreCase(step))
                && sameWorld(player, target.get()) && target.get().distanceSquared(player.getLocation())
                < (double) module.config().hologramRange * module.config().hologramRange;
        if (!wanted) {
            removeHologram(v);
            return;
        }
        Location at = target.get().clone().add(0, module.config().hologramHeight, 0);
        String id = step + "@" + at.getBlockX() + "," + at.getBlockY() + "," + at.getBlockZ();
        if (v.hologram != null && v.hologram.isValid() && id.equals(v.hologramFor)) {
            return;
        }
        removeHologram(v);
        long now = System.currentTimeMillis();
        if (now < v.hologramRetryAt) {
            return; // the last spawn was refused: try again a little later, not on every run
        }
        Component text = text(player, key, () -> module.msg().component(player, key));
        boolean armorStand = module.engine().bedrock(player) && "armor-stand".equalsIgnoreCase(module.config().hologramsBedrock.strip());
        Entity e;
        if (armorStand) {
            e = at.getWorld().spawn(at.clone().add(0, -1.8, 0), ArmorStand.class, s -> {
                s.setVisibleByDefault(false);
                s.setPersistent(false);
                s.setInvisible(true);
                s.setMarker(true);
                s.setGravity(false);
                s.setInvulnerable(true);
                s.setSilent(true);
                s.customName(text);
                s.setCustomNameVisible(true);
                s.getPersistentDataContainer().set(tag, PersistentDataType.STRING, player.getUniqueId().toString());
            });
        } else {
            e = at.getWorld().spawn(at, TextDisplay.class, d -> {
                d.setVisibleByDefault(false);
                d.setPersistent(false);
                d.text(text);
                d.setBillboard(Display.Billboard.CENTER);
                d.setAlignment(TextDisplay.TextAlignment.CENTER);
                d.setShadowed(true);
                d.setSeeThrough(true);
                d.getPersistentDataContainer().set(tag, PersistentDataType.STRING, player.getUniqueId().toString());
            });
        }
        if (!e.isValid()) {
            v.hologramRetryAt = now + module.config().spawnRetryMillis; // spawn refused here (e.g. by another plugin)
            return;
        }
        player.showEntity(core.plugin(), e);
        v.hologram = e;
        v.hologramFor = id;
    }

    private void removeHologram(View v) {
        if (v.hologram != null && v.hologram.isValid()) {
            v.hologram.remove();
        }
        v.hologram = null;
        v.hologramFor = null;
    }

    /** Removes guide holograms left in loaded chunks (e.g. after a crash); they are never saved with the chunk. */
    void cleanStray() {
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntitiesByClasses(TextDisplay.class, ArmorStand.class)) {
                if (e.getPersistentDataContainer().has(tag, PersistentDataType.STRING)) {
                    e.remove();
                }
            }
        }
    }

    void hideAll() {
        module.pip().hideAll();
        for (UUID id : new ArrayList<>(views.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                hide(p);
            } else {
                removeHologram(views.remove(id));
            }
        }
    }

    /** Pure: Pip comes along for this step ({@code pip.steps}; empty = every step). */
    static boolean pipStep(java.util.List<String> pipSteps, String step) {
        return step != null && (pipSteps == null || pipSteps.isEmpty() || pipSteps.contains(step));
    }
}
