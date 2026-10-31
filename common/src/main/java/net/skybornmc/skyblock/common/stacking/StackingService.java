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
}
