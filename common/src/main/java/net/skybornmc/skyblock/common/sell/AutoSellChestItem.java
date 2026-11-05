package net.skybornmc.skyblock.common.sell;

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
 * The Auto-Sell Chest item (owner 4 Nov 2026, "Coming soon" built for real): placed on your island it sells what is in it
 * every few seconds (economy module). Created by the Black Market; texts: messages/economy.yml {@code autosell.item.*}.
 */
public final class AutoSellChestItem {

    public static final NamespacedKey KEY = ItemKeys.key("autosell_chest");

    private AutoSellChestItem() {
    }

    public static ItemStack create(Core core) {
        ItemStack item = new ItemStack(Material.CHEST);
        item.editMeta(meta -> {
            meta.getPersistentDataContainer().set(KEY, PersistentDataType.BYTE, (byte) 1);
            meta.setEnchantmentGlintOverride(true);
        });
        Audience none = Audience.empty();
        ItemStack styled = core.menus().item(item, core.messages().component(none, "economy", "autosell.item.name"), List.of(
                core.messages().component(none, "economy", "autosell.item.subtitle"), Component.empty(),
                core.messages().component(none, "economy", "autosell.item.about"), Component.empty(),
                core.messages().component(none, "economy", "autosell.item.action")));
        item.setItemMeta(styled.getItemMeta());
        return item;
    }

    public static boolean isItem(ItemStack item) {
        return item != null && item.getType() == Material.CHEST && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(KEY, PersistentDataType.BYTE);
    }
}
