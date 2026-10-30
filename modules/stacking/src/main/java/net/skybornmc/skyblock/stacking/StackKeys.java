package net.skybornmc.skyblock.stacking;

import net.skybornmc.skyblock.common.item.ItemKeys;
import org.bukkit.NamespacedKey;

/** Persistent data keys of the stacking module (entities, items, chunks). */
final class StackKeys {

    /** Mobs in a stack (INTEGER) on the stack entity; missing = 1. */
    static final NamespacedKey MOB_COUNT = ItemKeys.key("stack_mob_count");
    /** Marks a mob from a stacked spawner (BYTE 1): no AI, vanilla loot, never despawns. */
    static final NamespacedKey SPAWNER_MOB = ItemKeys.key("stack_spawner_mob");
    /** Parents of a stack on breeding cooldown (INTEGER) and when the cooldown ends (LONG, epoch millis). */
    static final NamespacedKey BREED_BUSY = ItemKeys.key("stack_breed_busy");
    static final NamespacedKey BREED_UNTIL = ItemKeys.key("stack_breed_until");
    /** Items held by a dropped item entity (LONG, no max). */
    static final NamespacedKey ITEM_COUNT = ItemKeys.key("stack_item_count");
    /** When items last joined the entity (LONG, epoch millis): the despawn timer starts here. */
    static final NamespacedKey ITEM_SINCE = ItemKeys.key("stack_item_since");
    /** Mob type of a spawner item (STRING entity id). */
    static final NamespacedKey SPAWNER_TYPE = ItemKeys.key("spawner_type");
    /** Positions of crops in a chunk that may grow in the dark (INTEGER_ARRAY, see {@link CropPositions}). */
    static final NamespacedKey CROPS = ItemKeys.key("stack_crops");

    private StackKeys() {
    }
}
