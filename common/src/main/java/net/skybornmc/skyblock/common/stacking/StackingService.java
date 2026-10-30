package net.skybornmc.skyblock.common.stacking;

import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;

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

    /** True if the entity came from a stacked spawner (no AI, vanilla loot only). */
    boolean fromStackedSpawner(Entity entity);
}
