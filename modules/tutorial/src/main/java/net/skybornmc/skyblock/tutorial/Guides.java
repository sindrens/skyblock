package net.skybornmc.skyblock.tutorial;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.island.IslandService;
import net.skybornmc.skyblock.common.profile.ProfileService;
import net.skybornmc.skyblock.common.text.Msg;
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
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The guides of a running tutorial, for Java and Bedrock (TUTORIAL.md §9.3): boss bar compass (Bedrock-safe arrows),
 * action bar hint, flame trail and flame column (per-player particles), dust markers on farm spots and ore, and a
 * hologram over the target that only this player sees (text display, or a name-tag armor stand for Bedrock viewers,
 * TQ32). One repeating task for every tutorial player. Main thread.
 */
final class Guides {

    private static final float BAR_STEP_MIN = 0.05f;

    /** Per-player guide state. */
    private static final class View {
        BossBar bar;
        Entity hologram;
        String hologramFor;
        List<Location> markers = new ArrayList<>();
        String markersFor;
        long lastMarkerScan;
        long lastHint;
    }

    private final TutorialModule module;
    private final Core core;
    private final NamespacedKey tag;
    private final Map<UUID, View> views = new HashMap<>();
    private Scheduler.Task task;
    private long ticks;

    Guides(TutorialModule module) {
        this.module = module;
        this.core = module.core();
        this.tag = new NamespacedKey(core.plugin(), "tutorial_hologram");
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
                continue;
            }
            Optional<Location> target = draw(player, p, e.getValue(), particles);
            module.pip().update(player, target);
            module.listener().tick(player, p);
            idle(player, p, now);
        }
        module.pip().prune();
    }

    private void idle(Player player, Progress p, long now) {
        int minutes = module.config().idleReminderMinutes;
        if (minutes <= 0 || p.idleReminded || now - p.lastActivity < minutes * 60_000L) {
            return;
        }
        p.idleReminded = true;
        module.msg().title(player, "idle.title", Msg.c("step", module.engine().stepName(player, p.step)));
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
        if (module.config().bossbar) {
            bar(player, p, v, target);
        }
        long now = System.currentTimeMillis();
        if (now - v.lastHint >= 2000 && module.msg().has("steps." + step + ".hint")) {
            v.lastHint = now;
            module.msg().actionBar(player, "steps." + step + ".hint", counts(p, step));
        }
        hologram(player, p, v, target);
        if (particles) {
            particles(player, p, v, target);
        }
        return target;
    }

    // ------------------------------------------------------------------ boss bar compass

    private void bar(Player player, Progress p, View v, Optional<Location> target) {
        String step = p.step;
        TutorialFlow flow = module.engine().flow();
        Component text;
        float progress;
        int needed = needed(step);
        TagResolver[] base = {Msg.c("objective", module.msg().component(player, "steps." + step + ".objective")),
                Msg.number("number", flow.number(step)), Msg.number("total", flow.size())};
        if (needed > 0) {
            int have = p.count(step);
            text = module.msg().component(player, "bar.count", concat(base, Msg.number("count", have), Msg.number("needed", needed)));
            progress = Math.max(BAR_STEP_MIN, Math.min(1f, have / (float) needed));
        } else if (target.isPresent() && sameWorld(player, target.get())) {
            Location to = target.get();
            Location at = player.getLocation();
            double dx = to.getX() - at.getX();
            double dz = to.getZ() - at.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            Compass.Direction dir = Compass.direction(at.getYaw(), dx, dz, module.config().aheadDegrees, module.config().behindDegrees, 2.5);
            Component name = module.msg().has("steps." + step + ".target")
                    ? module.msg().component(player, "steps." + step + ".target") : module.engine().stepName(player, step);
            text = module.msg().component(player, "compass." + dir.name().toLowerCase(Locale.ROOT),
                    concat(base, Msg.c("target", name), Msg.number("distance", Math.round(dist))));
            progress = Compass.distanceProgress(dist, 100);
        } else if (!module.targets().atPlace(player, step)) {
            text = module.msg().component(player, Targets.place(step) == Targets.Place.ISLAND ? "bar.away-island" : "bar.away-depths", base);
            progress = Math.max(BAR_STEP_MIN, flow.index(step) / (float) Math.max(1, flow.size()));
        } else {
            text = module.msg().component(player, "bar.plain", base);
            progress = Math.max(BAR_STEP_MIN, flow.index(step) / (float) Math.max(1, flow.size()));
        }
        if (v.bar == null) {
            v.bar = BossBar.bossBar(text, progress, color(), BossBar.Overlay.PROGRESS);
            player.showBossBar(v.bar);
        } else {
            v.bar.name(text);
            v.bar.progress(progress);
            v.bar.color(color());
        }
    }

    private BossBar.Color color() {
        try {
            return BossBar.Color.valueOf(module.config().bossbarColor.strip().toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            return BossBar.Color.RED;
        }
    }

    private int needed(String step) {
        return switch (step) {
            case Steps.CROP -> module.config().cropCount;
            case Steps.MINE -> module.config().mineCount;
            default -> 0;
        };
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
        if (step.equals(Steps.PORTAL)) {
            trail(player);
        }
        target.filter(t -> sameWorld(player, t) && t.distanceSquared(player.getLocation()) < 96 * 96)
                .ifPresent(t -> column(player, t, c.columnHeight));
        if (step.equals(Steps.CROP) || step.equals(Steps.MINE)) {
            long now = System.currentTimeMillis();
            if (!step.equals(v.markersFor) || now - v.lastMarkerScan > 2000) {
                v.markers = step.equals(Steps.CROP) ? farmSpots(player) : ores(player);
                v.markersFor = step;
                v.lastMarkerScan = now;
            }
            for (Location m : v.markers) {
                if (sameWorld(player, m)) {
                    player.spawnParticle(Particle.HAPPY_VILLAGER, m.clone().add(0.5, 1.15, 0.5), 4, 0.25, 0.1, 0.25, 0);
                }
            }
        }
    }

    /** Flames every {@code guide.trail.spacing} blocks along the trail nodes, up to {@code guide.trail.ahead} ahead. */
    private List<String> trailSource;
    private List<TargetSpec.Point> trailPoints = List.of();

    /** The trail nodes, parsed once per config list (PERF2, P-21: they were parsed for every node on every particle tick). */
    private List<TargetSpec.Point> trailPoints() {
        List<String> raw = module.config().trailNodes;
        if (raw != trailSource) {
            List<TargetSpec.Point> points = new ArrayList<>();
            for (String node : raw) {
                TargetSpec.parse(node).ifPresent(spec -> {
                    if (spec instanceof TargetSpec.Point pt) {
                        points.add(pt);
                    }
                });
            }
            trailPoints = List.copyOf(points);
            trailSource = raw;
        }
        return trailPoints;
    }

    private void trail(Player player) {
        List<Location> nodes = new ArrayList<>();
        World here = player.getWorld();
        for (TargetSpec.Point pt : trailPoints()) {
            if (pt.world() != null && pt.world().equalsIgnoreCase(here.getName())) {
                nodes.add(new Location(here, pt.x(), pt.y(), pt.z()));
            }
        }
        if (nodes.size() < 2) {
            return;
        }
        Location at = player.getLocation();
        // Nearest segment, then walk forward from the player's projection.
        int bestSeg = 0;
        double bestDist = Double.MAX_VALUE;
        double bestT = 0;
        for (int i = 0; i < nodes.size() - 1; i++) {
            Vector a = nodes.get(i).toVector();
            Vector b = nodes.get(i + 1).toVector();
            Vector ab = b.clone().subtract(a);
            double len2 = ab.lengthSquared();
            double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, at.toVector().subtract(a).dot(ab) / len2));
            double d = a.clone().add(ab.clone().multiply(t)).distanceSquared(at.toVector());
            if (d < bestDist) {
                bestDist = d;
                bestSeg = i;
                bestT = t;
            }
        }
        if (bestDist > 40 * 40) {
            return; // far away from the road: the compass leads
        }
        double spacing = Math.max(1, module.config().trailSpacing);
        double left = module.config().trailAhead;
        double offset = spacing;
        for (int i = bestSeg; i < nodes.size() - 1 && left > 0; i++) {
            Vector a = nodes.get(i).toVector();
            Vector b = nodes.get(i + 1).toVector();
            Vector ab = b.clone().subtract(a);
            double len = ab.length();
            if (len < 1e-6) {
                continue;
            }
            Vector dir = ab.clone().multiply(1 / len);
            double startAt = i == bestSeg ? bestT * len : 0;
            for (double s = startAt + offset; s <= len && left > 0; s += spacing) {
                Vector pos = a.clone().add(dir.clone().multiply(s));
                player.spawnParticle(Particle.FLAME, pos.getX(), pos.getY() + 0.2, pos.getZ(), 1, 0.05, 0.02, 0.05, 0.002);
                left -= spacing;
            }
            offset = spacing;
        }
    }

    private void column(Player player, Location t, int height) {
        for (double y = 0; y <= height; y += 0.75) {
            player.spawnParticle(y % 1.5 == 0 ? Particle.FLAME : Particle.END_ROD, t.getX(), t.getY() + y, t.getZ(), 1, 0.08, 0.05, 0.08, 0.002);
        }
    }

    /** Up to 3 dirt/grass/farmland blocks with air above near the island home (first-crop step). */
    private List<Location> farmSpots(Player player) {
        List<Location> out = new ArrayList<>();
        Optional<Location> home = module.targets().home(player);
        if (home.isEmpty() || !sameWorld(player, home.get())) {
            return out;
        }
        Location h = home.get();
        int r = module.config().cropRadius;
        Optional<IslandService> islands = core.services().optional(IslandService.class);
        UUID key = ProfileService.of(core.services()).profileKey(player);
        World world = h.getWorld();
        List<MarkerScan.Hit> found = new ArrayList<>();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -3; dy <= 2; dy++) {
                    int x = h.getBlockX() + dx;
                    int y = h.getBlockY() + dy;
                    int z = h.getBlockZ() + dz;
                    Material m = world.getType(x, y, z);
                    if ((m == Material.GRASS_BLOCK || m == Material.DIRT || m == Material.FARMLAND)
                            && world.getType(x, y + 1, z).isAir()) {
                        found.add(MarkerScan.Hit.of(x, y, z, h.getX(), h.getY(), h.getZ()));
                    }
                }
            }
        }
        for (MarkerScan.Hit hit : MarkerScan.nearestFirst(found)) {
            Location loc = new Location(world, hit.x(), hit.y(), hit.z());
            if (islands.isPresent() && islands.get().islandAt(loc).map(i -> !i.owner().equals(key)
                    && !islands.get().isMember(i.id(), key)).orElse(true)) {
                continue;
            }
            out.add(loc);
            if (out.size() >= module.config().cropCount) {
                break;
            }
        }
        return out;
    }

    /** The nearest blocks matching {@code guide.markers.mine-blocks} around the player (mine step). */
    private List<Location> ores(Player player) {
        List<Location> out = new ArrayList<>();
        Location at = player.getLocation();
        int r = module.config().mineRadius;
        World world = at.getWorld();
        MarkerScan.Matcher matcher = mineMatcher();
        Map<Material, Boolean> seen = new java.util.EnumMap<>(Material.class);
        List<MarkerScan.Hit> found = new ArrayList<>();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    int x = at.getBlockX() + dx;
                    int y = at.getBlockY() + dy;
                    int z = at.getBlockZ() + dz;
                    Material m = world.getType(x, y, z);
                    if (seen.computeIfAbsent(m, k -> matcher.matches(k.getKey().getKey()))) {
                        found.add(MarkerScan.Hit.of(x, y, z, at.getX(), at.getY(), at.getZ()));
                    }
                }
            }
        }
        for (MarkerScan.Hit hit : MarkerScan.nearestFirst(found)) {
            out.add(new Location(world, hit.x(), hit.y() - 1, hit.z()));
            if (out.size() >= module.config().mineMarkers) {
                break;
            }
        }
        return out;
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
        Component text = module.msg().component(player, key);
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
}
