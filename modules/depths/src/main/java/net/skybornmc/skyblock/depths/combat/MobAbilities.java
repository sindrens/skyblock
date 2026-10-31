package net.skybornmc.skyblock.depths.combat;

import io.papermc.paper.math.Position;
import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.mob.AbilityClock;
import net.skybornmc.skyblock.common.mob.AbilityId;
import net.skybornmc.skyblock.common.mob.AbilitySpec;
import net.skybornmc.skyblock.common.mob.MobCombatMath;
import net.skybornmc.skyblock.common.mob.MobDef;
import net.skybornmc.skyblock.common.mob.StrikeQueue;
import net.skybornmc.skyblock.common.region.RegionFlag;
import net.skybornmc.skyblock.common.region.RegionFlags;
import net.skybornmc.skyblock.common.text.ModuleMessages;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.depths.DepthsConfig;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

/**
 * Mob abilities in the Depths combat zones (M8.1). One shared scheduler runs every {@code combat.abilities.tick-ticks}:
 * it advances the running effects ({@link StrikeQueue}) and asks the {@link AbilityClock} of each mob that has abilities
 * (a long comparison per mob, nothing else, until one is ready). Players are only ever looked up in the mob's own zone
 * (the player list of the last combat cycle), never in the whole world. At most {@code combat.abilities.max-active}
 * effects run at once.
 *
 * <p>Every ability is telegraphed first ({@code telegraph-ticks}, at least 1 s): a ring or lane of fake blocks
 * ({@code sendMultiBlockChange}, works on Bedrock through Geyser), a warning sound and an action bar line, particles only
 * as an extra; the mob stands still while it winds up. The hit then only reaches players that are still inside the marked
 * area. Ability damage only ever hits players from a mob (never between players) and never reaches players in a safe
 * region (flag mob-target).</p>
 */
public final class MobAbilities {

    /** Scoreboard tag of volley arrows (never picked up, gone after a few seconds). */
    static final String ARROW_TAG = "skyblock_depths_volley";

    /** What the abilities need from the combat zones. */
    interface Zones {
        /** Players standing in the zone (last combat cycle). */
        List<Player> players(String zoneId);

        /** True if the spot is inside the zone. */
        boolean inside(String zoneId, Location at);

        /** Spawns one regular mob of the zone near the spot (tracked like any other); {@code null} if not possible. */
        LivingEntity spawnAdd(String zoneId, Location near);
    }

    /** Runtime state of one mob with abilities or an attack cooldown. */
    static final class MobState {
        final LivingEntity entity;
        final MobDef def;
        final String zoneId;
        final AbilityClock clock;
        final AbilitySpec enrage;
        final int attackCooldown;
        long lastMelee = Long.MIN_VALUE;
        boolean enraged;
        double damageMultiplier = 1.0;
        double attackFactor = 1.0;
        long shieldUntil;
        double shieldReduction;
        Component savedName;
        boolean savedNameVisible;
        final Set<UUID> adds = new HashSet<>();

        MobState(LivingEntity entity, MobDef def, String zoneId, AbilityClock clock, AbilitySpec enrage, int attackCooldown) {
            this.entity = entity;
            this.def = def;
            this.zoneId = zoneId;
            this.clock = clock;
            this.enrage = enrage;
            this.attackCooldown = attackCooldown;
        }
    }

    /** One running ability effect. */
    static final class Effect {
        final MobState mob;
        final AbilitySpec spec;
        final Location center;
        final Location dest;
        final Player target;
        final List<Position> blocks;
        final Set<UUID> viewers = new HashSet<>();
        boolean awake;

        Effect(MobState mob, AbilitySpec spec, Location center, Location dest, Player target, List<Position> blocks) {
            this.mob = mob;
            this.spec = spec;
            this.center = center;
            this.dest = dest;
            this.target = target;
            this.blocks = blocks;
        }
    }

    private final Core core;
    private final DepthsConfig config;
    private final ModuleMessages msg;
    private final Zones zones;
    private final Map<UUID, MobState> mobs = new LinkedHashMap<>();
    private final StrikeQueue<Effect> strikes;
    private net.skybornmc.skyblock.common.util.Scheduler.Task task;
    private boolean applying;

    MobAbilities(Core core, DepthsConfig config, ModuleMessages msg, Zones zones) {
        this.core = core;
        this.config = config;
        this.msg = msg;
        this.zones = zones;
        this.strikes = new StrikeQueue<>(new Sink(), 20);
    }

    /** (Re)starts the scheduler; running effects end and every mob is forgotten (the caller tracks them again). */
    void start() {
        stop();
        long period = Math.max(1, config.abilitiesTickTicks);
        task = core.scheduler().repeat("depths-abilities", period, period, this::tick);
    }

    /** Stops the scheduler, ends every effect (restores fake blocks and names) and forgets every mob. */
    void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        reset();
    }

    /** Ends every effect and forgets every mob; the scheduler keeps running. */
    void reset() {
        strikes.clear();
        mobs.clear();
    }

    int mobCount() {
        return mobs.size();
    }

    int activeEffects() {
        return strikes.active();
    }

    /** True while an ability is dealing its own damage (the normal per-hit damage must not replace it). */
    boolean applying() {
        return applying;
    }

    // ------------------------------------------------------------------ tracking

    /**
     * Starts tracking a new combat mob.
     *
     * @param abilities the mob's abilities (elite extra included), already resolved with the catalog defaults; empty
     *                  when abilities are off for it
     */
    void track(LivingEntity entity, MobDef def, String zoneId, List<AbilitySpec> abilities) {
        int cooldown = def.stats().attackCooldownTicks();
        if (abilities.isEmpty() && cooldown <= 0) {
            return;
        }
        long now = Bukkit.getCurrentTick();
        AbilitySpec enrage = null;
        for (AbilitySpec a : abilities) {
            if (a.id() == AbilityId.ENRAGE) {
                enrage = a;
            }
        }
        AbilityClock clock = new AbilityClock(abilities, now, config.abilitiesFirstDelayTicks);
        mobs.put(entity.getUniqueId(), new MobState(entity, def, zoneId, clock, enrage, cooldown));
    }

    /** Stops tracking a mob (death, removal); its running effects end on the next run. */
    void untrack(UUID id) {
        MobState m = mobs.remove(id);
        if (m != null) {
            strikes.cancel(e -> e.mob == m);
        }
    }

    /** M8.2: no new ability starts before {@code untilTick} (a mini-boss walking back to its ring). */
    void hold(UUID id, long untilTick) {
        MobState m = mobs.get(id);
        if (m != null) {
            m.clock.busyUntil(untilTick);
        }
    }

    /** M8.2: true while one of the mob's abilities is winding up or running (a charge or leap may carry it away). */
    boolean busy(UUID id) {
        MobState m = mobs.get(id);
        return m != null && m.clock.busy(Bukkit.getCurrentTick());
    }

    // ------------------------------------------------------------------ combat hooks

    /** Damage multiplier of a mob's normal hits (enrage). */
    double damageMultiplier(UUID id) {
        MobState m = mobs.get(id);
        return m == null ? 1.0 : m.damageMultiplier;
    }

    /** Attack cooldown check for a melee hit; records the hit when allowed. */
    boolean meleeAllowed(UUID id) {
        MobState m = mobs.get(id);
        if (m == null || m.attackCooldown <= 0) {
            return true;
        }
        long now = Bukkit.getCurrentTick();
        if (!MobCombatMath.meleeAllowed(m.lastMelee, now, MobCombatMath.scaledCooldown(m.attackCooldown, m.attackFactor))) {
            return false;
        }
        m.lastMelee = now;
        return true;
    }

    /** True while the mob's shield is up. */
    boolean shielded(UUID id) {
        MobState m = mobs.get(id);
        return m != null && m.shieldUntil > Bukkit.getCurrentTick();
    }

    double shieldReduction(UUID id) {
        MobState m = mobs.get(id);
        return m == null ? 0 : m.shieldReduction;
    }

    /** After a hit on a tracked mob: starts enrage once its health falls below the threshold. */
    void afterHit(UUID id, double healthAfter, double maxHealth) {
        MobState m = mobs.get(id);
        if (m == null || m.enrage == null || !config.abilitiesEnabled
                || !MobCombatMath.enrageDue(healthAfter, maxHealth, m.enrage.num("below"), m.enraged)) {
            return;
        }
        m.enraged = true; // once, even if the effect cap is full (enrage is cheap and must not be lost)
        launch(m, m.enrage, Bukkit.getCurrentTick());
    }

    // ------------------------------------------------------------------ the scheduler

    void tick() {
        long now = Bukkit.getCurrentTick();
        strikes.advance(now);
        if (mobs.isEmpty()) {
            return;
        }
        long t0 = System.nanoTime();
        RandomGenerator rng = ThreadLocalRandom.current();
        boolean enabled = config.abilitiesEnabled;
        for (Iterator<MobState> it = mobs.values().iterator(); it.hasNext(); ) {
            MobState m = it.next();
            if (!m.entity.isValid() || m.entity.isDead()) {
                it.remove();
                strikes.cancel(e -> e.mob == m);
                continue;
            }
            if (!enabled || m.clock.size() == 0 || now < m.clock.nextReady()) {
                continue;
            }
            int index = m.clock.poll(now, () -> nearest(m, config.abilitiesActiveRange) != null,
                    strikes.active() < config.abilitiesMaxActive, config.abilitiesRetryTicks, rng);
            if (index >= 0) {
                launch(m, m.clock.ability(index), now);
            }
        }
        core.scheduler().timer().record("depths-abilities", System.nanoTime() - t0);
    }

    /** Builds and starts one effect; nothing happens if the ability has no valid target right now. */
    private void launch(MobState m, AbilitySpec spec, long now) {
        Location center = m.entity.getLocation();
        Player target = null;
        Location dest = null;
        List<int[]> shape;
        int pulses = 1;
        int interval = 1;
        int linger = 0;
        switch (spec.id()) {
            case SLAM, PULL, CURSE -> shape = MobCombatMath.ring(spec.num("radius"));
            case RING -> {
                shape = MobCombatMath.ring(spec.num("radius"));
                pulses = (int) Math.max(1, Math.round(spec.num("seconds")));
                interval = 20;
            }
            case LEAP -> {
                target = farthest(m, spec.num("range"));
                if (target == null) {
                    return;
                }
                dest = target.getLocation();
                center = dest.clone();
                shape = MobCombatMath.ring(spec.num("radius"));
                pulses = 2;
                interval = config.abilitiesLeapLandTicks;
            }
            case CHARGE -> {
                target = nearest(m, config.abilitiesActiveRange);
                if (target == null) {
                    return;
                }
                Vector dir = flat(target.getLocation().toVector().subtract(center.toVector()));
                int length = (int) Math.round(spec.num("length"));
                dest = center.clone().add(dir.clone().multiply(length));
                shape = MobCombatMath.lane(dir.getX(), dir.getZ(), length);
            }
            case VOLLEY -> {
                target = nearest(m, config.abilitiesActiveRange);
                if (target == null) {
                    return;
                }
                shape = MobCombatMath.ring(2);
            }
            case TELEPORT -> {
                target = nearest(m, spec.num("range"));
                if (target == null) {
                    return;
                }
                Vector back = flat(target.getLocation().getDirection()).multiply(-spec.num("distance"));
                dest = target.getLocation().clone().add(back);
                if (!zones.inside(m.zoneId, dest) || !standable(dest)) {
                    return;
                }
                center = dest.clone();
                shape = MobCombatMath.ring(1);
            }
            case SUMMON, ENRAGE -> shape = MobCombatMath.ring(1.5);
            case SHIELD -> {
                shape = MobCombatMath.ring(1.5);
                linger = (int) Math.round(spec.num("seconds") * 20);
            }
            default -> {
                return;
            }
        }
        Effect effect = new Effect(m, spec, center, dest, target, floor(center, shape));
        StrikeQueue.Strike<Effect> s = strikes.start(now, config.abilitiesTelegraphTicks, pulses, interval, linger, effect);
        m.clock.busyUntil(s.firstHitAt() + (long) (pulses - 1) * interval + 1);
    }

    /** Runs the telegraph, hits and end of each effect. */
    private final class Sink implements StrikeQueue.Sink<Effect> {

        @Override
        public void telegraph(StrikeQueue.Strike<Effect> strike) {
            Effect e = strike.payload();
            if (e.mob.entity instanceof Mob mob && e.spec.id() != AbilityId.SHIELD) {
                mob.setAware(false); // stands still while it winds up: what you see is what hits
            }
            showBlocks(e, block(config.abilitiesTelegraphBlock, Material.RED_CONCRETE));
            Component name = displayName(e.mob);
            for (Player p : viewers(e)) {
                msg.actionBar(p, "combat.ability." + e.spec.id().id(), Msg.c("name", name));
                if (config.abilitiesTelegraphSound != null && !config.abilitiesTelegraphSound.isBlank()) {
                    core.sounds().play(p, config.abilitiesTelegraphSound);
                }
            }
            particles(e, config.abilitiesTelegraphParticle);
        }

        @Override
        public void hit(StrikeQueue.Strike<Effect> strike, int pulse) {
            Effect e = strike.payload();
            if (!e.mob.entity.isValid() || e.mob.entity.isDead()) {
                return;
            }
            wake(e);
            try {
                apply(e, pulse);
            } catch (RuntimeException ex) {
                core.plugin().getLogger().warning("Depths ability " + e.spec.id().id() + " of " + e.mob.def.id() + " failed: " + ex);
            }
        }

        @Override
        public void end(StrikeQueue.Strike<Effect> strike) {
            Effect e = strike.payload();
            wake(e);
            restoreBlocks(e);
            if (e.spec.id() == AbilityId.SHIELD) {
                endShield(e.mob);
            }
        }
    }

    private void wake(Effect e) {
        if (!e.awake && e.mob.entity instanceof Mob mob && mob.isValid()) {
            mob.setAware(true);
        }
        e.awake = true;
    }

    // ------------------------------------------------------------------ the abilities

    private void apply(Effect e, int pulse) {
        MobState m = e.mob;
        AbilitySpec s = e.spec;
        switch (s.id()) {
            case SLAM -> {
                double radius = s.num("radius");
                for (Player p : inside(e, e.center, radius)) {
                    hurt(m, p, s.num("damage"));
                    Vector away = flat(p.getLocation().toVector().subtract(e.center.toVector()));
                    p.setVelocity(away.multiply(s.num("knockback")).setY(0.4));
                }
            }
            case LEAP -> {
                if (pulse == 0) {
                    Vector to = e.dest.toVector().subtract(m.entity.getLocation().toVector());
                    double dist = Math.hypot(to.getX(), to.getZ());
                    double speed = Math.max(0.3, Math.min(2.5, dist * 0.12 + 0.2));
                    m.entity.setVelocity(flat(to).multiply(speed).setY(0.55));
                } else {
                    for (Player p : inside(e, e.dest, s.num("radius"))) {
                        hurt(m, p, s.num("damage"));
                    }
                }
            }
            case CHARGE -> {
                Vector dir = flat(e.dest.toVector().subtract(e.center.toVector()));
                m.entity.setVelocity(dir.clone().multiply(s.num("speed")).setY(0.1));
                double reach = s.num("width") / 2.0 + 0.4;
                for (Player p : zonePlayers(e)) {
                    Location l = p.getLocation();
                    if (Math.abs(l.getY() - e.center.getY()) <= 3 && MobCombatMath.distanceToSegment(l.getX(), l.getZ(),
                            e.center.getX(), e.center.getZ(), e.dest.getX(), e.dest.getZ()) <= reach) {
                        hurt(m, p, s.num("damage"));
                        p.setVelocity(dir.clone().multiply(0.8).setY(0.35));
                    }
                }
            }
            case VOLLEY -> {
                Player target = e.target != null && e.target.isValid() ? e.target : nearest(m, config.abilitiesActiveRange);
                if (target == null) {
                    return;
                }
                Location eye = m.entity.getEyeLocation();
                Vector base = target.getEyeLocation().toVector().subtract(eye.toVector());
                if (base.lengthSquared() < 1e-6) {
                    return;
                }
                base.normalize();
                for (double yaw : MobCombatMath.fan((int) Math.round(s.num("count")), s.num("spread"))) {
                    Vector v = base.clone().rotateAroundY(Math.toRadians(yaw)).multiply(s.num("speed"));
                    Arrow arrow = m.entity.launchProjectile(Arrow.class, v);
                    arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
                    arrow.setPersistent(false);
                    arrow.addScoreboardTag(ARROW_TAG);
                    arrow.setLifetimeTicks(Math.max(0, 1200 - config.abilitiesArrowLifeTicks));
                }
            }
            case PULL -> {
                Vector to = m.entity.getLocation().toVector();
                for (Player p : inside(e, e.center, s.num("radius"))) {
                    Vector pull = flat(to.clone().subtract(p.getLocation().toVector()));
                    p.setVelocity(pull.multiply(s.num("strength")).setY(0.3));
                }
            }
            case SUMMON -> {
                m.adds.removeIf(id -> {
                    var add = Bukkit.getEntity(id);
                    return add == null || !add.isValid() || add.isDead();
                });
                int room = (int) Math.round(s.num("max-alive")) - m.adds.size();
                int count = Math.min(room, (int) Math.round(s.num("count")));
                for (int i = 0; i < count; i++) {
                    LivingEntity add = zones.spawnAdd(m.zoneId, m.entity.getLocation());
                    if (add == null) {
                        break;
                    }
                    m.adds.add(add.getUniqueId());
                }
            }
            case SHIELD -> startShield(m, s, Bukkit.getCurrentTick() + Math.round(s.num("seconds") * 20));
            case RING -> {
                boolean fire = !"soul".equals(s.text("kind"));
                if (pulse == 0) {
                    showBlocks(e, fire ? block(config.abilitiesFireBlock, Material.MAGMA_BLOCK)
                            : block(config.abilitiesSoulBlock, Material.SOUL_SOIL));
                }
                for (Player p : inside(e, e.center, s.num("radius"))) {
                    hurt(m, p, s.num("damage"));
                    if (fire && config.abilitiesFireTicks > 0) {
                        p.setFireTicks(Math.max(p.getFireTicks(), config.abilitiesFireTicks));
                    }
                }
                particles(e, fire ? "flame" : "soul_fire_flame");
            }
            case CURSE -> {
                PotionEffectType type = switch (s.text("effect")) {
                    case "weakness" -> PotionEffectType.WEAKNESS;
                    case "wither" -> PotionEffectType.WITHER;
                    default -> PotionEffectType.SLOWNESS;
                };
                int ticks = (int) Math.round(s.num("seconds") * 20);
                int amplifier = (int) Math.round(s.num("level")) - 1;
                for (Player p : inside(e, e.center, s.num("radius"))) {
                    p.addPotionEffect(new PotionEffect(type, ticks, amplifier, false, true, true));
                }
            }
            case TELEPORT -> {
                if (zones.inside(m.zoneId, e.dest) && standable(e.dest)) {
                    Location to = e.dest.clone();
                    if (e.target != null && e.target.isValid()) {
                        to.setDirection(e.target.getLocation().toVector().subtract(to.toVector()));
                    }
                    m.entity.teleportAsync(to);
                }
            }
            case ENRAGE -> {
                m.damageMultiplier *= 1.0 + s.num("damage-bonus");
                m.attackFactor = s.num("attack-factor");
                AttributeInstance speed = m.entity.getAttribute(Attribute.MOVEMENT_SPEED);
                if (speed != null && s.num("speed-bonus") > 0) {
                    speed.setBaseValue(speed.getBaseValue() * (1.0 + s.num("speed-bonus")));
                }
            }
        }
    }

    private void startShield(MobState m, AbilitySpec s, long until) {
        if (m.shieldUntil <= Bukkit.getCurrentTick()) {
            m.savedName = m.entity.customName();
            m.savedNameVisible = m.entity.isCustomNameVisible();
        }
        m.shieldUntil = until;
        m.shieldReduction = s.num("reduction");
        m.entity.customName(msg.component(null, "combat.ability.shield-name", Msg.c("name", displayName(m))));
        m.entity.setCustomNameVisible(true);
    }

    private void endShield(MobState m) {
        if (m.shieldUntil == 0) {
            return;
        }
        m.shieldUntil = 0;
        if (m.entity.isValid()) {
            m.entity.customName(m.savedName);
            m.entity.setCustomNameVisible(m.savedNameVisible);
        }
        m.savedName = null;
    }

    /** Ability damage: the mob's damage per hit × multiplier, dealt by the mob (armor and enchants still count). */
    private void hurt(MobState m, Player p, double multiplier) {
        if (!(multiplier > 0)) {
            return;
        }
        double base = m.def.damage() > 0 ? m.def.damage() : attackDamage(m.entity);
        double amount = base * multiplier * m.damageMultiplier;
        if (!(amount > 0)) {
            return;
        }
        applying = true;
        try {
            p.damage(amount, m.entity);
        } finally {
            applying = false;
        }
    }

    private static double attackDamage(LivingEntity e) {
        AttributeInstance a = e.getAttribute(Attribute.ATTACK_DAMAGE);
        return a == null ? 2.0 : a.getValue();
    }

    // ------------------------------------------------------------------ players (zone only)

    /** Players of the mob's zone that abilities may touch: online, alive, survival/adventure, not in a safe region. */
    private List<Player> zonePlayers(Effect e) {
        return targets(e.mob);
    }

    private List<Player> targets(MobState m) {
        List<Player> out = new ArrayList<>();
        World world = m.entity.getWorld();
        RegionFlags flags = core.services().optional(RegionFlags.class).orElse(null);
        for (Player p : zones.players(m.zoneId)) {
            if (!p.isOnline() || p.isDead() || p.getWorld() != world) {
                continue;
            }
            GameMode mode = p.getGameMode();
            if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) {
                continue;
            }
            if (flags != null && flags.denied(p.getLocation(), RegionFlag.MOB_TARGET)) {
                continue;
            }
            out.add(p);
        }
        return out;
    }

    /** Players inside a circle (horizontal) and at most 3 blocks above or below its center. */
    private List<Player> inside(Effect e, Location center, double radius) {
        List<Player> out = new ArrayList<>();
        for (Player p : zonePlayers(e)) {
            Location l = p.getLocation();
            if (Math.abs(l.getY() - center.getY()) <= 3
                    && MobCombatMath.inside(l.getX() - center.getX(), l.getZ() - center.getZ(), radius)) {
                out.add(p);
            }
        }
        return out;
    }

    private Player nearest(MobState m, double range) {
        Location at = m.entity.getLocation();
        Player best = null;
        double bestSq = range * range;
        for (Player p : targets(m)) {
            double d = p.getLocation().distanceSquared(at);
            if (d <= bestSq) {
                bestSq = d;
                best = p;
            }
        }
        return best;
    }

    private Player farthest(MobState m, double range) {
        Location at = m.entity.getLocation();
        Player best = null;
        double bestSq = -1;
        double max = range * range;
        for (Player p : targets(m)) {
            double d = p.getLocation().distanceSquared(at);
            if (d <= max && d > bestSq) {
                bestSq = d;
                best = p;
            }
        }
        return best;
    }

    /** Players of the zone close enough to see the warning. */
    private List<Player> viewers(Effect e) {
        List<Player> out = new ArrayList<>();
        double max = (double) config.abilitiesEffectRange * config.abilitiesEffectRange;
        World world = e.center.getWorld();
        for (Player p : zones.players(e.mob.zoneId)) {
            if (p.isOnline() && p.getWorld() == world && p.getLocation().distanceSquared(e.center) <= max) {
                out.add(p);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ telegraph blocks and particles

    /** Floor blocks of a shape around a center (the top solid block within 3 below / 1 above), capped. */
    private List<Position> floor(Location center, List<int[]> shape) {
        List<Position> out = new ArrayList<>();
        World world = center.getWorld();
        if (world == null) {
            return out;
        }
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();
        int cap = config.abilitiesMaxRingBlocks;
        for (int[] o : shape) {
            if (out.size() >= cap) {
                break;
            }
            int x = cx + o[0];
            int z = cz + o[1];
            if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }
            for (int y = cy; y >= cy - 4; y--) {
                Block b = world.getBlockAt(x, y, z);
                if (b.getType().isSolid() && b.getRelative(0, 1, 0).isPassable()) {
                    out.add(Position.block(x, y, z));
                    break;
                }
            }
        }
        return out;
    }

    private void showBlocks(Effect e, BlockData data) {
        if (e.blocks.isEmpty()) {
            return;
        }
        Map<Position, BlockData> changes = new HashMap<>();
        for (Position pos : e.blocks) {
            changes.put(pos, data);
        }
        for (Player p : viewers(e)) {
            e.viewers.add(p.getUniqueId());
            p.sendMultiBlockChange(changes);
        }
    }

    private void restoreBlocks(Effect e) {
        if (e.blocks.isEmpty() || e.viewers.isEmpty()) {
            return;
        }
        World world = e.center.getWorld();
        if (world == null) {
            return;
        }
        Map<Position, BlockData> real = new HashMap<>();
        for (Position pos : e.blocks) {
            real.put(pos, world.getBlockData(pos.blockX(), pos.blockY(), pos.blockZ()));
        }
        for (UUID id : e.viewers) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.getWorld() == world) {
                p.sendMultiBlockChange(real);
            }
        }
        e.viewers.clear();
    }

    private void particles(Effect e, String name) {
        Particle particle = particle(name);
        World world = e.center.getWorld();
        if (particle == null || world == null || e.blocks.isEmpty()) {
            return;
        }
        int step = Math.max(1, e.blocks.size() / 16);
        for (int i = 0; i < e.blocks.size(); i += step) {
            Position pos = e.blocks.get(i);
            world.spawnParticle(particle, pos.blockX() + 0.5, pos.blockY() + 1.1, pos.blockZ() + 0.5, 1, 0.1, 0.05, 0.1, 0.0);
        }
    }

    private static Particle particle(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            Particle p = Particle.valueOf(name.trim().toUpperCase(Locale.ROOT));
            return p.getDataType() == Void.class ? p : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static BlockData block(String id, Material fallback) {
        Material m = id == null ? null : Material.matchMaterial(id.trim());
        return (m != null && m.isBlock() ? m : fallback).createBlockData();
    }

    // ------------------------------------------------------------------ helpers

    private static Vector flat(Vector v) {
        Vector out = new Vector(v.getX(), 0, v.getZ());
        if (out.lengthSquared() < 1e-9) {
            double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
            return new Vector(Math.cos(angle), 0, Math.sin(angle));
        }
        return out.normalize();
    }

    /** Solid floor and two passable blocks at the spot. */
    private static boolean standable(Location at) {
        World world = at.getWorld();
        if (world == null || !world.isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)) {
            return false;
        }
        Block feet = at.getBlock();
        return feet.isPassable() && feet.getRelative(0, 1, 0).isPassable() && feet.getRelative(0, -1, 0).getType().isSolid();
    }

    private Component displayName(MobState m) {
        if (m.savedName != null) {
            return m.savedName;
        }
        Component name = m.entity.customName();
        return name != null ? name : Component.translatable(m.entity.getType().translationKey());
    }
}
