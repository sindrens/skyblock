package net.skybornmc.skyblock.common.stacking;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Stacked mobs, items and spawners as seen by other modules (published by the stacking module:
 * {@code core.services().optional(StackingService.class)}). Main thread only.
 *
 * <p>Entities with the scoreboard tag {@link #NO_STACK_TAG} never stack (for modules that spawn their own mobs).</p>
 */
public interface StackingService {

    /** Scoreboard tag: this entity is never merged into a stack. */
    String NO_STACK_TAG = "skyblock_no_stack";

    /** Mobs in this entity's stack: 1 for a normal mob, 0 for anything that is not a living entity. */
    int mobCount(Entity entity);

    /** Items held by a dropped item entity (can be far above 64), 0 for a removed entity. */
    long itemCount(Item item);

    /** Spawners stacked in this block, 0 if it is not a stacked spawner. */
    int spawnerCount(Block block);

    /**
     * The loaded chunk with the most hoppers (owner F13).
     *
     * @param threshold staff are alerted above this many hoppers in one chunk ({@code stacking.yml → hoppers.alert-above})
     */
    record HopperHotspot(int chunkX, int chunkZ, int count, int threshold) {
    }

    /** Hoppers in the loaded chunk at {@code location} (cached count, owner F13); 0 outside the stacking worlds. */
    default int hopperCount(Location location) {
        return 0;
    }

    /**
     * The loaded chunk with the most hoppers inside a block rectangle (both corners inclusive), e.g. an island's bounds
     * for {@code /isadmin info}; empty when no loaded chunk there has a hopper. Cached counts, no scan.
     */
    default java.util.Optional<HopperHotspot> busiestHopperChunk(String world, int minX, int minZ, int maxX, int maxZ) {
        return java.util.Optional.empty();
    }

    /**
     * Spawner tier (1-5, {@code stacking.yml → spawners.tier-N}) of a mob type id ({@code zombie}); empty for types that
     * can't be spawners. The island level values spawners by tier (PHASE-5 M5.2). Any thread.
     */
    default java.util.OptionalInt spawnerTier(String type) {
        return java.util.OptionalInt.empty();
    }

    /**
     * Stacked spawners placed on an island (sum of every block's stack), from memory; 0 when unknown or not supported.
     * The disband confirm shows what is lost (owner R3-30). Main thread.
     */
    default long spawnersOnIsland(java.util.UUID islandId) {
        return 0;
    }

    /** True if the entity came from a stacked spawner (no AI, vanilla loot only). */
    boolean fromStackedSpawner(Entity entity);

    /**
     * Stacks (two or more mobs) near a location, nearest first, in the stacking worlds only; never bosses, named, tamed or
     * never-stack mobs. Uses the module's chunk index (no entity scan).
     */
    List<LivingEntity> stacksNear(Location at, double radius);

    /**
     * Batch kill API (ENCHANTS §7.2, E3): kills up to {@code count} mobs of a stack at once without a death event. Loot is
     * rolled a few times and scaled to all killed mobs (plus the weapon's {@link StackKillProvider#bonus}), merged into
     * stacked item drops; vanilla XP comes as one orb; strong spawner drops keep their rare chance per mob. The stack
     * shrinks, or is removed when every mob died. Fires one {@link StackKillEvent} ({@link StackKillEvent.Cause#AREA}) so
     * skill XP, tool XP and book drops are paid for the batch.
     *
     * <p>The caller checks the rules (island permissions, enchant levels); this only refuses entities that are no stack
     * (players, bosses, removed entities, other worlds). Main thread.</p>
     *
     * @param stack  the stacked mob
     * @param count  mobs to kill (clamped to the stack size)
     * @param killer the player credited (loot context, XP, events)
     * @return mobs killed (0 when refused)
     */
    int killFromStack(LivingEntity stack, int count, Player killer);

    /**
     * Spawner types of one tier (1-5) as {@code stacking.yml} lists them (crate and Black Market rewards pick one at
     * random, PHASE-10 M10.1). Empty when the tier has none or the stacking module does not support it.
     */
    default List<String> spawnerTypes(int tier) {
        return List.of();
    }

    /**
     * New spawner items of {@code type} (1-64 per stack; callers split bigger amounts), registered with {@code source}
     * for dupe detection (e.g. {@code crate:blaze}). Empty if the type is not allowed. Main thread.
     */
    default java.util.Optional<org.bukkit.inventory.ItemStack> createSpawner(String type, int amount, String source) {
        return java.util.Optional.empty();
    }

    /**
     * R3-13b: opens the staff view of the frozen stacked spawners a player placed (block lost in a crash or world edit),
     * with a restore button per entry (the "Frozen spawners" button in {@code /inspect}; needs
     * {@code skyblock.spawner.restore}). Does nothing when the stacking module does not support it.
     */
    default void openFrozenSpawners(Player staff, java.util.UUID player, String name, net.skybornmc.skyblock.common.menu.Menu back) {
    }

    /**
     * Owner F2 (diminishing spawner output per island and tier, {@code stacking.yml → spawners.efficiency}): how well the
     * <b>next</b> spawner of {@code type} this player places on their island would work, in percent of a full spawner (the
     * shop's Spawners tab shows "Next works at X%" and the payback time from it). 100 without an island or stacking module.
     * Main thread.
     */
    default int nextSpawnerOutputPercent(Player player, String type) {
        return 100;
    }

    // ------------------------------------------------------------------ ECON-SPAWNERS step 1

    /** Spawners of {@code tier} an island may hold (cap incl. the perk bonus); empty = no limit info. Main thread. */
    default java.util.OptionalInt spawnerCap(java.util.UUID island, int tier) {
        return java.util.OptionalInt.empty();
    }

    /** Spawners of {@code tier} placed on the island, from memory. Main thread. */
    default long spawnersOfTier(java.util.UUID island, int tier) {
        return 0;
    }

    /**
     * Whether a tier's spawners can be bought for this island (kills of the previous tier's mobs on the island).
     *
     * @param unlocked     true when the shop may sell this tier here
     * @param kills        counted kills
     * @param needed       kills needed (0 = no gate)
     * @param mobsLabelKey {@code tier} when every mob of the previous tier counts, else {@code mobs}
     * @param mobs         the mob ids that count
     */
    record Unlock(boolean unlocked, long kills, long needed, String mobsLabelKey, List<String> mobs) {
        public static final Unlock OPEN = new Unlock(true, 0, 0, "tier", List.of());
    }

    /** Tier 1 and the premium tier are always open. Main thread (loads the island's kills lazily). */
    default Unlock spawnerUnlock(java.util.UUID island, int tier) {
        return Unlock.OPEN;
    }

    /** Money per production hour of one full spawner of {@code mob} on the island (0 = no sellable drop). Main thread. */
    default double spawnerIncomePerHour(java.util.UUID island, String mob) {
        return 0;
    }

    /** The custom drop item id of a spawner mob ({@code mobdrop_pig}), or empty (legacy, unknown). */
    default java.util.Optional<String> spawnerDropItem(String mob) {
        return java.util.Optional.empty();
    }

    /** True for a mob that is sold as a spawner (tiers 1-5); false for legacy and unknown mobs. */
    default boolean spawnerSellable(String mob) {
        return true;
    }
}
