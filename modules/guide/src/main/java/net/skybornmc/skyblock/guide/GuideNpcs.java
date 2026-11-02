package net.skybornmc.skyblock.guide;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.island.IslandService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The guide entities: one Mannequin (plus a two-line name/status text) per island, spawned only while a player stands on
 * that island and the chunk is loaded; never saved (not persistent), removed when the chunk unloads and on disable.
 * Left-overs from a crash carry the scoreboard tag {@link #TAG} and are removed when their chunk loads.
 */
final class GuideNpcs implements Listener {

    static final String TAG = "phoenix_guide";

    /** One spawned guide. */
    private static final class Placed {
        Mannequin body;
        TextDisplay label;
        boolean news;
    }

    private final GuideModule m;
    private final Map<UUID, Placed> placed = new HashMap<>();
    /** Moved spots (loaded at start, changed by /is guide move). */
    private final Map<UUID, GuideStore.Spot> spots = new ConcurrentHashMap<>();
    /** Islands whose guide has something new (an island level-up nobody looked at yet). Memory only. */
    private final Set<UUID> news = ConcurrentHashMap.newKeySet();

    GuideNpcs(GuideModule m) {
        this.m = m;
    }

    void spots(Map<UUID, GuideStore.Spot> loaded) {
        spots.putAll(loaded);
    }

    // ------------------------------------------------------------------ spots

    /** Where the island's guide stands: the moved spot, else next to the island home. */
    Optional<Location> spot(UUID island) {
        GuideStore.Spot s = spots.get(island);
        if (s == null) {
            Optional<Location> home = m.islands().flatMap(i -> i.home(island));
            if (home.isEmpty() || home.get().getWorld() == null) {
                return Optional.empty();
            }
            Location h = home.get();
            GuideConfig c = m.config();
            s = GuideRules.defaultSpot(h.getWorld().getName(), h.getX(), h.getY(), h.getZ(), c.offsetX, c.offsetY, c.offsetZ);
        }
        World w = Bukkit.getWorld(s.world());
        return w == null ? Optional.empty() : Optional.of(new Location(w, s.x(), s.y(), s.z(), s.yaw(), 0f));
    }

    void move(UUID island, GuideStore.Spot spot) {
        spots.put(island, spot);
        despawn(island);
    }

    void forget(UUID island) {
        spots.remove(island);
        news.remove(island);
        despawn(island);
    }

    // ------------------------------------------------------------------ news

    void markNews(UUID island) {
        news.add(island);
        Placed p = placed.get(island);
        if (p != null) {
            label(island, p);
        }
    }

    void clearNews(UUID island) {
        if (news.remove(island)) {
            Placed p = placed.get(island);
            if (p != null) {
                label(island, p);
            }
        }
    }

    boolean hasNews(UUID island) {
        return news.contains(island);
    }

    // ------------------------------------------------------------------ spawning

    /** Every few seconds: guides of islands players stand on are spawned (loaded chunks only); dead ones forgotten. */
    void sweep() {
        placed.entrySet().removeIf(e -> {
            Placed p = e.getValue();
            boolean gone = p.body == null || !p.body.isValid();
            if (gone) {
                remove(p);
            }
            return gone;
        });
        if (!m.config().enabled) {
            return;
        }
        IslandService islands = m.islands().orElse(null);
        if (islands == null) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!islands.isIslandWorld(player.getWorld())) {
                continue;
            }
            islands.islandAt(player.getLocation()).ifPresent(info -> {
                if (!placed.containsKey(info.id())) {
                    spot(info.id()).ifPresent(at -> spawn(info.id(), at));
                }
            });
        }
    }

    /** Turns guides toward the nearest player within 6 blocks (cheap: a handful of entities). */
    void look() {
        if (!m.config().lookAtPlayers) {
            return;
        }
        for (Placed p : placed.values()) {
            if (p.body == null || !p.body.isValid()) {
                continue;
            }
            Location at = p.body.getLocation();
            Player nearest = null;
            double best = 36;
            for (Player pl : at.getWorld().getPlayers()) {
                double d = pl.getLocation().distanceSquared(at);
                if (d < best) {
                    best = d;
                    nearest = pl;
                }
            }
            if (nearest != null) {
                Location eye = nearest.getEyeLocation();
                float yaw = GuideRules.yawToward(at.getX(), at.getZ(), eye.getX(), eye.getZ());
                p.body.setRotation(yaw, 0f);
            }
        }
    }

    private void spawn(UUID island, Location at) {
        World w = at.getWorld();
        if (w == null || !w.isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)) {
            return;
        }
        GuideConfig c = m.config();
        Placed p = new Placed();
        p.body = w.spawn(at, Mannequin.class, body -> {
            body.setImmovable(true);
            try {
                body.setDescription(null);
            } catch (RuntimeException e) {
                body.setDescription(Component.empty());
            }
            if (c.skin != null && !c.skin.isBlank()) {
                body.setProfile(ResolvableProfile.resolvableProfile().name(c.skin.trim()).build());
            }
            Material held = c.heldItem == null || c.heldItem.isBlank() ? null : Material.matchMaterial(c.heldItem.trim());
            if (held != null && held.isItem()) {
                body.getEquipment().setItemInMainHand(new ItemStack(held));
            }
            body.setPersistent(false);
            body.setRemoveWhenFarAway(false);
            body.setInvulnerable(true);
            body.setSilent(true);
            body.setGravity(false);
            body.setCollidable(false);
            body.setCanPickupItems(false);
            body.setCustomNameVisible(false);
            body.addScoreboardTag(TAG);
            body.addScoreboardTag(TAG + ":" + island);
        });
        Location labelAt = at.clone().add(0, c.labelHeight, 0);
        labelAt.setYaw(0f);
        p.label = w.spawn(labelAt, TextDisplay.class, d -> {
            d.setBillboard(Display.Billboard.CENTER);
            d.setViewRange((float) c.viewRange);
            d.setAlignment(TextDisplay.TextAlignment.CENTER);
            d.setShadowed(true);
            d.setPersistent(false);
            d.addScoreboardTag(TAG);
        });
        placed.put(island, p);
        label(island, p);
    }

    private void label(UUID island, Placed p) {
        if (p.label == null || !p.label.isValid()) {
            return;
        }
        boolean star = news.contains(island);
        p.news = star;
        Component text = m.msg().component(net.kyori.adventure.audience.Audience.empty(), "npc.title").append(Component.newline())
                .append(m.msg().component(net.kyori.adventure.audience.Audience.empty(), "npc.name")).append(Component.newline())
                .append(m.msg().component(net.kyori.adventure.audience.Audience.empty(), star ? "npc.status-news" : "npc.status"));
        p.label.text(text);
    }

    void despawn(UUID island) {
        Placed p = placed.remove(island);
        if (p != null) {
            remove(p);
        }
    }

    void despawnAll() {
        for (Placed p : placed.values()) {
            remove(p);
        }
        placed.clear();
    }

    private static void remove(Placed p) {
        if (p.body != null) {
            p.body.remove();
        }
        if (p.label != null) {
            p.label.remove();
        }
    }

    /** The island a guide entity belongs to, or null when the entity is no guide. */
    UUID islandOf(Entity e) {
        if (!(e instanceof Mannequin) || !e.getScoreboardTags().contains(TAG)) {
            return null;
        }
        for (Map.Entry<UUID, Placed> en : placed.entrySet()) {
            if (en.getValue().body == e) {
                return en.getKey();
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ events

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity e : event.getEntities()) {
            if ((e instanceof Mannequin || e instanceof TextDisplay) && e.getScoreboardTags().contains(TAG) && !ours(e)) {
                e.remove(); // never saved, but a crash must not leave doubles
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesUnload(EntitiesUnloadEvent event) {
        for (Iterator<Map.Entry<UUID, Placed>> it = placed.entrySet().iterator(); it.hasNext(); ) {
            Placed p = it.next().getValue();
            if (event.getEntities().contains(p.body) || event.getEntities().contains(p.label)) {
                remove(p);
                it.remove();
            }
        }
    }

    private boolean ours(Entity e) {
        for (Placed p : placed.values()) {
            if (p.body == e || p.label == e) {
                return true;
            }
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInteract(PlayerInteractEntityEvent event) {
        UUID island = islandOf(event.getRightClicked());
        if (island == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() == EquipmentSlot.HAND) {
            m.talk(event.getPlayer(), island);
        }
    }

    /** Bedrock and Java left clicks count as a click too (one click does one thing). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onHit(EntityDamageByEntityEvent event) {
        UUID island = islandOf(event.getEntity());
        if (island == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getDamager() instanceof Player player) {
            m.talk(player, island);
        }
    }

}
