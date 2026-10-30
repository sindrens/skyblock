package net.skybornmc.skyblock.stacking;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.stacking.StackKillProvider;
import net.skybornmc.skyblock.common.stacking.StackingService;
import net.skybornmc.skyblock.common.text.Msg;
import net.skybornmc.skyblock.common.util.Format;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.AbstractSkeleton;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Hoglin;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.MushroomCow;
import org.bukkit.entity.PiglinAbstract;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Steerable;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.LootContext;
import org.bukkit.loot.LootTable;
import org.bukkit.loot.Lootable;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Stacked mobs (owner S1–S13, SK1): spawner mobs and natural mobs in the stacking worlds merge into one entity that
 * carries the count; the name shows {@code Zombie x45}. One lethal hit kills one (or what a {@link StackKillProvider}
 * weapon allows), grinder damage kills the whole stack, drops and XP are multiplied (loot rolled a few times and scaled,
 * merged into stacked items). Spawner mobs have no AI; stacked and spawner mobs never despawn. Named, tamed, leashed,
 * saddled mobs, villagers and bosses never stack. Feeding a stack breeds one pair per food item.
 *
 * <p>Performance: merging happens only on spawn (and for mobs that walk, in a small budgeted task), using the
 * chunk-indexed {@link MobIndex}; no entity scans, no per-tick work.</p>
 */
final class MobStacker implements Listener {

    /** Never stack (owner S9, SK8): villagers and bosses. */
    private static final Set<EntityType> NEVER = EnumSet.of(EntityType.VILLAGER, EntityType.WANDERING_TRADER, EntityType.ZOMBIE_VILLAGER,
            EntityType.WARDEN, EntityType.WITHER, EntityType.ENDER_DRAGON, EntityType.ELDER_GUARDIAN, EntityType.ALLAY,
            EntityType.ARMOR_STAND, EntityType.PLAYER, EntityType.GIANT);
    /** Scoreboard tag other modules put on bosses. */
    static final String BOSS_TAG = "skyblock_boss";

    private final StackingModule module;
    private final MobIndex index = new MobIndex();
    private Set<EntityType> never = EnumSet.copyOf(NEVER);
    private Set<CreatureSpawnEvent.SpawnReason> reasons = EnumSet.noneOf(CreatureSpawnEvent.SpawnReason.class);
    private Set<String> wholeStackDamage = Set.of();
    /** True while this class spawns or copies a mob (its spawn event must not merge it). */
    private boolean internal;
    private boolean lootWarned;

    MobStacker(StackingModule module) {
        this.module = module;
    }

    MobIndex index() {
        return index;
    }

    void applyConfig() {
        StackingConfig config = module.config();
        Set<EntityType> types = EnumSet.copyOf(NEVER);
        for (String raw : config.mobNeverStack) {
            EntityType type = module.entityType(raw);
            if (type == null) {
                module.core().plugin().getLogger().warning("stacking.yml → mobs.never-stack: '" + raw + "' is not a mob type, skipped");
            } else {
                types.add(type);
            }
        }
        never = types;
        Set<CreatureSpawnEvent.SpawnReason> r = EnumSet.noneOf(CreatureSpawnEvent.SpawnReason.class);
        for (String raw : config.mobSpawnReasons) {
            try {
                r.add(CreatureSpawnEvent.SpawnReason.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                module.core().plugin().getLogger().warning("stacking.yml → mobs.spawn-reasons: '" + raw + "' is not a spawn reason, skipped");
            }
        }
        reasons = r;
        wholeStackDamage = Set.copyOf(config.wholeStackDamage.stream().map(SpawnerTiers::normalize).toList());
    }

    // ================================================================== data

    static int count(Entity entity) {
        if (!(entity instanceof LivingEntity)) {
            return 0;
        }
        Integer stored = entity.getPersistentDataContainer().get(StackKeys.MOB_COUNT, PersistentDataType.INTEGER);
        return stored == null ? 1 : Math.max(1, stored);
    }

    static boolean isSpawnerMob(Entity entity) {
        return entity.getPersistentDataContainer().has(StackKeys.SPAWNER_MOB, PersistentDataType.BYTE);
    }

    /** Sets the stack size, name and despawn/pickup flags (owner S8, S12). */
    void setCount(LivingEntity entity, int count) {
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        Integer before = pdc.get(StackKeys.MOB_COUNT, PersistentDataType.INTEGER);
        pdc.set(StackKeys.MOB_COUNT, PersistentDataType.INTEGER, Math.max(1, count));
        boolean stacked = count > 1;
        if (stacked || isSpawnerMob(entity)) {
            entity.setRemoveWhenFarAway(false);
        }
        if (stacked) {
            entity.setCanPickupItems(false);
            Component name = module.msg().component(Audience.empty(), "mob-name",
                    Msg.c("name", Component.translatable(entity.getType().translationKey())), Msg.p("count", Format.number(count)));
            entity.customName(name);
            entity.setCustomNameVisible(true);
        } else if (before != null && before > 1) {
            // Stack names exist only from 2 mobs on, so a name on a single mob is always a player's name tag.
            entity.customName(null);
            entity.setCustomNameVisible(false);
        }
    }

    /** Types that can ever be stacked (checked once per entity when indexing). */
    boolean stackableType(Entity entity) {
        return entity instanceof Mob && !never.contains(entity.getType());
    }

    /** Owner S9 and the never-stack rules, checked whenever a mob would merge (they can change any time). */
    boolean canStack(LivingEntity entity) {
        // No isValid() here: a mob in its spawn event is not in the world yet. Callers check isValid() on stacks.
        if (entity.isDead() || !stackableType(entity) || !module.eligible(entity.getWorld())) {
            return false;
        }
        Set<String> tags = entity.getScoreboardTags();
        if (!tags.isEmpty() && (tags.contains(StackingService.NO_STACK_TAG) || tags.contains(BOSS_TAG))) {
            return false;
        }
        if (entity instanceof Tameable tameable && tameable.isTamed()) {
            return false;
        }
        if (entity instanceof Steerable steerable && steerable.hasSaddle()) {
            return false;
        }
        if (entity instanceof Mob mob && mob.isLeashed()) {
            return false;
        }
        if (entity.isInsideVehicle() || !entity.getPassengers().isEmpty()) {
            return false;
        }
        return count(entity) > 1 || entity.customName() == null;
    }

    /** Same kind of mob: type, spawner or natural, baby or adult, and what changes the drops (color, size, variant). */
    static boolean compatible(LivingEntity a, LivingEntity b) {
        if (a.getType() != b.getType() || isSpawnerMob(a) != isSpawnerMob(b)) {
            return false;
        }
        if (a instanceof Ageable x && b instanceof Ageable y && x.isAdult() != y.isAdult()) {
            return false;
        }
        if (a instanceof Sheep x && b instanceof Sheep y && (x.getColor() != y.getColor() || x.isSheared() != y.isSheared())) {
            return false;
        }
        if (a instanceof Slime x && b instanceof Slime y && x.getSize() != y.getSize()) {
            return false;
        }
        if (a instanceof MushroomCow x && b instanceof MushroomCow y && x.getVariant() != y.getVariant()) {
            return false;
        }
        return !(a instanceof Creeper x && b instanceof Creeper y && x.isPowered() != y.isPowered());
    }

    // ================================================================== index upkeep (called by the module's add/remove listener)

    void track(LivingEntity entity) {
        if (stackableType(entity) && module.eligible(entity.getWorld())) {
            index.add(entity, !isSpawnerMob(entity) && (!(entity instanceof Mob mob) || mob.isAware()));
        }
    }

    void untrack(LivingEntity entity) {
        index.remove(entity);
    }

    // ================================================================== merging

    /** The nearest compatible stack within {@code radius} of {@code at} with room for {@code amount}, or null. */
    private LivingEntity target(LivingEntity joining, Location at, double radius, int amount) {
        int max = module.config().mobMaxStack;
        double r2 = radius * radius;
        LivingEntity[] best = {null};
        double[] bestDistance = {Double.MAX_VALUE};
        index.near(at.getWorld().getUID(), at.getX(), at.getZ(), radius, other -> {
            if (other == joining || !other.isValid() || !compatible(joining, other) || StackMath.room(count(other), max) < amount
                    || !canStack(other)) {
                return;
            }
            double d = other.getLocation().distanceSquared(at);
            if (d <= r2 && d < bestDistance[0]) {
                bestDistance[0] = d;
                best[0] = other;
            }
        });
        return best[0];
    }

    /** Stacks of {@code type} in the chunk of {@code at}. */
    private int stacksInChunk(Location at, EntityType type, LivingEntity except) {
        int n = 0;
        for (LivingEntity e : index.chunk(at.getWorld().getUID(), at.getBlockX() >> 4, at.getBlockZ() >> 4)) {
            if (e != except && e.getType() == type && e.isValid()) {
                n++;
            }
        }
        return n;
    }

    /** Any compatible stack with room in the chunk of {@code at} (when the chunk has its 8 stacks). */
    private LivingEntity chunkTarget(LivingEntity joining, Location at, int amount) {
        int max = module.config().mobMaxStack;
        for (LivingEntity e : index.chunk(at.getWorld().getUID(), at.getBlockX() >> 4, at.getBlockZ() >> 4)) {
            if (e != joining && e.isValid() && compatible(joining, e) && StackMath.room(count(e), max) >= amount && canStack(e)) {
                return e;
            }
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        if (internal || !reasons.contains(event.getSpawnReason())) {
            return;
        }
        LivingEntity entity = event.getEntity();
        if (!module.eligible(entity.getWorld()) || !canStack(entity)) {
            return;
        }
        long start = module.perf().start();
        try {
            Location at = event.getLocation();
            int amount = count(entity);
            LivingEntity target = target(entity, at, module.config().mobMergeRadius, amount);
            if (target == null && stacksInChunk(at, entity.getType(), entity) >= module.config().mobMaxStacksPerChunk) {
                target = chunkTarget(entity, at, amount);
                if (target == null) {
                    event.setCancelled(true); // owner SK1: the chunk is full of this mob
                    return;
                }
            }
            if (target != null) {
                setCount(target, count(target) + amount);
                event.setCancelled(true);
            }
        } finally {
            module.perf().end(start);
        }
    }

    /**
     * Moving mobs (natural, with AI) that walked close to a compatible stack merge into it; also re-files them in the
     * index. Visits at most {@code mobs.natural-merge-budget} mobs per run.
     */
    void mergeMoving() {
        long start = module.perf().start();
        try {
            int budget = Math.min(module.config().naturalMergeBudget, index.movingSize());
            double radius = module.config().mobMergeRadius;
            for (int i = 0; i < budget; i++) {
                LivingEntity mob = index.nextMoving();
                if (mob == null) {
                    return;
                }
                index.update(mob);
                if (!canStack(mob)) {
                    continue;
                }
                int amount = count(mob);
                LivingEntity target = target(mob, mob.getLocation(), radius, amount);
                if (target == null) {
                    continue;
                }
                // The bigger stack stays (fewer renames, the visible stack keeps its place).
                LivingEntity keep = count(target) >= amount ? target : mob;
                LivingEntity gone = keep == target ? mob : target;
                int total = count(keep) + count(gone);
                index.remove(gone);
                gone.remove();
                setCount(keep, total);
            }
        } finally {
            module.perf().end(start);
        }
    }

    // ================================================================== spawner output

    /**
     * Adds {@code amount} spawner mobs of {@code type} around a spawner block: into nearby spawner stacks first, then as
     * new stacks (at most {@code mobs.max-stacks-per-chunk} of the type in the chunk); the rest is not spawned.
     *
     * @return mobs actually added
     */
    long addSpawned(Location spawner, EntityType type, long amount) {
        if (amount <= 0 || type.getEntityClass() == null) {
            return 0;
        }
        StackingConfig config = module.config();
        World world = spawner.getWorld();
        double radius = config.mobMergeRadius;
        double r2 = radius * radius;
        List<LivingEntity> stacks = new ArrayList<>();
        index.near(world.getUID(), spawner.getX(), spawner.getZ(), radius, e -> {
            if (e.getType() == type && isSpawnerMob(e) && e.isValid() && canStack(e) && e.getLocation().distanceSquared(spawner) <= r2) {
                stacks.add(e);
            }
        });
        stacks.sort((a, b) -> Integer.compare(count(b), count(a))); // fill the biggest first: fewer entities
        int[] existing = new int[stacks.size()];
        for (int i = 0; i < existing.length; i++) {
            existing[i] = count(stacks.get(i));
        }
        int freeStacks = Math.max(0, config.mobMaxStacksPerChunk - stacksInChunk(spawner, type, null));
        StackMath.Distribution plan = StackMath.distribute(amount, existing, config.mobMaxStack, freeStacks);
        for (int i = 0; i < existing.length; i++) {
            if (plan.added()[i] > 0) {
                setCount(stacks.get(i), existing[i] + plan.added()[i]);
            }
        }
        long added = plan.placed();
        for (int size : plan.newStacks()) {
            if (spawnStack(spawner, type, size) == null) {
                added -= size;
            }
        }
        return added;
    }

    /** Spawns one stack of spawner mobs next to the spawner (light, space and blocks ignored: owner S25). */
    private LivingEntity spawnStack(Location spawner, EntityType type, int size) {
        Location at = spawnSpot(spawner);
        Class<? extends Entity> cls = type.getEntityClass();
        if (cls == null || !LivingEntity.class.isAssignableFrom(cls)) {
            return null;
        }
        internal = true;
        try {
            Entity spawned = spawner.getWorld().spawn(at, cls, false, e -> {
                if (e instanceof LivingEntity living) {
                    prepareSpawnerMob(living, size);
                }
            });
            return spawned instanceof LivingEntity living && living.isValid() ? living : null;
        } catch (RuntimeException e) {
            module.core().plugin().getLogger().log(Level.WARNING, "Stacking: could not spawn " + type.getKey() + " for a spawner", e);
            return null;
        } finally {
            internal = false;
        }
    }

    private void prepareSpawnerMob(LivingEntity living, int size) {
        living.getPersistentDataContainer().set(StackKeys.SPAWNER_MOB, PersistentDataType.BYTE, (byte) 1);
        if (living instanceof Mob mob) {
            mob.setAware(false); // owner S7: no AI for spawner mobs
            mob.getEquipment().clear(); // owner SK9: vanilla loot only
        }
        if (living instanceof Slime slime) {
            slime.setSize(1); // the smallest size drops the loot and never splits
        }
        if (!module.config().spawnerMobsBurnInDay) {
            if (living instanceof Zombie zombie) {
                zombie.setShouldBurnInDay(false);
            } else if (living instanceof AbstractSkeleton skeleton) {
                skeleton.setShouldBurnInDay(false);
            }
        }
        if (living instanceof PiglinAbstract piglin) {
            piglin.setImmuneToZombification(true);
        } else if (living instanceof Hoglin hoglin) {
            hoglin.setImmuneToZombification(true);
        }
        setCount(living, size);
    }

    private Location spawnSpot(Location spawner) {
        int range = module.config().spawnRange;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        World world = spawner.getWorld();
        for (int attempt = 0; attempt < 6 && range > 0; attempt++) {
            int x = spawner.getBlockX() + random.nextInt(-range, range + 1);
            int z = spawner.getBlockZ() + random.nextInt(-range, range + 1);
            int y = spawner.getBlockY() + random.nextInt(-1, 2);
            if ((x == spawner.getBlockX() && z == spawner.getBlockZ()) || !world.isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }
            if (world.getBlockAt(x, y, z).isPassable() && world.getBlockAt(x, y + 1, z).isPassable()) {
                return new Location(world, x + 0.5, y, z + 0.5);
            }
        }
        return spawner.clone().add(0.5, 1, 0.5);
    }

    // ================================================================== kills (owner S4–S6, SK9, SK10)

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof Player || !module.eligible(entity.getWorld())) {
            return;
        }
        boolean spawnerMob = isSpawnerMob(entity);
        int stack = count(entity);
        if (stack <= 1 && !spawnerMob) {
            return;
        }
        long start = module.perf().start();
        try {
            Player killer = entity.getKiller();
            boolean whole = wholeStackDamage.contains(event.getDamageSource().getDamageType().getKey().getKey());
            int weaponKills = 1;
            ItemStack weapon = killer == null ? null : killer.getInventory().getItemInMainHand();
            if (!whole && killer != null && stack > 1) {
                StackKillProvider provider = module.core().services().optional(StackKillProvider.class).orElse(null);
                if (provider != null) {
                    try {
                        weaponKills = provider.killsPerHit(killer, weapon, entity, stack);
                    } catch (RuntimeException e) {
                        module.core().plugin().getLogger().log(Level.WARNING, "StackKillProvider failed; one kill counted", e);
                    }
                }
            }
            int killed = StackMath.killed(stack, whole, weaponKills);
            int extra = killed - 1;
            Location at = entity.getLocation();
            List<Drop> extras = extra > 0 ? extraLoot(entity, killer, extra, event.getDrops()) : new ArrayList<>();
            if (spawnerMob) {
                applyRareDrops(entity.getType(), event.getDrops(), extras, killed);
            }
            event.setDroppedExp(StackMath.xp(event.getDroppedExp(), killed));
            for (Drop drop : extras) {
                module.items().drop(at, drop.prototype(), drop.amount());
            }
            int remainder = stack - killed;
            if (remainder > 0) {
                keepRest(event, entity, remainder);
            }
        } finally {
            module.perf().end(start);
        }
    }

    /**
     * Loot of {@code extra} more mobs: the loot table rolled {@code mobs.loot-samples} times and scaled, so a 1,024 stack
     * costs the same as a small one. Falls back to the dying mob's drops times {@code extra} if the table can't be rolled.
     * Equipment (e.g. picked-up items) is never multiplied: only the loot table counts.
     */
    private List<Drop> extraLoot(LivingEntity entity, Player killer, int extra, List<ItemStack> ownDrops) {
        List<ItemStack> prototypes = new ArrayList<>();
        List<long[]> sums = new ArrayList<>();
        int samples = Math.min(extra, module.config().lootSamples);
        LootTable table = entity instanceof Lootable lootable ? lootable.getLootTable() : null;
        boolean rolled = false;
        if (table != null) {
            try {
                LootContext.Builder builder = new LootContext.Builder(entity.getLocation()).lootedEntity(entity);
                if (killer != null) {
                    builder.killer(killer); // looting and other weapon effects come from the killer's held item
                }
                LootContext context = builder.build();
                ThreadLocalRandom random = ThreadLocalRandom.current();
                for (int s = 0; s < samples; s++) {
                    addAll(prototypes, sums, table.populateLoot(random, context));
                }
                rolled = true;
            } catch (RuntimeException e) {
                prototypes.clear();
                sums.clear();
                if (!lootWarned) {
                    lootWarned = true;
                    module.core().plugin().getLogger().log(Level.WARNING, "Stacking: loot table of " + entity.getType().getKey()
                            + " could not be rolled; using the dying mob's drops for the whole stack (logged once)", e);
                }
            }
        }
        if (!rolled) {
            addAll(prototypes, sums, ownDrops);
            samples = 1;
        }
        List<Drop> out = new ArrayList<>();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < prototypes.size(); i++) {
            long amount = StackMath.scale(sums.get(i)[0], samples, extra, random.nextDouble());
            if (amount > 0) {
                out.add(new Drop(prototypes.get(i), amount));
            }
        }
        return out;
    }

    /**
     * Owner SK10: strong drops of spawner mobs (evoker totems, shulker shells) only with the configured, very rare chance
     * per mob killed, instead of the vanilla one.
     */
    private void applyRareDrops(EntityType type, List<ItemStack> own, List<Drop> extras, int killed) {
        for (SpawnerTiers.RareDrop rare : module.rareDrops()) {
            if (!rare.mob().equals(type.getKey().getKey())) {
                continue;
            }
            Material material = Material.matchMaterial(rare.item());
            if (material == null) {
                continue;
            }
            own.removeIf(i -> i.getType() == material);
            extras.removeIf(d -> d.prototype().getType() == material);
            long hits = StackMath.binomial(killed, rare.chance(), () -> ThreadLocalRandom.current().nextDouble());
            if (hits > 0) {
                extras.add(new Drop(new ItemStack(material), hits));
            }
        }
    }

    /**
     * The rest of the stack lives on as a copy of the dying mob (so every other plugin sees a normal death: skills,
     * logs). Equipment is cleared so nothing can be duplicated; if copying fails the death is cancelled instead.
     */
    private void keepRest(EntityDeathEvent event, LivingEntity dying, int remainder) {
        Entity copy = null;
        internal = true;
        try {
            copy = dying.copy(dying.getLocation());
        } catch (RuntimeException e) {
            module.core().plugin().getLogger().log(Level.FINE, "Stacking: copy failed, reviving instead", e);
        } finally {
            internal = false;
        }
        if (copy instanceof LivingEntity living && living.isValid()) {
            heal(living);
            living.setFireTicks(0);
            living.setFallDistance(0);
            living.setVelocity(new org.bukkit.util.Vector());
            if (living instanceof Mob mob) {
                mob.getEquipment().clear();
            }
            setCount(living, remainder);
            return;
        }
        if (copy != null) {
            copy.remove();
        }
        // Fallback: the mob does not die; its drops (already scaled) are dropped by hand.
        event.setCancelled(true);
        event.setReviveHealth(maxHealth(dying));
        Location at = dying.getLocation();
        for (ItemStack drop : event.getDrops()) {
            module.items().drop(at, asOne(drop), drop.getAmount());
        }
        if (event.getDroppedExp() > 0) {
            int xp = event.getDroppedExp();
            at.getWorld().spawn(at, ExperienceOrb.class, orb -> orb.setExperience(xp));
        }
        setCount(dying, remainder);
    }

    private static double maxHealth(LivingEntity entity) {
        AttributeInstance attribute = entity.getAttribute(Attribute.MAX_HEALTH);
        return attribute == null ? 20 : attribute.getValue();
    }

    private static void heal(LivingEntity entity) {
        entity.setHealth(Math.max(1, maxHealth(entity)));
    }

    // ------------------------------------------------------------------ loot helpers

    /** {@code amount} of {@code prototype} (an item stack holds at most 99, so the amount is kept beside it). */
    record Drop(ItemStack prototype, long amount) {
    }

    private static ItemStack asOne(ItemStack stack) {
        ItemStack one = stack.clone();
        one.setAmount(1);
        return one;
    }

    private static void addAll(List<ItemStack> prototypes, List<long[]> sums, Collection<ItemStack> loot) {
        for (ItemStack stack : loot) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            int found = -1;
            for (int i = 0; i < prototypes.size(); i++) {
                if (prototypes.get(i).isSimilar(stack)) {
                    found = i;
                    break;
                }
            }
            if (found < 0) {
                prototypes.add(asOne(stack));
                sums.add(new long[]{stack.getAmount()});
            } else {
                sums.get(found)[0] += stack.getAmount();
            }
        }
    }

    // ================================================================== interactions (owner S9, S10)

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof LivingEntity entity) || count(entity) < 2 || !module.eligible(entity.getWorld())) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack hand = event.getHand() == EquipmentSlot.OFF_HAND ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand();
        Material type = hand.getType();
        if (type == Material.NAME_TAG) {
            event.setCancelled(true);
            deny(player, "stack.no-name-tag");
            return;
        }
        if (type == Material.SADDLE && entity instanceof Steerable) {
            event.setCancelled(true);
            deny(player, "stack.no-saddle");
            return;
        }
        if (entity instanceof Animals animals && !hand.isEmpty() && animals.isBreedItem(hand)) {
            event.setCancelled(true);
            if (animals.isAdult()) {
                breed(player, animals, hand, event.getHand());
            }
        }
    }

    /** Owner S10: every food item breeds one pair; babies join the stack. */
    private void breed(Player player, Animals stack, ItemStack hand, EquipmentSlot slot) {
        long start = module.perf().start();
        try {
            PersistentDataContainer pdc = stack.getPersistentDataContainer();
            long now = System.currentTimeMillis();
            long until = pdc.getOrDefault(StackKeys.BREED_UNTIL, PersistentDataType.LONG, 0L);
            int busy = now < until ? pdc.getOrDefault(StackKeys.BREED_BUSY, PersistentDataType.INTEGER, 0) : 0;
            boolean creative = player.getGameMode() == GameMode.CREATIVE;
            int food = creative ? 64 : hand.getAmount();
            int count = count(stack);
            StackMath.Breed result = StackMath.breed(count, busy, food, module.config().mobMaxStack);
            switch (result.result()) {
                case COOLDOWN -> {
                    deny(player, "breed.cooldown", Msg.p("time", Format.duration(java.time.Duration.ofMillis(Math.max(0, until - now)))));
                    return;
                }
                case FULL -> {
                    deny(player, "breed.full", Msg.p("max", Format.number(module.config().mobMaxStack)));
                    return;
                }
                case OK -> {
                }
                default -> {
                    return;
                }
            }
            int babies = result.babies();
            if (!creative) {
                ItemStack left = hand.clone();
                left.setAmount(hand.getAmount() - babies);
                if (slot == EquipmentSlot.OFF_HAND) {
                    player.getInventory().setItemInOffHand(left.getAmount() <= 0 ? null : left);
                } else {
                    player.getInventory().setItemInMainHand(left.getAmount() <= 0 ? null : left);
                }
            }
            pdc.set(StackKeys.BREED_BUSY, PersistentDataType.INTEGER, result.busyAfter());
            pdc.set(StackKeys.BREED_UNTIL, PersistentDataType.LONG, now + module.config().breedCooldownSeconds * 1000L);
            setCount(stack, count + babies);
            int xp = 0;
            ThreadLocalRandom random = ThreadLocalRandom.current();
            int lo = Math.min(module.config().breedXpMin, module.config().breedXpMax);
            int hi = Math.max(module.config().breedXpMin, module.config().breedXpMax);
            for (int i = 0; i < babies; i++) {
                xp += random.nextInt(lo, hi + 1);
            }
            Location at = stack.getLocation();
            if (xp > 0) {
                int total = xp;
                stack.getWorld().spawn(at, ExperienceOrb.class, orb -> orb.setExperience(total));
            }
            stack.getWorld().spawnParticle(Particle.HEART, at.clone().add(0, stack.getHeight() + 0.3, 0), Math.min(7, babies), 0.4, 0.3, 0.4);
            module.msg().actionBar(player, "breed.done", Msg.p("babies", Format.number(babies)),
                    Msg.p("count", Format.number(count + babies)));
            module.core().sounds().play(player, "success");
        } finally {
            module.perf().end(start);
        }
    }

    private void deny(Player player, String key, net.kyori.adventure.text.minimessage.tag.resolver.TagResolver... placeholders) {
        module.msg().send(player, key, placeholders);
        module.core().sounds().play(player, "error");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLeash(PlayerLeashEntityEvent event) {
        if (count(event.getEntity()) > 1 && module.eligible(event.getEntity().getWorld())) {
            event.setCancelled(true);
            deny(event.getPlayer(), "stack.no-leash");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTame(EntityTameEvent event) {
        if (count(event.getEntity()) > 1 && module.eligible(event.getEntity().getWorld())) {
            event.setCancelled(true);
            if (event.getOwner() instanceof Player player) {
                deny(player, "stack.no-tame");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMount(EntityMountEvent event) {
        if (count(event.getMount()) > 1 && module.eligible(event.getMount().getWorld())) {
            event.setCancelled(true);
            if (event.getEntity() instanceof Player player) {
                deny(player, "stack.no-ride");
            }
        }
    }

    /** Shearing a stack of sheep shears all of them: wool times the stack, merged into one item. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShear(PlayerShearEntityEvent event) {
        Entity entity = event.getEntity();
        int count = count(entity);
        if (count < 2 || !module.eligible(entity.getWorld())) {
            return;
        }
        if (!(entity instanceof Sheep) || !module.config().shearWholeStack) {
            event.setCancelled(true);
            deny(event.getPlayer(), "stack.no-shear");
            return;
        }
        Location at = entity.getLocation();
        for (ItemStack drop : event.getDrops()) {
            module.items().drop(at, asOne(drop), (long) drop.getAmount() * count);
        }
        event.setDrops(List.of());
    }

    /** A stack that turns into another mob (lightning, curing, drowning) stays a stack of the same size. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTransform(EntityTransformEvent event) {
        int count = count(event.getEntity());
        if (count < 2 || event.getTransformedEntities().size() != 1 || !(event.getTransformedEntity() instanceof LivingEntity result)) {
            return;
        }
        if (stackableType(result) && module.eligible(result.getWorld())) {
            if (isSpawnerMob(event.getEntity())) {
                result.getPersistentDataContainer().set(StackKeys.SPAWNER_MOB, PersistentDataType.BYTE, (byte) 1);
            }
            setCount(result, count);
        }
    }
}
