package net.skybornmc.skyblock.depths.combat;

import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Scheduler;
import net.skybornmc.skyblock.depths.DepthsConfig;
import net.skybornmc.skyblock.depths.zones.Aspect;
import net.skybornmc.skyblock.depths.zones.Field;
import net.skybornmc.skyblock.depths.zones.Zone;
import net.skybornmc.skyblock.depths.zones.ZoneService;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

/**
 * Per-player ambient mobs in mine and forest zones (M4.8, REVIEW B6): a few harmless mobs from {@code mobs.yml →
 * ambient} around each player, seen only by that player (hidden from everyone else from the first packet). They never
 * target or hurt anyone, give nothing when killed and are removed when their player leaves the zone, walks away or logs
 * out. One batched cycle over the Depths world's players; a global cap bounds the entity count.
 */
public final class AmbientMobs implements Listener {

    /** One ambient mob and the player it belongs to. */
    record Owned(UUID owner, LivingEntity entity, String zoneId) {
    }

    private final Core core;
    private final DepthsConfig config;
    private final ModuleMessages msg;
    private final ZoneService zones;
    private final MobFactory factory;
    private final Map<UUID, Owned> byEntity = new HashMap<>();
    private final Map<UUID, List<UUID>> byPlayer = new HashMap<>();
    private volatile MobCatalog catalog = MobCatalog.EMPTY;
    private Scheduler.Task task;

    public AmbientMobs(Core core, DepthsConfig config, ModuleMessages msg, ZoneService zones, MobFactory factory) {
        this.core = core;
        this.config = config;
        this.msg = msg;
        this.zones = zones;
        this.factory = factory;
    }

    public void catalog(MobCatalog catalog) {
        this.catalog = catalog == null ? MobCatalog.EMPTY : catalog;
    }

    public void start() {
        if (task != null) {
            task.cancel();
        }
        long ticks = Math.max(20, config.ambientCycleTicks);
        task = core.scheduler().repeat("depths-ambient", ticks, ticks, this::cycle);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        clearAll();
    }

    /** True if the entity is one of the ambient mobs (not an orphan). */
    public boolean owns(UUID entity) {
        return byEntity.containsKey(entity);
    }

    public int alive() {
        return byEntity.size();
    }

    public int aliveFor(UUID player) {
        List<UUID> list = byPlayer.get(player);
        return list == null ? 0 : list.size();
    }

    void cycle() {
        if (!config.ambientEnabled || config.ambientPerPlayer <= 0) {
            if (!byEntity.isEmpty()) {
                clearAll();
            }
            return;
        }
        World world = Bukkit.getWorld(zones.world());
        Set<UUID> seen = new HashSet<>();
        RandomGenerator rng = ThreadLocalRandom.current();
        double despawnSq = (double) config.ambientDespawnDistance * config.ambientDespawnDistance;
        MobCatalog cat = catalog;
        if (world != null) {
            for (Player player : world.getPlayers()) {
                seen.add(player.getUniqueId());
                Zone zone = player.getGameMode() == GameMode.SPECTATOR ? null : zones.zoneAt(player.getLocation());
                AmbientSpawns spawns = zone == null || zone.aspect() == Aspect.COMBAT ? null
                        : cat.ambient(zone.aspect().id(), zone.tier());
                List<UUID> mine = byPlayer.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>(4));
                Location at = player.getLocation();
                for (Iterator<UUID> it = mine.iterator(); it.hasNext(); ) {
                    Owned o = byEntity.get(it.next());
                    boolean keep = o != null && spawns != null && o.entity().isValid() && o.zoneId().equals(zone.id())
                            && o.entity().getWorld() == world && o.entity().getLocation().distanceSquared(at) <= despawnSq;
                    if (!keep) {
                        it.remove();
                        if (o != null) {
                            byEntity.remove(o.entity().getUniqueId());
                            if (o.entity().isValid()) {
                                o.entity().remove();
                            }
                        }
                    }
                }
                if (spawns == null) {
                    continue;
                }
                int count = SpawnMath.ambientToSpawn(config.ambientPerPlayer, mine.size(), config.ambientMaxTotal - byEntity.size());
                for (int i = 0; i < count; i++) {
                    MobDef def = cat.mob(SpawnMath.pick(spawns.mobs(), rng));
                    Location spot = def == null ? null : spot(world, zone, at, rng);
                    if (spot == null) {
                        break;
                    }
                    LivingEntity e = factory.spawn(spot, def, name(def), player, config.ambientSilent);
                    if (e != null) {
                        byEntity.put(e.getUniqueId(), new Owned(player.getUniqueId(), e, zone.id()));
                        mine.add(e.getUniqueId());
                    }
                }
            }
        }
        for (UUID player : new ArrayList<>(byPlayer.keySet())) {
            if (!seen.contains(player)) {
                removeFor(player);
            }
        }
    }

    private Component name(MobDef def) {
        String key = "mobs." + def.id();
        return msg.has(key) ? msg.component(null, key) : null;
    }

    /** A spot near the player inside the zone and outside its ore/log fields (so they never block mining). */
    private Location spot(World world, Zone zone, Location around, RandomGenerator rng) {
        int min = Math.min(config.ambientRadiusMin, config.ambientRadiusMax);
        int max = Math.max(config.ambientRadiusMin, config.ambientRadiusMax);
        for (int attempt = 0; attempt < 6; attempt++) {
            double angle = rng.nextDouble() * Math.PI * 2;
            double r = min + rng.nextDouble() * Math.max(0, max - min);
            int x = (int) Math.floor(around.getX() + Math.cos(angle) * r);
            int z = (int) Math.floor(around.getZ() + Math.sin(angle) * r);
            if (!zone.region().containsColumn(x, z) || inField(zone, x, z) || !world.isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }
            int high = Math.min(zone.region().maxY(), around.getBlockY() + 4);
            int low = Math.max(zone.region().minY() + 1, around.getBlockY() - 4);
            int y = CombatZones.standY(world, x, z, high, low);
            if (y != Integer.MIN_VALUE) {
                return new Location(world, x + 0.5, y, z + 0.5, rng.nextFloat() * 360f, 0f);
            }
        }
        return null;
    }

    private static boolean inField(Zone zone, int x, int z) {
        for (Field f : zone.fields()) {
            if (f.box().growHorizontal(1).containsColumn(x, z)) {
                return true;
            }
        }
        return false;
    }

    private void removeFor(UUID player) {
        List<UUID> list = byPlayer.remove(player);
        if (list == null) {
            return;
        }
        for (UUID id : list) {
            Owned o = byEntity.remove(id);
            if (o != null && o.entity().isValid()) {
                o.entity().remove();
            }
        }
    }

    private void clearAll() {
        for (Owned o : byEntity.values()) {
            if (o.entity().isValid()) {
                o.entity().remove();
            }
        }
        byEntity.clear();
        byPlayer.clear();
    }

    // ------------------------------------------------------------------ harmless

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTarget(EntityTargetEvent event) {
        if (byEntity.containsKey(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /** Ambient mobs never hurt anyone (melee, projectiles, slime contact). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Entity from = CombatZones.attacker(event.getDamager());
        if (byEntity.containsKey(from.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /** Killing one gives nothing (no drops, no XP: the skills module skips tagged mobs). */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(EntityDeathEvent event) {
        Owned o = byEntity.remove(event.getEntity().getUniqueId());
        if (o == null) {
            return;
        }
        event.getDrops().clear();
        event.setDroppedExp(0);
        List<UUID> list = byPlayer.get(o.owner());
        if (list != null) {
            list.remove(event.getEntity().getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        removeFor(event.getPlayer().getUniqueId());
    }

    /** {@code /depths debug}: ambient numbers. */
    public void debug(org.bukkit.command.CommandSender viewer, Player target) {
        msg.send(viewer, "debug.ambient", Msg.p("alive", byEntity.size()), Msg.p("max", config.ambientMaxTotal),
                Msg.p("player", target.getName()), Msg.p("own", aliveFor(target.getUniqueId())));
    }
}
