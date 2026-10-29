package net.skybornmc.skyblock.common.item;

import org.bukkit.NamespacedKey;

/**
 * Persistent data keys on custom items. The namespace is fixed (not tied to the plugin instance) so items stay
 * readable across renames and in tests.
 */
public final class ItemKeys {

    public static final String NAMESPACE = "phoenixskyblock";

    /** Custom item id, e.g. {@code tool_iron_pickaxe}. Present on every custom item. */
    public static final NamespacedKey ID = key("item_id");
    /** Unique instance id (UUID string). Only on unique items. */
    public static final NamespacedKey UUID = key("item_uuid");
    /** Creation time, epoch millis (unique items). */
    public static final NamespacedKey CREATED = key("item_created");
    /** Where the item came from, e.g. {@code admin:SlickyMatt}, {@code crate:rare} (unique items). */
    public static final NamespacedKey SOURCE = key("item_source");
    /** Set (epoch millis) when the dupe scanner found this item's UUID more than once. */
    public static final NamespacedKey DUPE_FLAG = key("item_dupe_flag");

    private ItemKeys() {
    }

    public static NamespacedKey key(String name) {
        return new NamespacedKey(NAMESPACE, name);
    }
}
