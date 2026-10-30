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
}
