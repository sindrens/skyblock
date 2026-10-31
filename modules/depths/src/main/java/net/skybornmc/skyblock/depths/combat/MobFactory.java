package net.skybornmc.skyblock.depths.combat;

import net.skybornmc.skyblock.common.mob.MobDef;
import net.skybornmc.skyblock.common.mob.MobStats;
import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.skills.SkillService;
import net.skybornmc.skyblock.common.stacking.StackingService;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.AbstractSkeleton;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Raider;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Zombie;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Locale;

/**
 * Spawns Depths mobs from their {@link MobDef} (M4.8): attributes, name, tags, no persistence (never saved with the
 * chunk, so a crash leaves no orphans), no item pickup, no burning, no raids. Ambient mobs are hidden from everyone
 * but their player from the very first packet ({@code visibleByDefault = false} before the entity is added).
 */
public final class MobFactory {

    /** Scoreboard tag on every Depths mob (orphan cleanup, block-change guard). */
    static final String TAG = "skyblock_depths_mob";
    /** Extra tag on per-player ambient mobs. */
    static final String AMBIENT_TAG = "skyblock_depths_ambient";

    private final Plugin plugin;
    private final NamespacedKey idKey;

    MobFactory(Plugin plugin) {
        this.plugin = plugin;
        this.idKey = new NamespacedKey(plugin, "depths_mob");
    }

    public static MobFactory create(Plugin plugin) {
        return new MobFactory(plugin);
    }

    /** True for entity type ids that can be spawned as a mob (mobs.yml validation). */
    public static boolean spawnableMob(String id) {
        if (id == null || id.isBlank() || !id.matches("[a-z0-9_]+")) {
            return false;
        }
        EntityType type = Registry.ENTITY_TYPE.get(NamespacedKey.minecraft(id.toLowerCase(Locale.ROOT)));
        return type != null && type.isSpawnable() && type.isAlive() && type.getEntityClass() != null
                && Mob.class.isAssignableFrom(type.getEntityClass());
    }

    /** The mob id stored on a Depths mob, or {@code null}. */
    String idOf(Entity entity) {
        return entity.getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
    }

    /**
     * Spawns a mob. Returns {@code null} if the type is unknown or the spawn was refused.
     *
     * @param name   custom name ({@code null} = vanilla name)
     * @param viewer the only player who sees it (ambient), {@code null} = everyone (combat)
     */
    LivingEntity spawn(Location at, MobDef def, Component name, Player viewer, boolean silent) {
        return spawn(at, def, name, viewer, silent, 1.0, false);
    }

    /**
     * Spawns a mob with an M8.1 health multiplier (elites) and optionally an always-visible name.
     *
     * @param healthMultiplier max health factor (1 = as configured; only used when the mob sets a health)
     * @param forceNameVisible name tag always shown, whatever {@code name-visible} says (elites)
     */
    LivingEntity spawn(Location at, MobDef def, Component name, Player viewer, boolean silent, double healthMultiplier,
                       boolean forceNameVisible) {
        World world = at.getWorld();
        EntityType type = Registry.ENTITY_TYPE.get(NamespacedKey.minecraft(def.type()));
        if (world == null || type == null || type.getEntityClass() == null || !Mob.class.isAssignableFrom(type.getEntityClass())) {
            return null;
        }
        boolean ambient = viewer != null;
        Entity spawned = world.spawn(at, type.getEntityClass(), true, e -> configure(e, def, name, ambient, silent,
                healthMultiplier, forceNameVisible));
        if (!(spawned instanceof LivingEntity living) || !spawned.isValid()) {
            if (spawned != null && spawned.isValid()) {
                spawned.remove();
            }
            return null;
        }
        if (ambient) {
            viewer.showEntity(plugin, living);
        }
        return living;
    }

    private void configure(Entity e, MobDef def, Component name, boolean ambient, boolean silent, double healthMultiplier,
                           boolean forceNameVisible) {
        if (ambient) {
            e.setVisibleByDefault(false);
        }
        e.setPersistent(false);
        e.addScoreboardTag(TAG);
        e.addScoreboardTag(SkillService.NO_XP_TAG);
        e.addScoreboardTag(StackingService.NO_STACK_TAG);
        if (ambient) {
            e.addScoreboardTag(AMBIENT_TAG);
            e.setSilent(silent);
        }
        e.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, def.id());
        if (!(e instanceof Mob mob)) {
            return;
        }
        mob.setRemoveWhenFarAway(false);
        mob.setCanPickupItems(false);
        if (ambient) {
            mob.setCollidable(false);
        }
        if (mob instanceof Slime slime && def.size() > 0) {
            slime.setSize(def.size()); // before the attributes: setSize resets health
        }
        if (mob instanceof Ageable ageable) {
            ageable.setAdult();
        }
        if (mob instanceof Zombie zombie) {
            zombie.setShouldBurnInDay(false);
        }
        if (mob instanceof AbstractSkeleton skeleton) {
            skeleton.setShouldBurnInDay(false);
        }
        if (mob instanceof Raider raider) {
            raider.setCanJoinRaid(false);
            raider.setPatrolLeader(false);
        }
        base(mob, Attribute.SCALE, def.scale() != 1.0 ? def.scale() : 0);
        base(mob, Attribute.MOVEMENT_SPEED, def.speed());
        base(mob, Attribute.ATTACK_DAMAGE, def.damage());
        double health = def.health() * (healthMultiplier > 0 ? healthMultiplier : 1.0);
        if (health > 0 && base(mob, Attribute.MAX_HEALTH, health)) {
            mob.setHealth(health);
        }
        // M8.1 optional stats: only touched when mobs.yml sets them (unset = vanilla, as before).
        MobStats stats = def.stats();
        if (!stats.vanillaKnockback()) {
            AttributeInstance kb = mob.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
            if (kb != null) {
                kb.setBaseValue(stats.knockbackResistance());
            }
        }
        base(mob, Attribute.FOLLOW_RANGE, stats.followRange());
        if (name != null) {
            mob.customName(name);
            mob.setCustomNameVisible(def.nameVisible() || forceNameVisible);
        }
        mob.setGlowing(def.glowing());
    }

    /** Sets an attribute's base value when {@code value > 0} and the mob has it. */
    private static boolean base(Mob mob, Attribute attribute, double value) {
        if (!(value > 0)) {
            return false;
        }
        AttributeInstance a = mob.getAttribute(attribute);
        if (a == null) {
            return false;
        }
        a.setBaseValue(value);
        return true;
    }
}
