package net.skybornmc.skyblock.common.stacking;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.item.ItemKeys;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * The Chunk Collector item (owner 4 Nov 2026, "Coming soon" built for real): a hopper that, placed on your island, catches
 * every item that drops in its chunk (farms, spawners) before it lies on the ground; it passes them on like a hopper.
 * Created by the Black Market; texts: messages/stacking.yml {@code collector.item.*}.
 */
public final class ChunkCollectorItem {

    public static final NamespacedKey KEY = ItemKeys.key("chunk_collector");

    private ChunkCollectorItem() {
    }

    public static ItemStack create(Core core) {
        ItemStack item = new ItemStack(Material.HOPPER);
        item.editMeta(meta -> {
            meta.getPersistentDataContainer().set(KEY, PersistentDataType.BYTE, (byte) 1);
            meta.setEnchantmentGlintOverride(true);
        });
        Audience none = Audience.empty();
        ItemStack styled = core.menus().item(item, core.messages().component(none, "stacking", "collector.item.name"), List.of(
                core.messages().component(none, "stacking", "collector.item.subtitle"), Component.empty(),
                core.messages().component(none, "stacking", "collector.item.about"), Component.empty(),
                core.messages().component(none, "stacking", "collector.item.action")));
        item.setItemMeta(styled.getItemMeta());
        return item;
    }

    public static boolean isItem(ItemStack item) {
        return item != null && item.getType() == Material.HOPPER && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(KEY, PersistentDataType.BYTE);
    }
}
