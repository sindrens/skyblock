package net.skybornmc.skyblock.npcs;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;

/**
 * Spawns and removes the entity sets of every point. Entities are never saved ({@code persistent = false}): a set is
 * spawned when its chunk's entities load, removed when they unload, and everything is removed on disable. Left-overs
 * with our tag that we don't track (crash, failed reload) are removed when their chunk loads. The only repeating work
 * is the head turn every {@code npcs.look-ticks} for NPCs with a player nearby.
 */
final class Placer implements Listener {

    /** Scoreboard tag on every entity of this module (fast clean-up). */
    static final String TAG = "phoenix_npcs";
    /** Eye height of a standing mannequin/villager (blocks). */
    private static final double EYE = 1.62;

    private final NpcsModule module;
    private final NamespacedKey kindKey;
    private final NamespacedKey idKey;
    private final Map<String, Placed> byKey = new LinkedHashMap<>();
    private final Map<String, Map<Long, List<Placed>>> byChunk = new HashMap<>();
    private net.skybornmc.skyblock.common.util.Scheduler.Task lookTask;
    /** PERF3: sets waiting to be spawned, a few per tick ({@code spawning.per-tick} / {@code spawning.budget-ms}). */
    private final SpawnQueue<Placed> queue = new SpawnQueue<>();
    /**
     * PERF5: sets of the previous placement (reload) waiting to be removed, a few per tick before any new set spawns
     * (removing every NPC, hologram and leaderboard in the reload tick was most of {@code npcs:apply}).
     */
    private final SpawnQueue<Placed> retiring = new SpawnQueue<>();
    private net.skybornmc.skyblock.common.util.Scheduler.Task spawnTask;
    /**
     * PERF8: the left-over walk of the first placement of an enable runs in the first spawn tick (before anything of
     * this placement spawns), not inside {@code npcs:apply}.
     */
    private boolean purgePending;

    /** What one spawn tick does (PERF8). */
    enum Step { PURGE, RETIRE, SPAWN, DONE }

    /**
     * The work of one spawn tick: the left-over walk first (alone in its tick), then removing the previous placement,
     * then spawning; DONE when nothing is left (the task stops). Pure.
     */
    static Step step(boolean purgePending, boolean retiringEmpty, boolean queueEmpty) {
        if (purgePending) {
            return Step.PURGE;
        }
        if (!retiringEmpty) {
            return Step.RETIRE;
        }
        return queueEmpty ? Step.DONE : Step.SPAWN;
    }

    Placer(NpcsModule module) {
        this.module = module;
        this.kindKey = new NamespacedKey(module.core().plugin(), "npcs_kind");
        this.idKey = new NamespacedKey(module.core().plugin(), "npcs_id");
    }

    // ------------------------------------------------------------------ lifecycle

    /**
     * Removes every placed set and left-over, then queues {@code spots} in the loaded chunks; they are spawned over the
     * next ticks (PERF3: spawning them all in this tick took over a second on update101). Never loads a chunk.
     */
    void start(Spots spots) {
        boolean reload = !byKey.isEmpty() || !retiring.isEmpty();
        var timer = module.core().scheduler().timer();
        timer.time("npcs:apply-retire", this::retire);
        if (!reload) {
            // First placement of this enable: left-overs of a crash or an earlier enable. On a reload every entity of
            // ours is tracked (byKey, then retiring), so the walk over every world's entities is skipped.
            // PERF8: the walk runs in the first spawn tick (npcs:purge), before anything of this placement spawns.
            purgePending = true;
        }
        NpcsConfig c = module.config();
        timer.time("npcs:apply-track", () -> {
            if (c.npcsEnabled) {
                spots.npcs().forEach(n -> track(new Placed(Placed.Kind.NPC, n, null)));
            }
            if (c.hologramsEnabled) {
                spots.holograms().forEach(t -> track(new Placed(Placed.Kind.HOLOGRAM, null, t)));
            }
            if (c.leaderboardsEnabled) {
                spots.boards().forEach(t -> track(new Placed(Placed.Kind.BOARD, null, t)));
            }
        });
        timer.time("npcs:apply-queue", () -> {
            for (Placed p : byKey.values()) {
                if (loaded(p) != null) {
                    queue.add(p);
                }
            }
        });
        drainLater();
        if (c.npcsEnabled && c.lookAtPlayers && !spots.npcs().isEmpty()) {
            lookTask = module.core().scheduler().repeat("npcs:look", c.lookTicks, c.lookTicks, this::look);
        }
    }

    /** Removes every entity of this module at once (disable) and forgets the points. */
    void stop() {
        retire();
        retiring.drain(Integer.MAX_VALUE, Long.MAX_VALUE, System::nanoTime, Placer::removeSet);
        retiring.clear();
    }

    /**
     * PERF5: forgets the points and moves their sets to {@link #retiring}; the spawn task removes them over the next
     * ticks (before it spawns anything new, so a reload never shows a set twice for longer than the removal takes).
     */
    private void retire() {
        if (lookTask != null) {
            lookTask.cancel();
            lookTask = null;
        }
        if (spawnTask != null) {
            spawnTask.cancel();
            spawnTask = null;
        }
        queue.clear();
        for (Placed p : byKey.values()) {
            if (p.body != null || p.label != null || p.click != null) {
                retiring.add(p);
            }
        }
        byKey.clear();
        byChunk.clear();
    }

    /** Removes one retired set (always counts as work, so the removal budget applies). */
    private static boolean removeSet(Placed p) {
        p.remove();
        return true;
    }

    private void track(Placed p) {
        byKey.put(p.key(), p);
        byChunk.computeIfAbsent(p.world, w -> new HashMap<>()).computeIfAbsent(p.chunkKey(), k -> new ArrayList<>()).add(p);
    }

    /** Removes tagged entities from loaded chunks (left from an earlier enable). */
    private void purgeLeftovers() {
        int removed = 0;
        for (World w : Bukkit.getWorlds()) {
            // PERF3: only the entity kinds NPCs are made of can carry the tag (no tag-set copy for every mob and item).
            for (Entity e : w.getEntitiesByClasses(Villager.class, Mannequin.class, TextDisplay.class, Interaction.class)) {
                if (e.getScoreboardTags().contains(TAG)) {
                    e.remove();
                    removed++;
                }
            }
        }
        if (removed > 0) {
            module.core().plugin().getLogger().info("[npcs] removed " + removed + " left-over NPC/hologram entities");
        }
    }

    // ------------------------------------------------------------------ chunk events

    /** The entity kinds an NPC or hologram is built from (body, label, click box). */
    static boolean npcKind(Entity e) {
        return e instanceof Villager || e instanceof Mannequin || e instanceof TextDisplay || e instanceof Interaction;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity e : event.getEntities()) {
            // PERF2 (P-24): only the entity kinds NPCs are made of can carry the tag; skip the tag-set copy for the rest.
            if (npcKind(e) && e.getScoreboardTags().contains(TAG) && !owned(e)) {
                e.remove(); // never saved, but a crash or a failed reload must not leave doubles behind
            }
        }
        Map<Long, List<Placed>> chunks = byChunk.get(event.getWorld().getName());
        if (chunks == null) {
            return;
        }
        Chunk chunk = event.getChunk();
        List<Placed> here = chunks.get(Placed.chunkKey(chunk.getX(), chunk.getZ()));
        if (here == null) {
            return;
        }
        // Spawned from the next tick on (outside the chunk-load event, a few per tick), only if the chunk is still there.
        for (Placed p : here) {
            if (!p.spawned()) {
                queue.add(p);
            }
        }
        drainLater();
    }

    // ------------------------------------------------------------------ spawn queue (PERF3)

    /** Number of sets waiting to be spawned. */
    int waiting() {
        return queue.size();
    }

    /** Starts the per-tick spawn task (from the next tick) unless it runs or nothing waits. */
    private void drainLater() {
        if (spawnTask == null && (purgePending || !queue.isEmpty() || !retiring.isEmpty())) {
            spawnTask = module.core().scheduler().repeat("npcs:spawn", 1, 1, this::drain);
        }
    }

    /** One tick of spawning; stops the task when the queue is empty. */
    private void drain() {
        NpcsConfig c = module.config();
        switch (step(purgePending, retiring.isEmpty(), queue.isEmpty())) {
            case PURGE -> {
                purgePending = false;
                long purgeStart = System.nanoTime();
                purgeLeftovers();
                module.core().scheduler().timer().record("npcs:purge", System.nanoTime() - purgeStart);
            }
            // PERF5: the previous placement goes first; new sets spawn once it is gone.
            case RETIRE -> retiring.drain(c.removalsPerTick, c.spawnBudgetMs * 1_000_000L, System::nanoTime, Placer::removeSet);
            case SPAWN -> queue.drain(c.spawnsPerTick, c.spawnBudgetMs * 1_000_000L, System::nanoTime, this::spawnIfWanted);
            case DONE -> { }
        }
        if (step(purgePending, retiring.isEmpty(), queue.isEmpty()) == Step.DONE && spawnTask != null) {
            spawnTask.cancel();
            spawnTask = null;
        }
    }

    /** Spawns a queued set if it is still tracked, not spawned and its chunk's entities are still loaded. */
    private boolean spawnIfWanted(Placed p) {
        if (byKey.get(p.key()) != p || p.spawned()) {
            return false;
        }
        World w = loaded(p);
        if (w == null) {
            return false;
        }
        spawn(p, w);
        return true;
    }

    /** The set's world when its chunk is loaded with entities (never loads a chunk), else null. */
    private static World loaded(Placed p) {
        World w = Bukkit.getWorld(p.world);
        return w != null && w.isChunkLoaded(p.chunkX, p.chunkZ) && w.getChunkAt(p.chunkX, p.chunkZ).isEntitiesLoaded() ? w : null;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesUnload(EntitiesUnloadEvent event) {
        Map<Long, List<Placed>> chunks = byChunk.get(event.getWorld().getName());
        if (chunks == null) {
            return;
        }
        Chunk chunk = event.getChunk();
        List<Placed> here = chunks.get(Placed.chunkKey(chunk.getX(), chunk.getZ()));
        if (here != null) {
            here.forEach(Placed::remove);
        }
    }

    /** True for an entity of a set we placed in this run. */
    private boolean owned(Entity e) {
        Placed p = find(e);
        return p != null && (e.equals(p.body) || e.equals(p.label) || e.equals(p.click));
    }

    // ------------------------------------------------------------------ lookups

    /** The placed set an entity belongs to (by its tags), or null. */
    Placed find(Entity e) {
        if (e == null || !e.getScoreboardTags().contains(TAG)) {
            return null;
        }
        String kind = e.getPersistentDataContainer().get(kindKey, PersistentDataType.STRING);
        String id = e.getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
        if (kind == null || id == null) {
            return null;
        }
        try {
            return byKey.get(Placed.key(Placed.Kind.valueOf(kind), id));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    Optional<Placed> get(Placed.Kind kind, String id) {
        return Optional.ofNullable(byKey.get(Placed.key(kind, id)));
    }

    Collection<Placed> all() {
        return byKey.values();
    }

    /**
     * M10-3: spawns the holograms with this builder id again (their text changed, e.g. a live text from
     * {@link net.skybornmc.skyblock.common.npc.HologramTexts}); only those whose chunk is loaded, over the next ticks.
     */
    void respawnHolograms(String pointId) {
        boolean queued = false;
        for (Placed p : byKey.values()) {
            if (p.kind == Placed.Kind.HOLOGRAM && p.pointId().equals(pointId)) {
                p.remove();
                if (loaded(p) != null) {
                    queued |= queue.add(p);
                }
            }
        }
        if (queued) {
            drainLater();
        }
    }

    /** Respawns the text of one set (after a leaderboard refresh); does nothing while unloaded. */
    void setText(Placed p, Component text) {
        if (p.label != null && p.label.isValid()) {
            p.label.text(text);
        }
    }

    // ------------------------------------------------------------------ spawning

    private void spawn(Placed p, World w) {
        p.remove();
        try {
            switch (p.kind) {
                case NPC -> spawnNpc(p, w);
                case HOLOGRAM, BOARD -> spawnText(p, w);
            }
        } catch (RuntimeException e) {
            p.remove();
            module.core().plugin().getLogger().log(Level.WARNING, "[npcs] could not spawn " + p.key() + " in " + w.getName(), e);
        }
    }

    private void spawnNpc(Placed p, World w) {
        NpcsConfig c = module.config();
        Spots.Npc n = p.npc;
        int bx = (int) Math.floor(n.x());
        int by = (int) Math.floor(n.y());
        int bz = (int) Math.floor(n.z());
        Block block = w.getBlockAt(bx, by, bz);
        // The metadata names the feet cell; some builders give the pad block itself: stand on top of it then.
        double y = block.getType().isSolid() ? by + 1 : n.y();
        Location at = new Location(w, Layout.centre(n.x()), y, Layout.centre(n.z()), n.yaw(), 0f);
        p.location = at.clone();
        p.lastYaw = n.yaw();
        p.lastPitch = 0f;
        p.lookingAway = true;
        if (c.body == NpcsConfig.Body.VILLAGER) {
            p.body = w.spawn(at, Villager.class, v -> {
                v.setAI(false);
                v.setAware(false);
                common(v, p);
            });
        } else {
            String skin = module.mapped(module.skins(), n.id(), n.role());
            Material held = module.heldItem(n.id(), n.role());
            p.body = w.spawn(at, Mannequin.class, m -> {
                m.setImmovable(true);
                try {
                    m.setDescription(null); // the name text above replaces the vanilla "NPC" line
                } catch (RuntimeException e) {
                    m.setDescription(Component.empty());
                }
                if (skin != null) {
                    m.setProfile(ResolvableProfile.resolvableProfile().name(skin).build());
                }
                if (held != null) {
                    m.getEquipment().setItemInMainHand(new ItemStack(held));
                }
                common(m, p);
            });
        }
        Location labelAt = at.clone().add(0, c.nameHeight, 0);
        labelAt.setYaw(0f);
        p.label = w.spawn(labelAt, TextDisplay.class, d -> {
            d.text(module.npcLabel(n));
            d.setBillboard(Display.Billboard.CENTER);
            d.setViewRange((float) c.nameViewRange);
            d.setAlignment(TextDisplay.TextAlignment.CENTER);
            d.setShadowed(true);
            background(d, c.backgroundAlpha);
            tag(d, p);
        });
    }

    private void common(LivingEntity e, Placed p) {
        e.setPersistent(false);
        e.setRemoveWhenFarAway(false);
        e.setInvulnerable(true);
        e.setSilent(true);
        e.setGravity(false);
        e.setCollidable(false);
        e.setCanPickupItems(false);
        e.setCustomNameVisible(false);
        tag(e, p);
    }

    private void spawnText(Placed p, World w) {
        NpcsConfig c = module.config();
        Spots.Text t = p.text;
        NpcsModule.TextContent content = p.kind == Placed.Kind.BOARD ? module.boardContent(t) : module.hologramContent(t);
        if (content == null) {
            return; // no text configured: nothing to show
        }
        float scale = Layout.scale(content.lines(), t.height(), c.lineHeight, c.fill, c.minScale, c.maxScale);
        double boardBottom = Layout.bottomY(t.y(), t.height());
        double textHeight = content.lines() * c.lineHeight * scale;
        double x = Layout.centre(t.x());
        double z = Layout.centre(t.z());
        float yaw = t.faced() ? (float) (t.yaw() + c.yawOffset) : 0f;
        if (t.faced()) {
            double[] dir = Layout.direction(t.yaw());
            x -= dir[0] * c.wallOffset;
            z -= dir[1] * c.wallOffset;
        }
        Location at = new Location(w, x, Layout.textBottom(boardBottom, t.height(), textHeight), z, yaw, 0f);
        p.location = at.clone();
        p.label = w.spawn(at, TextDisplay.class, d -> {
            d.text(content.text());
            d.setBillboard(t.faced() ? Display.Billboard.FIXED : Display.Billboard.CENTER);
            d.setViewRange((float) c.viewRange);
            d.setLineWidth(c.lineWidth);
            d.setAlignment(TextDisplay.TextAlignment.CENTER);
            d.setShadowed(true);
            background(d, c.backgroundAlpha);
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(scale, scale, scale), new AxisAngle4f()));
            d.setPersistent(false);
            tag(d, p);
        });
        if (c.clickable && module.hasHelp(p)) {
            float width = (float) Math.min(c.clickMaxWidth, t.width());
            Location clickAt = new Location(w, x, boardBottom, z);
            p.click = w.spawn(clickAt, Interaction.class, i -> {
                i.setInteractionWidth(width);
                i.setInteractionHeight(t.height());
                i.setResponsive(true);
                i.setPersistent(false);
                tag(i, p);
            });
        }
    }

    private static void background(TextDisplay d, int alpha) {
        d.setDefaultBackground(false);
        d.setBackgroundColor(Color.fromARGB(Math.max(0, Math.min(255, alpha)), 0, 0, 0));
    }

    private void tag(Entity e, Placed p) {
        e.setPersistent(false);
        e.addScoreboardTag(TAG);
        e.getPersistentDataContainer().set(kindKey, PersistentDataType.STRING, p.kind.name());
        e.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, p.id);
    }

    // ------------------------------------------------------------------ looking at players

    /** Turns each spawned NPC's head to the nearest player in range, or back to its pad facing. */
    private void look() {
        NpcsConfig c = module.config();
        double r = c.lookRadius;
        // PERF4: one pass over each world's players into a grid instead of a nearby-players query per NPC.
        Map<World, NearestGrid<Player>> grids = new HashMap<>();
        for (Placed p : byKey.values()) {
            if (p.kind != Placed.Kind.NPC || p.body == null || !p.body.isValid()) {
                continue;
            }
            Location at = p.location;
            World world = at.getWorld();
            NearestGrid<Player> grid = grids.computeIfAbsent(world, w -> playersOf(w, r));
            double ex = at.getX();
            double ey = at.getY() + EYE;
            double ez = at.getZ();
            Player nearest = grid.nearest(ex, ey, ez, r);
            float yaw;
            float pitch;
            if (nearest == null) {
                if (p.lookingAway) {
                    continue;
                }
                yaw = p.npc.yaw();
                pitch = 0f;
                p.lookingAway = true;
            } else {
                Location target = nearest.getEyeLocation();
                float[] yp = Layout.lookAt(target.getX() - ex, target.getY() - ey, target.getZ() - ez);
                yaw = yp[0];
                pitch = yp[1];
                p.lookingAway = false;
                if (Layout.angleDiff(yaw, p.lastYaw) < 3f && Math.abs(pitch - p.lastPitch) < 3f) {
                    continue; // nothing visible changed: no packet
                }
            }
            p.lastYaw = yaw;
            p.lastPitch = pitch;
            p.body.setRotation(yaw, pitch);
            p.body.setBodyYaw(yaw);
        }
    }

    /** The non-spectator players of a world by eye position (empty grid for a world nobody is in). */
    private static NearestGrid<Player> playersOf(World world, double radius) {
        NearestGrid<Player> grid = new NearestGrid<>(radius);
        if (world == null) {
            return grid;
        }
        for (Player player : world.getPlayers()) {
            if (player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            Location loc = player.getLocation();
            grid.add(player, loc.getX(), loc.getY() + player.getEyeHeight(), loc.getZ());
        }
        return grid;
    }
}
