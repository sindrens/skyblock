package net.skybornmc.skyblock.depths.combat;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.Core;
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
    record Tracked(LivingEntity entity, MobDef def, String zoneId, int tier, boolean boss, DamageShares shares) {
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

    public CombatZones(Core core, DepthsConfig config, ModuleMessages msg, ZoneService zones, SatchelManager satchel,
                       DepthsDrops drops, MobFactory factory) {
        this.core = core;
        this.config = config;
        this.msg = msg;
        this.zones = zones;
        this.satchel = satchel;
        this.drops = drops;
        this.factory = factory;
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
        long ticks = Math.max(10, config.combatCycleTicks);
        task = core.scheduler().repeat("depths-combat", ticks, ticks, this::cycle);
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
        clearAll();
    }

    /** Removes every combat mob and boss bar; the timer keeps running. */
    private void clearAll() {
        for (ZoneState state : new ArrayList<>(states.values())) {
            clear(state);
        }
        states.clear();
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
        Set<String> live = new HashSet<>();
        RandomGenerator rng = ThreadLocalRandom.current();
        MobCatalog cat = catalog;
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
            prune(state, zone, world, rng);
            List<Player> players = present.getOrDefault(zone.id(), List.of());
            state.players = players.size();
            if (players.isEmpty()) {
                if (state.emptySince == 0) {
                    state.emptySince = now;
                }
                if (SpawnMath.despawnDue(state.emptySince, now, config.combatDespawnAfterSeconds * 1000L)) {
                    clear(state);
                }
                syncBar(state, players);
                continue;
            }
            state.emptySince = 0;
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
            if (spawns.hasMiniBoss() && state.clock.due(now, true)) {
                MobDef boss = cat.mob(spawns.miniBoss());
                Location at = boss == null ? null : spot(world, zone, players, rng);
                if (at != null && spawn(state, zone, boss, at, true) != null) {
                    announce(state, zone, players);
                }
            }
            syncBar(state, players);
        }
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
    private void prune(ZoneState state, Zone zone, World world, RandomGenerator rng) {
        for (Iterator<UUID> it = state.mobs.iterator(); it.hasNext(); ) {
            UUID id = it.next();
            Tracked t = tracked.get(id);
            if (t == null || !t.entity().isValid() || t.entity().isDead()) {
                it.remove();
                tracked.remove(id);
                if (id.equals(state.boss)) {
                    bossGone(state);
                }
                continue;
            }
            if (!config.combatLeash) {
                continue;
            }
            Location l = t.entity().getLocation();
            if (l.getWorld() != world || !zone.region().contains(l.getX(), l.getY(), l.getZ())) {
                Location back = spot(world, zone, List.of(), rng);
                if (back != null) {
                    t.entity().teleportAsync(back);
                } else {
                    it.remove();
                    tracked.remove(id);
                    t.entity().remove();
                    if (id.equals(state.boss)) {
                        bossGone(state);
                    }
                }
            }
        }
    }

    private LivingEntity spawn(ZoneState state, Zone zone, MobDef def, Location at, boolean boss) {
        LivingEntity e = factory.spawn(at, def, name(def, boss), null, false);
        if (e == null) {
            return null;
        }
        tracked.put(e.getUniqueId(), new Tracked(e, def, zone.id(), zone.tier(), boss, boss ? new DamageShares() : null));
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
        Component name = msg.component(null, key);
        return boss ? msg.component(null, "combat.mini-boss.name", Msg.c("name", name)) : name;
    }

    /** A free spot in the zone's arena (or the whole zone): solid floor, two free blocks, loaded chunk, away from players. */
    Location spot(World world, Zone zone, List<Player> players, RandomGenerator rng) {
        Box area = zone.arena() != null ? zone.arena() : zone.region();
        double minDistSq = (double) config.combatMinPlayerDistance * config.combatMinPlayerDistance;
        for (int attempt = 0; attempt < config.combatSpawnAttempts; attempt++) {
            int x = area.minX() + 1 + rng.nextInt(Math.max(1, area.sizeX() - 2));
            int z = area.minZ() + 1 + rng.nextInt(Math.max(1, area.sizeZ() - 2));
            if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }
            int y = standY(world, x, z, area.maxY(), area.minY());
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
            sound(p, config.miniBossSpawnSound);
        }
        if (config.miniBossBar) {
            state.bar = BossBar.bossBar(msg.component(null, "combat.mini-boss.bar", Msg.c("name", shown)), 1f,
                    BossBar.Color.RED, BossBar.Overlay.NOTCHED_10);
        }
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
        UUID killerKey = killer == null ? null : profiles.profileKey(killer);
        double max = maxHealth(entity);
        Set<UUID> paid = new HashSet<>();
        for (UUID profile : t.shares().eligible(killerKey, max, config.miniBossMinDamageShare)) {
            Optional<Player> p = profile.equals(killerKey) ? Optional.of(killer) : profiles.onlinePlayer(profile);
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
        MobRewards.Roll roll = MobRewards.roll(t.def(), fortune, crystalBonus, config.combatMaxDropMultiplier,
                ThreadLocalRandom.current());
        Map<String, Integer> items = roll.items();
        long crystals = roll.crystals();
        double xp = roll.xp();
        if (DepthsMobKillEvent.getHandlerList().getRegisteredListeners().length > 0) {
            DepthsMobKillEvent hook = new DepthsMobKillEvent(player, entity, t.def().id(), t.tier(), boss, items, crystals, xp);
            Bukkit.getPluginManager().callEvent(hook);
            items = hook.getItems();
            crystals = hook.getCrystals();
            xp = hook.getXp();
        }
        if (config.satchelEnabled && !items.isEmpty() && satchel.accepts(player)) {
            items.forEach((id, n) -> {
                if (n != null && n > 0) {
                    satchel.add(player, id, n);
                }
            });
        }
        drops.credit(player, crystals);
        if (xp > 0) {
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
        if (t.def().damage() > 0) {
            event.setDamage(t.def().damage());
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
            t.shares().add(ProfileService.of(core.services()).profileKey(player), dealt);
        }
        ZoneState state = states.get(t.zoneId());
        if (state != null && state.bar != null) {
            float left = (float) Math.max(0, Math.min(1, (boss.getHealth() - dealt) / maxHealth(boss)));
            state.bar.progress(left);
        }
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
