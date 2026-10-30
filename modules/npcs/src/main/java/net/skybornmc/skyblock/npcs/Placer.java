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

    Placer(NpcsModule module) {
        this.module = module;
        this.kindKey = new NamespacedKey(module.core().plugin(), "npcs_kind");
        this.idKey = new NamespacedKey(module.core().plugin(), "npcs_id");
    }

    // ------------------------------------------------------------------ lifecycle

    /** Removes every placed set and left-over, then places {@code spots} in the loaded chunks. */
    void start(Spots spots) {
        stop();
        purgeLeftovers();
        NpcsConfig c = module.config();
        if (c.npcsEnabled) {
            spots.npcs().forEach(n -> track(new Placed(Placed.Kind.NPC, n, null)));
        }
        if (c.hologramsEnabled) {
            spots.holograms().forEach(t -> track(new Placed(Placed.Kind.HOLOGRAM, null, t)));
        }
        if (c.leaderboardsEnabled) {
            spots.boards().forEach(t -> track(new Placed(Placed.Kind.BOARD, null, t)));
        }
        for (Placed p : byKey.values()) {
            World w = Bukkit.getWorld(p.world);
            if (w != null && w.isChunkLoaded(p.chunkX, p.chunkZ) && w.getChunkAt(p.chunkX, p.chunkZ).isEntitiesLoaded()) {
                spawn(p, w);
            }
        }
        if (c.npcsEnabled && c.lookAtPlayers && !spots.npcs().isEmpty()) {
            lookTask = module.core().scheduler().repeat("npcs:look", c.lookTicks, c.lookTicks, this::look);
        }
    }

    /** Removes every entity of this module and forgets the points. */
    void stop() {
        if (lookTask != null) {
            lookTask.cancel();
            lookTask = null;
        }
        byKey.values().forEach(Placed::remove);
        byKey.clear();
        byChunk.clear();
    }

    private void track(Placed p) {
        byKey.put(p.key(), p);
        byChunk.computeIfAbsent(p.world, w -> new HashMap<>()).computeIfAbsent(p.chunkKey(), k -> new ArrayList<>()).add(p);
    }

    /** Removes tagged entities from loaded chunks (left from an earlier enable). */
    private void purgeLeftovers() {
        int removed = 0;
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) {
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
        // Spawned one tick later, outside the chunk-load event, and only if the chunk is still there.
        World world = event.getWorld();
        int cx = chunk.getX();
        int cz = chunk.getZ();
        module.core().scheduler().later("npcs:spawn", 1, () -> {
            if (!world.isChunkLoaded(cx, cz) || !world.getChunkAt(cx, cz).isEntitiesLoaded()) {
                return;
            }
            for (Placed p : here) {
                if (!p.spawned() && byKey.get(p.key()) == p) {
                    spawn(p, world);
                }
            }
        });
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
        for (Placed p : byKey.values()) {
            if (p.kind != Placed.Kind.NPC || p.body == null || !p.body.isValid()) {
                continue;
            }
            Location eye = p.location.clone().add(0, EYE, 0);
            Player nearest = null;
            double best = r * r;
            for (Player player : eye.getWorld().getNearbyPlayers(eye, r)) {
                if (player.getGameMode() == GameMode.SPECTATOR) {
                    continue;
                }
                double d = player.getEyeLocation().distanceSquared(eye);
                if (d <= best) {
                    best = d;
                    nearest = player;
                }
            }
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
                float[] yp = Layout.lookAt(target.getX() - eye.getX(), target.getY() - eye.getY(), target.getZ() - eye.getZ());
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
}
