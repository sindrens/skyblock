package net.skybornmc.skyblock.depths.combat;

import net.skybornmc.skyblock.common.mob.AbilityId;
import net.skybornmc.skyblock.common.mob.AbilitySpec;
import net.skybornmc.skyblock.common.mob.EliteRules;
import net.skybornmc.skyblock.common.mob.MobCombatMath;
import net.skybornmc.skyblock.common.mob.MobDef;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.region.RegionFlag;
import net.skybornmc.skyblock.common.region.RegionFlags;
import net.skybornmc.skyblock.common.bonus.BonusContext;
import net.skybornmc.skyblock.common.bonus.BonusFamily;
import net.skybornmc.skyblock.common.bonus.BonusService;
import net.skybornmc.skyblock.common.profile.ProfileService;
import net.skybornmc.skyblock.common.skills.SkillService;
import net.skybornmc.skyblock.common.skills.SkillType;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.tools.ToolService;
import net.skybornmc.skyblock.common.util.Scheduler;
import net.skybornmc.skyblock.depths.DepthsConfig;
import net.skybornmc.skyblock.depths.drops.DepthsDrops;
import net.skybornmc.skyblock.depths.satchel.SatchelManager;
import net.skybornmc.skyblock.depths.zones.Aspect;
import net.skybornmc.skyblock.depths.zones.Box;
import net.skybornmc.skyblock.depths.zones.Zone;
import net.skybornmc.skyblock.depths.zones.ZoneService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EvokerFangs;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.SlimeSplitEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;

/**
 * Combat zones (M4.8): shared mobs per combat tier from {@code mobs.yml}, visible to everyone (REVIEW B6). One timer
 * handles every zone in a batched pass: count the players of the Depths world per zone (O(players there)), drop dead or
 * unloaded mobs, put strays back, top each zone up to its cap (a few per cycle, only in loaded chunks, never next to a
 * player) and remove the mobs of zones that stayed empty. No per-tick work and no scans of the world's entities; every
 * mob is tracked by UUID. Kills pay Combat XP, satchel drops and crystals (batched with the mine's crystals); mini-boss
 * placeholders spawn on a timer with a boss bar and reward everyone who did enough damage (by profile key).
 */
public final class CombatZones implements Listener {

    /** Spawn reasons still allowed in the Depths world with {@code combat.block-other-spawns}. */
    static final Set<CreatureSpawnEvent.SpawnReason> ALLOWED_REASONS = EnumSet.of(CreatureSpawnEvent.SpawnReason.CUSTOM,
            CreatureSpawnEvent.SpawnReason.COMMAND, CreatureSpawnEvent.SpawnReason.SPAWNER_EGG);
    /** Ignite causes refused in the Depths world (mob fireballs, lightning, spread); builders' flint and steel still work. */
    static final Set<BlockIgniteEvent.IgniteCause> BLOCKED_IGNITES = EnumSet.of(BlockIgniteEvent.IgniteCause.FIREBALL,
            BlockIgniteEvent.IgniteCause.LIGHTNING, BlockIgniteEvent.IgniteCause.SPREAD, BlockIgniteEvent.IgniteCause.EXPLOSION,
            BlockIgniteEvent.IgniteCause.LAVA);
    /** Skill XP source of Depths kills. */
    public static final String XP_SOURCE = "depths_mob";

    /** One live combat mob. */
    record Tracked(LivingEntity entity, MobDef def, String zoneId, int tier, boolean boss, BossFight fight, boolean elite) {
    }

    /** Runtime state of one combat zone. */
    static final class ZoneState {
        final String zoneId;
        final Set<UUID> mobs = new LinkedHashSet<>();
        MiniBossClock clock;
        int clockMinutes = -1;
        UUID boss;
        BossBar bar;
        final Set<UUID> barViewers = new HashSet<>();
        long emptySince;
        int players;

        ZoneState(String zoneId) {
            this.zoneId = zoneId;
        }
    }

    private final Core core;
    private final DepthsConfig config;
    private final ModuleMessages msg;
    private final ZoneService zones;
    private final SatchelManager satchel;
    private final DepthsDrops drops;
    private final MobFactory factory;
    private final Map<UUID, Tracked> tracked = new HashMap<>();
    private final Map<String, ZoneState> states = new HashMap<>();
    private volatile MobCatalog catalog = MobCatalog.EMPTY;
    private Predicate<UUID> otherOwned = id -> false;
    private Scheduler.Task task;
    /** QUICK1 (P-10): standing heights per arena column, so spawn spots don't rescan columns every cycle. */
    private final SpawnColumns columns = new SpawnColumns();
    /** QUICK1: parsed mob names (message parsing once per mob kind, not per spawn); cleared on start/reload. */
    private final Map<String, Component> names = new HashMap<>();
    /** M8.1: players per combat zone from the last cycle (abilities only look here, never at the whole world). */
    private Map<String, List<Player>> present = Map.of();
    /** M8.1: mob abilities (one shared scheduler). */
    private final MobAbilities abilities;

    public CombatZones(Core core, DepthsConfig config, ModuleMessages msg, ZoneService zones, SatchelManager satchel,
                       DepthsDrops drops, MobFactory factory) {
        this.core = core;
        this.config = config;
        this.msg = msg;
        this.zones = zones;
        this.satchel = satchel;
        this.drops = drops;
        this.factory = factory;
        this.abilities = new MobAbilities(core, config, msg, new AbilityZones());
    }

    public void catalog(MobCatalog catalog) {
        this.catalog = catalog == null ? MobCatalog.EMPTY : catalog;
    }

    /** Entities tracked elsewhere (ambient mobs) are not orphans. */
    public void otherOwned(Predicate<UUID> owned) {
        this.otherOwned = owned == null ? id -> false : owned;
    }

    public void start() {
        if (task != null) {
            task.cancel();
        }
        columns.clear();
        names.clear();
        long ticks = Math.max(10, config.combatCycleTicks);
        task = core.scheduler().repeat("depths-combat", ticks, ticks, this::cycle);
        abilities.start();
        // A reload keeps the live mobs: they get their abilities again from the (maybe changed) catalog.
        EliteRules rules = eliteRules();
        for (Tracked t : tracked.values()) {
            if (t.entity().isValid()) {
                MobDef def = catalog.mob(t.def().id()) != null ? catalog.mob(t.def().id()) : t.def();
                abilities.track(t.entity(), def, t.zoneId(), abilitiesOf(def, t.tier(), t.boss(), t.elite(), rules));
            }
        }
        World world = Bukkit.getWorld(zones.world());
        if (world != null) {
            removeOrphans(world.getEntities());
        }
    }

    /** Removes every combat mob and boss bar (disable). */
    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        abilities.stop();
        clearAll();
    }

    /** Removes every combat mob and boss bar; the timer keeps running. */
    private void clearAll() {
        for (ZoneState state : new ArrayList<>(states.values())) {
            clear(state);
        }
        states.clear();
        abilities.reset();
        present = Map.of();
        for (Tracked t : tracked.values()) {
            if (t.entity().isValid()) {
                t.entity().remove();
            }
        }
        tracked.clear();
    }

    public int alive() {
        return tracked.size();
    }

    /** Lookup for the drop/damage listeners: is this entity a live combat mob? */
    boolean isTracked(UUID entity) {
        return tracked.containsKey(entity);
    }

    // ------------------------------------------------------------------ the cycle

    void cycle() {
        if (!config.combatEnabled) {
            if (!tracked.isEmpty() || !states.isEmpty()) {
                clearAll();
            }
            return;
        }
        World world = Bukkit.getWorld(zones.world());
        if (world == null) {
            return;
        }
        long now = System.currentTimeMillis();
        // PERF8: each part is measured over the whole cycle and recorded under its own name (nested inside depths-combat),
        // so a slow cycle names the part: -players (who stands where), -prune (mob checks), -spawn, -bars.
        long partStart = System.nanoTime();
        long pruneNanos = 0;
        long spawnNanos = 0;
        long barNanos = 0;
        Map<String, List<Player>> present = new HashMap<>();
        for (Player p : world.getPlayers()) {
            if (p.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                continue;
            }
            Zone zone = zones.zoneAt(p.getLocation());
            if (zone != null && zone.aspect() == Aspect.COMBAT) {
                present.computeIfAbsent(zone.id(), k -> new ArrayList<>(4)).add(p);
            }
        }
        this.present = present;
        core.scheduler().timer().record("depths-combat-players", System.nanoTime() - partStart);
        Set<String> live = new HashSet<>();
        RandomGenerator rng = ThreadLocalRandom.current();
        MobCatalog cat = catalog;
        RegionFlags flags = core.services().optional(RegionFlags.class).orElse(null);
        for (Zone zone : zones.zones(Aspect.COMBAT)) {
            ZoneSpawns spawns = cat.combat(zone.tier());
            if (spawns == null) {
                continue;
            }
            live.add(zone.id());
            ZoneState state = states.computeIfAbsent(zone.id(), ZoneState::new);
            if (state.clock == null || state.clockMinutes != spawns.miniBossMinutes()) {
                state.clock = new MiniBossClock(now, spawns.miniBossMinutes() * 60_000L);
                state.clockMinutes = spawns.miniBossMinutes();
            }
            long t0 = System.nanoTime();
            prune(state, zone, world, rng, flags);
            pruneNanos += System.nanoTime() - t0;
            List<Player> players = present.getOrDefault(zone.id(), List.of());
            state.players = players.size();
            if (players.isEmpty()) {
                if (state.emptySince == 0) {
                    state.emptySince = now;
                }
                if (SpawnMath.despawnDue(state.emptySince, now, config.combatDespawnAfterSeconds * 1000L)) {
                    clear(state);
                }
                long t1 = System.nanoTime();
                syncBar(state, players);
                barNanos += System.nanoTime() - t1;
                continue;
            }
            state.emptySince = 0;
            long t2 = System.nanoTime();
            int regular = state.mobs.size() - (state.boss == null ? 0 : 1);
            int room = config.combatMaxTotalMobs - tracked.size();
            int count = SpawnMath.toSpawn(SpawnMath.target(spawns, players.size()), regular, config.combatSpawnsPerCycle, room);
            for (int i = 0; i < count; i++) {
                MobDef def = cat.mob(SpawnMath.pick(spawns.mobs(), rng));
                Location at = def == null ? null : spot(world, zone, players, rng);
                if (at == null) {
                    break;
                }
                spawn(state, zone, def, at, false);
            }
            rescale(state, now);
            if (spawns.hasMiniBoss() && state.clock.due(now, true)) {
                MobDef boss = cat.mob(spawns.miniBoss());
                Location at = boss == null ? null : spot(world, zone, players, rng);
                if (at != null && spawn(state, zone, boss, at, true) != null) {
                    announce(state, zone, players);
                }
            }
            long t3 = System.nanoTime();
            spawnNanos += t3 - t2;
            syncBar(state, players);
            barNanos += System.nanoTime() - t3;
        }
        core.scheduler().timer().record("depths-combat-prune", pruneNanos);
        core.scheduler().timer().record("depths-combat-spawn", spawnNanos);
        core.scheduler().timer().record("depths-combat-bars", barNanos);
        // Zones removed from zones.yml / mobs.yml on a reload lose their mobs.
        for (Iterator<Map.Entry<String, ZoneState>> it = states.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, ZoneState> e = it.next();
            if (!live.contains(e.getKey())) {
                clear(e.getValue());
                it.remove();
            }
        }
    }

    /** Drops dead/unloaded mobs and puts mobs that left their zone back on a spawn spot. */
    private void prune(ZoneState state, Zone zone, World world, RandomGenerator rng, RegionFlags flags) {
        for (Iterator<UUID> it = state.mobs.iterator(); it.hasNext(); ) {
            UUID id = it.next();
            Tracked t = tracked.get(id);
            if (t == null || !t.entity().isValid() || t.entity().isDead()) {
                it.remove();
                tracked.remove(id);
                abilities.untrack(id);
                if (id.equals(state.boss)) {
                    bossGone(state);
                }
                continue;
            }
            Location l = t.entity().getLocation();
            // DEPTHSPROT (builders' hard rule): tier mobs never keep a target standing in a safe zone (flag mob-target),
            // and a mob that got into one goes back to a spawn spot.
            boolean inSafe = false;
            if (flags != null && t.entity() instanceof Mob mob) {
                if (mob.getTarget() instanceof Player target && flags.denied(target.getLocation(), RegionFlag.MOB_TARGET)) {
                    mob.setTarget(null);
                }
                inSafe = flags.denied(l, RegionFlag.MOB_TARGET);
            }
            if (!config.combatLeash && !inSafe) {
                continue;
            }
            if (inSafe || l.getWorld() != world || !zone.region().contains(l.getX(), l.getY(), l.getZ())) {
                Location back = spot(world, zone, List.of(), rng);
                if (back != null) {
                    t.entity().teleportAsync(back);
                } else {
                    it.remove();
                    tracked.remove(id);
                    abilities.untrack(id);
                    t.entity().remove();
                    if (id.equals(state.boss)) {
                        bossGone(state);
                    }
                }
            }
        }
    }

    private LivingEntity spawn(ZoneState state, Zone zone, MobDef def, Location at, boolean boss) {
        return spawn(state, zone, def, at, boss, true);
    }

    /**
     * @param mayBeElite false for summoned adds (they never roll elites)
     */
    private LivingEntity spawn(ZoneState state, Zone zone, MobDef def, Location at, boolean boss, boolean mayBeElite) {
        EliteRules rules = eliteRules();
        boolean elite = mayBeElite && rules.roll(def.stats().eliteChance(), zone.tier(), boss, ThreadLocalRandom.current());
        LivingEntity e = factory.spawn(at, def, name(def, boss, elite), null, false, elite ? rules.healthMultiplier() : 1.0, elite);
        if (e == null) {
            return null;
        }
        tracked.put(e.getUniqueId(), new Tracked(e, def, zone.id(), zone.tier(), boss, boss ? new BossFight(maxHealth(e)) : null,
                elite));
        abilities.track(e, def, zone.id(), abilitiesOf(def, zone.tier(), boss, elite, rules));
        state.mobs.add(e.getUniqueId());
        if (boss) {
            e.addScoreboardTag(net.skybornmc.skyblock.common.combat.BossMobs.TAG); // E2: Giant Slayer / Executioner see bosses
            state.boss = e.getUniqueId();
            state.clock.spawned();
        }
        return e;
    }

    private Component name(MobDef def, boolean boss) {
        String key = "mobs." + def.id();
        if (!msg.has(key)) {
            return null;
        }
        return names.computeIfAbsent((boss ? "boss:" : "mob:") + def.id(), k -> {
            Component name = msg.component(null, key);
            return boss ? msg.component(null, "combat.mini-boss.name", Msg.c("name", name)) : name;
        });
    }

    /** M8.1: elites get the star before their name (the vanilla name when the mob has none in the messages). */
    private Component name(MobDef def, boolean boss, boolean elite) {
        if (!elite) {
            return name(def, boss);
        }
        return names.computeIfAbsent("elite:" + def.id(), k -> {
            Component base = name(def, false);
            Component shown = base != null ? base : Component.translatable("entity.minecraft." + def.type());
            return msg.component(null, "combat.elite.name", Msg.c("name", shown));
        });
    }

    // ------------------------------------------------------------------ M8.1 elites and abilities

    /** The elite rules from depths.yml (cheap; read per spawn so a reload applies at once). */
    EliteRules eliteRules() {
        List<AbilityId> extra = new ArrayList<>();
        for (String id : config.eliteExtraAbilities) {
            AbilityId a = AbilityId.of(id);
            if (a != null) {
                extra.add(a);
            }
        }
        return new EliteRules(config.eliteHealthMultiplier, config.eliteDamageMultiplier, config.eliteDropMultiplier,
                config.eliteXpMultiplier, config.eliteMinTier, config.eliteBudgetNeutral, extra);
    }

    /**
     * The abilities a new mob uses: none while abilities are off; regular mobs only from {@code abilities.min-tier}
     * (tiers 1-3 stay safe); elites add their extra ability; unset values come from {@code ability-defaults}.
     */
    private List<AbilitySpec> abilitiesOf(MobDef def, int tier, boolean boss, boolean elite, EliteRules rules) {
        if (!config.abilitiesEnabled || (!boss && tier < config.abilitiesMinTier)) {
            return List.of();
        }
        List<AbilitySpec> own = rules.abilities(def.stats().abilities(), elite);
        if (own.isEmpty()) {
            return own;
        }
        MobCatalog cat = catalog;
        List<AbilitySpec> out = new ArrayList<>(own.size());
        for (AbilitySpec a : own) {
            out.add(cat.resolve(a));
        }
        return out;
    }

    /** M8.1 summon: one regular mob of the zone near a spot, inside the zone and the caps; never an elite or boss. */
    private LivingEntity spawnAdd(String zoneId, Location near) {
        ZoneState state = states.get(zoneId);
        Zone zone = zones.zone(zoneId).orElse(null);
        ZoneSpawns spawns = zone == null ? null : catalog.combat(zone.tier());
        if (state == null || spawns == null || near.getWorld() == null || tracked.size() >= config.combatMaxTotalMobs
                || state.mobs.size() >= spawns.max()) {
            return null;
        }
        RandomGenerator rng = ThreadLocalRandom.current();
        MobDef def = catalog.mob(SpawnMath.pick(spawns.mobs(), rng));
        if (def == null) {
            return null;
        }
        World world = near.getWorld();
        for (int attempt = 0; attempt < config.combatSpawnAttempts; attempt++) {
            int x = near.getBlockX() + rng.nextInt(7) - 3;
            int z = near.getBlockZ() + rng.nextInt(7) - 3;
            if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }
            int y = standY(world, x, z, near.getBlockY() + 2, near.getBlockY() - 3);
            if (y == Integer.MIN_VALUE || !zone.region().contains(x + 0.5, y, z + 0.5)) {
                continue;
            }
            return spawn(state, zone, def, new Location(world, x + 0.5, y, z + 0.5, rng.nextFloat() * 360f, 0f), false, false);
        }
        return null;
    }

    /** What the abilities see of the zones. */
    private final class AbilityZones implements MobAbilities.Zones {
        @Override
        public List<Player> players(String zoneId) {
            return present.getOrDefault(zoneId, List.of());
        }

        @Override
        public boolean inside(String zoneId, Location at) {
            Zone zone = zones.zone(zoneId).orElse(null);
            return zone != null && at.getWorld() != null && at.getWorld().getName().equals(zones.world())
                    && zone.region().contains(at.getX(), at.getY(), at.getZ());
        }

        @Override
        public LivingEntity spawnAdd(String zoneId, Location near) {
            return CombatZones.this.spawnAdd(zoneId, near);
        }
    }

    /** A free spot in the zone's arena (or the whole zone): solid floor, two free blocks, loaded chunk, away from players. */
    Location spot(World world, Zone zone, List<Player> players, RandomGenerator rng) {
        Box area = zone.arena() != null ? zone.arena() : zone.region();
        double minDistSq = (double) config.combatMinPlayerDistance * config.combatMinPlayerDistance;
        int zoneKey = java.util.Objects.hash(zone.id(), area);
        long now = System.currentTimeMillis();
        long life = config.combatSpawnColumnCacheSeconds * 1000L;
        for (int attempt = 0; attempt < config.combatSpawnAttempts; attempt++) {
            int x = area.minX() + 1 + rng.nextInt(Math.max(1, area.sizeX() - 2));
            int z = area.minZ() + 1 + rng.nextInt(Math.max(1, area.sizeZ() - 2));
            if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }
            int y = columns.height(zoneKey, x, z, now, life, () -> standY(world, x, z, area.maxY(), area.minY()));
            if (y == Integer.MIN_VALUE) {
                continue;
            }
            Location at = new Location(world, x + 0.5, y, z + 0.5, rng.nextFloat() * 360f, 0f);
            boolean tooClose = false;
            for (Player p : players) {
                if (p.getLocation().distanceSquared(at) < minDistSq) {
                    tooClose = true;
                    break;
                }
            }
            if (!tooClose) {
                return at;
            }
        }
        return null;
    }

    /** Highest y in [low, high] with a solid block under it and two passable blocks, or MIN_VALUE. */
    static int standY(World world, int x, int z, int high, int low) {
        for (int y = high; y >= low; y--) {
            Block feet = world.getBlockAt(x, y, z);
            if (!feet.isPassable() || !feet.getRelative(0, 1, 0).isPassable()) {
                continue;
            }
            Block floor = feet.getRelative(0, -1, 0);
            if (floor.getType().isSolid()) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }

    // ------------------------------------------------------------------ mini-boss

    private void announce(ZoneState state, Zone zone, List<Player> players) {
        Tracked t = tracked.get(state.boss);
        if (t == null) {
            return;
        }
        Component name = name(t.def(), false);
        Component shown = name == null ? Component.text(t.def().id()) : name;
        for (Player p : players) {
            msg.send(p, "combat.mini-boss.spawn", Msg.c("name", shown), Msg.p("zone", zone.name()));
            eventSound(p, config.miniBossSpawnSound); // F1: a boss announcement, follows "Event sounds" in /settings
        }
        if (config.miniBossBar) {
            state.bar = BossBar.bossBar(barName(t, t.entity().getHealth()), 1f, BossBar.Color.RED, BossBar.Overlay.NOTCHED_10);
        }
    }

    /** R3-10: the boss bar name with the scaled health ({@code combat.mini-boss.bar-health}). */
    private Component barName(Tracked t, double health) {
        Component name = name(t.def(), false);
        Component shown = name == null ? Component.text(t.def().id()) : name;
        double max = maxHealth(t.entity());
        int extra = t.fight() == null ? 0 : (int) Math.round((t.fight().scale() - 1.0) * 100.0);
        return msg.component(null, extra > 0 ? "combat.mini-boss.bar-health-scaled" : "combat.mini-boss.bar-health",
                Msg.c("name", shown), Msg.number("health", (long) Math.ceil(Math.max(0, health))),
                Msg.number("max", (long) Math.ceil(max)), Msg.number("extra", extra));
    }

    /** Updates the bar's fill and name after the boss's health changed (hit or scaling). */
    private void updateBar(ZoneState state, Tracked t, double health) {
        if (state == null || state.bar == null) {
            return;
        }
        double max = maxHealth(t.entity());
        state.bar.progress((float) Math.max(0, Math.min(1, health / max)));
        state.bar.name(barName(t, health));
    }

    // ------------------------------------------------------------------ R3-10 scaling and eligibility

    /** Re-applies the mini-boss's health scale from the fighters that are active now (once per cycle). */
    private void rescale(ZoneState state, long now) {
        Tracked t = state.boss == null ? null : tracked.get(state.boss);
        if (t == null || t.fight() == null || !t.entity().isValid() || t.entity().isDead()) {
            return;
        }
        BossFight fight = t.fight();
        int fighters = fight.active(now, config.miniBossRecentHitSeconds * 1000L, config.miniBossMaxPerIp, this::afkProfile).size();
        double scale = BossFight.scale(fighters, config.miniBossHealthPerExtra, config.miniBossMaxExtra);
        if (Math.abs(scale - fight.scale()) < 1e-9) {
            return;
        }
        AttributeInstance attr = t.entity().getAttribute(Attribute.MAX_HEALTH);
        if (attr == null) {
            return;
        }
        double oldMax = attr.getValue();
        double health = t.entity().getHealth();
        attr.setBaseValue(fight.baseHealth() * scale);
        double newMax = attr.getValue();
        double next = BossFight.rescaled(health, oldMax, newMax);
        t.entity().setHealth(next);
        fight.scale(scale);
        updateBar(state, t, next);
    }

    /** R3-10 "not AFK": /afk (social module) or standing in the AFK pool. Offline profiles count as AFK. */
    private boolean afkProfile(UUID profile) {
        Optional<Player> player = ProfileService.of(core.services()).onlinePlayer(profile);
        return player.isEmpty() || afk(player.get());
    }

    private boolean afk(Player player) {
        if (net.skybornmc.skyblock.common.afk.AfkPool.of(core.services()).map(p -> p.inPool(player)).orElse(false)) {
            return true;
        }
        return core.placeholders().has("afk")
                && core.placeholders().text(player, "afk").map("true"::equalsIgnoreCase).orElse(false);
    }

    private static String ip(Player player) {
        java.net.InetSocketAddress address = player.getAddress();
        return address == null || address.getAddress() == null ? null : address.getAddress().getHostAddress();
    }

    /** R3-24: Combat XP only for kills inside a Depths combat zone (the mob's spot when it died). */
    static boolean paysCombatXp(Aspect aspectAtDeath) {
        return aspectAtDeath == Aspect.COMBAT;
    }

    private void syncBar(ZoneState state, List<Player> players) {
        if (state.bar == null) {
            return;
        }
        Set<UUID> now = new HashSet<>();
        for (Player p : players) {
            now.add(p.getUniqueId());
            if (state.barViewers.add(p.getUniqueId())) {
                p.showBossBar(state.bar);
            }
        }
        for (Iterator<UUID> it = state.barViewers.iterator(); it.hasNext(); ) {
            UUID id = it.next();
            if (!now.contains(id)) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) {
                    p.hideBossBar(state.bar);
                }
                it.remove();
            }
        }
    }

    private void hideBar(ZoneState state) {
        if (state.bar == null) {
            return;
        }
        for (UUID id : state.barViewers) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.hideBossBar(state.bar);
            }
        }
        state.barViewers.clear();
        state.bar = null;
    }

    private void bossGone(ZoneState state) {
        state.boss = null;
        hideBar(state);
        if (state.clock != null) {
            state.clock.gone(System.currentTimeMillis(), Math.max(0, state.clockMinutes) * 60_000L);
        }
    }

    /** Removes every mob of a zone (empty zone, reload, disable). */
    private void clear(ZoneState state) {
        for (UUID id : state.mobs) {
            abilities.untrack(id);
            Tracked t = tracked.remove(id);
            if (t != null && t.entity().isValid()) {
                t.entity().remove();
            }
        }
        state.mobs.clear();
        if (state.boss != null) {
            bossGone(state);
        }
        hideBar(state);
    }

    // ------------------------------------------------------------------ kills and rewards

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Tracked t = tracked.remove(entity.getUniqueId());
        if (t == null) {
            return;
        }
        abilities.untrack(entity.getUniqueId());
        event.getDrops().clear();
        event.setDroppedExp(0);
        ZoneState state = states.get(t.zoneId());
        if (state != null) {
            state.mobs.remove(entity.getUniqueId());
            if (t.boss() && entity.getUniqueId().equals(state.boss)) {
                bossGone(state);
            }
        }
        Player killer = entity.getKiller();
        if (!t.boss()) {
            if (killer != null) {
                reward(killer, entity, t, false);
            }
            return;
        }
        ProfileService profiles = ProfileService.of(core.services());
        double max = maxHealth(entity);
        Set<UUID> paid = new HashSet<>();
        // R3-10: everyone eligible (10% of the damage, hit in the last 90 s, not AFK, max 3 per IP) gets the full reward,
        // exactly once (claim() hands the list out a single time).
        List<UUID> winners = t.fight().claim(System.currentTimeMillis(), max, config.miniBossMinDamageShare,
                config.miniBossRecentHitSeconds * 1000L, config.miniBossMaxPerIp, this::afkProfile);
        for (UUID profile : winners) {
            Optional<Player> p = profiles.onlinePlayer(profile);
            if (p.isEmpty() || !paid.add(p.get().getUniqueId())) {
                continue;
            }
            reward(p.get(), entity, t, true);
            msg.send(p.get(), "combat.mini-boss.reward", Msg.c("name", nameOrId(t.def())));
            sound(p.get(), config.miniBossKillSound);
        }
        if (state != null && killer != null) {
            World world = entity.getWorld();
            for (Player p : world.getPlayers()) {
                Zone zone = zones.zoneAt(p.getLocation());
                if (zone != null && zone.id().equals(t.zoneId())) {
                    msg.send(p, "combat.mini-boss.killed", Msg.c("name", nameOrId(t.def())), Msg.p("player", killer.getName()));
                }
            }
        }
    }

    private Component nameOrId(MobDef def) {
        Component name = name(def, false);
        return name == null ? Component.text(def.id()) : name;
    }

    private void reward(Player player, LivingEntity entity, Tracked t, boolean boss) {
        ItemStack tool = player.getInventory().getItemInMainHand();
        BonusService bonus = core.services().optional(BonusService.class).orElse(null);
        BonusContext ctx = new BonusContext(player, tool, null, SkillType.COMBAT.id());
        double fortune = core.services().optional(ToolService.class).map(s -> s.dropMultiplier(tool))
                .orElseGet(() -> bonus == null ? 1.0 : bonus.multiplier(BonusFamily.EXTRA_DROPS, ctx));
        double crystalBonus = bonus == null ? 1.0 : bonus.multiplier(BonusFamily.CRYSTALS, ctx);
        // M8.1 elites: more loot on an elite, a little less on normal kills of that mob (budget-neutral); 1.0 otherwise.
        EliteRules rules = eliteRules();
        double loot = rules.lootFactor(t.def().stats().eliteChance(), t.tier(), t.boss(), t.elite());
        MobRewards.Roll roll = MobRewards.roll(t.def(), fortune, crystalBonus, config.combatMaxDropMultiplier, loot,
                ThreadLocalRandom.current());
        Map<String, Integer> items = roll.items();
        long crystals = roll.crystals();
        double xp = t.elite() ? roll.xp() * rules.xpMultiplier() : roll.xp();
        if (DepthsMobKillEvent.getHandlerList().getRegisteredListeners().length > 0) {
            DepthsMobKillEvent hook = new DepthsMobKillEvent(player, entity, t.def().id(), t.tier(), boss, items, crystals, xp);
            Bukkit.getPluginManager().callEvent(hook);
            items = hook.getItems();
            crystals = hook.getCrystals();
            xp = hook.getXp();
        }
        // R3-22: satchel → inventory → /box (combat drops were lost on a full satchel before).
        items.forEach((id, n) -> {
            if (n != null && n > 0) {
                drops.deliver(player, id, n, true);
            }
        });
        drops.credit(player, crystals);
        Zone at = zones.zoneAt(entity.getLocation());
        if (xp > 0 && paysCombatXp(at == null ? null : at.aspect())) {
            double gain = xp;
            core.services().optional(SkillService.class).ifPresent(s -> s.addXp(player, SkillType.COMBAT, gain, XP_SOURCE));
        }
        if (config.combatVanillaXp && t.def().vanillaXp() > 0) {
            player.giveExp(t.def().vanillaXp());
        }
    }

    private static double maxHealth(LivingEntity entity) {
        AttributeInstance a = entity.getAttribute(Attribute.MAX_HEALTH);
        return a == null ? 1 : a.getValue();
    }

    // ------------------------------------------------------------------ damage, targets, guards

    /** The living entity behind a damager (projectile shooter, fang caster, cloud source), or the damager itself. */
    static Entity attacker(Entity damager) {
        if (damager instanceof Projectile projectile) {
            ProjectileSource source = projectile.getShooter();
            return source instanceof Entity e ? e : damager;
        }
        if (damager instanceof EvokerFangs fangs && fangs.getOwner() != null) {
            return fangs.getOwner();
        }
        if (damager instanceof AreaEffectCloud cloud && cloud.getSource() instanceof Entity e) {
            return e;
        }
        return damager;
    }

    /** Mob damage per tier: hits on players deal the mob's damage; mobs never hurt each other. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Entity from = attacker(event.getDamager());
        Tracked t = tracked.get(from.getUniqueId());
        if (t == null) {
            return;
        }
        if (!(event.getEntity() instanceof Player)) {
            event.setCancelled(true);
            return;
        }
        if (abilities.applying()) {
            return; // M8.1: an ability set its own damage (player.damage with the mob as source)
        }
        if (event.getDamager() == from && !abilities.meleeAllowed(from.getUniqueId())) {
            event.setCancelled(true); // M8.1 attack-cooldown: too soon after its last melee hit
            return;
        }
        double mult = abilities.damageMultiplier(from.getUniqueId()) * (t.elite() ? config.eliteDamageMultiplier : 1.0);
        if (t.def().damage() > 0) {
            event.setDamage(t.def().damage() * mult);
        } else if (mult != 1.0) {
            event.setDamage(event.getDamage() * mult);
        }
    }

    /**
     * M8.1 armor and shield: a player's hit on a combat mob is reduced by the mob's armor % (max 60) and, while its
     * shield is up, by the shield. Only player → mob hits; player versus player damage is never touched here.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMobHurt(EntityDamageByEntityEvent event) {
        Tracked t = tracked.get(event.getEntity().getUniqueId());
        if (t == null || !(attacker(event.getDamager()) instanceof Player)) {
            return;
        }
        UUID id = t.entity().getUniqueId();
        double armor = t.def().stats().armor();
        boolean shield = abilities.shielded(id);
        if (armor > 0 || shield) {
            event.setDamage(MobCombatMath.playerHitOnMob(event.getDamage(), armor, shield, abilities.shieldReduction(id)));
        }
    }

    /** M8.1 enrage: checked after every hit on a mob that has it. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMobHurtAfter(EntityDamageByEntityEvent event) {
        Tracked t = tracked.get(event.getEntity().getUniqueId());
        if (t != null) {
            LivingEntity mob = t.entity();
            abilities.afterHit(mob.getUniqueId(), mob.getHealth() - event.getFinalDamage(), maxHealth(mob));
        }
    }

    /** Mini-boss damage shares (per profile) and boss bar. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBossHit(EntityDamageByEntityEvent event) {
        Tracked t = tracked.get(event.getEntity().getUniqueId());
        if (t == null || !t.boss()) {
            return;
        }
        LivingEntity boss = t.entity();
        double dealt = Math.min(event.getFinalDamage(), boss.getHealth());
        if (attacker(event.getDamager()) instanceof Player player) {
            t.fight().hit(ProfileService.of(core.services()).profileKey(player), ip(player), dealt, System.currentTimeMillis());
        }
        updateBar(states.get(t.zoneId()), t, Math.max(0, boss.getHealth() - dealt));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (event.getTarget() != null && !(event.getTarget() instanceof Player) && tracked.containsKey(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /** No zombie → drowned, piglin zombification or slime splitting: the mob would leave the tracker. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTransform(EntityTransformEvent event) {
        if (event.getEntity().getScoreboardTags().contains(MobFactory.TAG)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSplit(SlimeSplitEvent event) {
        if (event.getEntity().getScoreboardTags().contains(MobFactory.TAG)) {
            event.setCancelled(true);
        }
    }

    /** Only the plugin, commands and spawn eggs bring mobs into the Depths world (no reinforcements, vexes, jockeys). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (config.combatBlockOtherSpawns && event.getEntity() instanceof Mob && inDepths(event.getEntity().getWorld())
                && !ALLOWED_REASONS.contains(event.getSpawnReason())) {
            event.setCancelled(true);
        }
    }

    /** Depths mobs never change blocks (ravagers, endermen). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChangeBlock(EntityChangeBlockEvent event) {
        if (event.getEntity().getScoreboardTags().contains(MobFactory.TAG)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        if (inDepths(event.getEntity().getWorld())) {
            event.blockList().clear();
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        if (BLOCKED_IGNITES.contains(event.getCause()) && inDepths(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    /** A Depths mob loaded from disk is an orphan (they are never saved; this only guards against crashes). */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        if (inDepths(event.getWorld())) {
            removeOrphans(event.getEntities());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        for (ZoneState state : states.values()) {
            if (state.bar != null && state.barViewers.remove(event.getPlayer().getUniqueId())) {
                event.getPlayer().hideBossBar(state.bar);
            }
        }
    }

    private void removeOrphans(List<Entity> entities) {
        for (Entity e : entities) {
            if (e.getScoreboardTags().contains(MobFactory.TAG) && !tracked.containsKey(e.getUniqueId())
                    && !otherOwned.test(e.getUniqueId())) {
                e.remove();
            }
        }
    }

    private boolean inDepths(World world) {
        return world != null && world.getName().equals(zones.world());
    }

    /** An event/boss announcement sound: muted for players who turned "Event sounds" off in /settings. */
    private void eventSound(Player player, String sound) {
        if (sound != null && !sound.isBlank()) {
            core.sounds().playEvent(player, sound);
        }
    }

    private void sound(Player player, String sound) {
        if (sound != null && !sound.isBlank()) {
            core.sounds().play(player, sound);
        }
    }

    // ------------------------------------------------------------------ staff debug

    /** {@code /depths debug}: combat numbers and the target's zone. */
    public void debug(org.bukkit.command.CommandSender viewer, Player target) {
        msg.send(viewer, "debug.combat", Msg.p("alive", tracked.size()), Msg.p("max", config.combatMaxTotalMobs),
                Msg.p("zones", states.size()));
        msg.send(viewer, "debug.abilities", Msg.p("mobs", abilities.mobCount()), Msg.p("active", abilities.activeEffects()),
                Msg.p("max", config.abilitiesMaxActive));
        Zone zone = zones.zoneAt(target.getLocation());
        ZoneState state = zone == null ? null : states.get(zone.id());
        if (zone == null || zone.aspect() != Aspect.COMBAT || state == null) {
            return;
        }
        ZoneSpawns spawns = catalog.combat(zone.tier());
        long now = System.currentTimeMillis();
        msg.send(viewer, "debug.combat-zone", Msg.p("zone", zone.id()), Msg.p("alive", state.mobs.size()),
                Msg.p("target", SpawnMath.target(spawns, state.players)), Msg.p("players", state.players),
                Msg.p("boss", state.boss != null ? "alive" : state.clock == null ? "-" : (state.clock.remaining(now) / 1000) + " s"));
    }
}
