package net.skybornmc.skyblock.common.sell;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.skybornmc.skyblock.common.Core;
import net.skybornmc.skyblock.common.item.ItemKeys;
import net.skybornmc.skyblock.common.text.Msg;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

/**
 * The Sell Wand (owner 4 Nov 2026, "Coming soon" items built for real): right-click a chest or barrel on your island to
 * sell everything sellable in it at once (the economy module's listener). It has a number of uses, shown in the lore.
 * Created by the Black Market (and staff); one unique id per wand so it can be traced. Texts: messages/economy.yml
 * {@code sell-wand.*}.
 */
public final class SellWand {

    public static final NamespacedKey USES = ItemKeys.key("sell_wand_uses");
    public static final NamespacedKey ID = ItemKeys.key("sell_wand_id");
    private static final String MODULE = "economy";

    private SellWand() {
    }

    /** A new wand with {@code uses} uses. Main thread (messages). */
    public static ItemStack create(Core core, int uses) {
        ItemStack item = new ItemStack(Material.BLAZE_ROD);
        item.editMeta(meta -> {
            meta.getPersistentDataContainer().set(USES, PersistentDataType.INTEGER, Math.max(1, uses));
            meta.getPersistentDataContainer().set(ID, PersistentDataType.STRING, UUID.randomUUID().toString());
            meta.setEnchantmentGlintOverride(true);
        });
        render(core, item);
        return item;
    }

    /** Uses left, or -1 when the item is no Sell Wand. */
    public static int uses(ItemStack item) {
        if (item == null || item.getType() != Material.BLAZE_ROD || !item.hasItemMeta()) {
            return -1;
        }
        Integer n = item.getItemMeta().getPersistentDataContainer().get(USES, PersistentDataType.INTEGER);
        return n == null ? -1 : n;
    }

    /** Sets the uses left and updates the lore (never below 0). */
    public static void setUses(Core core, ItemStack item, int uses) {
        item.editMeta(meta -> meta.getPersistentDataContainer().set(USES, PersistentDataType.INTEGER, Math.max(0, uses)));
        render(core, item);
    }

    private static void render(Core core, ItemStack item) {
        int uses = uses(item);
        TagResolver tag = Msg.number("uses", Math.max(0, uses));
        Component name = core.messages().component(net.kyori.adventure.audience.Audience.empty(), MODULE, "sell-wand.name", tag);
        java.util.List<Component> lore = java.util.List.of(
                core.messages().component(net.kyori.adventure.audience.Audience.empty(), MODULE, "sell-wand.subtitle", tag),
                Component.empty(),
                core.messages().component(net.kyori.adventure.audience.Audience.empty(), MODULE, "sell-wand.about", tag),
                Component.empty(),
                core.messages().component(net.kyori.adventure.audience.Audience.empty(), MODULE, "sell-wand.uses", tag),
                Component.empty(),
                core.messages().component(net.kyori.adventure.audience.Audience.empty(), MODULE, "sell-wand.action", tag));
        ItemStack styled = core.menus().item(item, name, lore);
        item.setItemMeta(styled.getItemMeta());
    }
}
