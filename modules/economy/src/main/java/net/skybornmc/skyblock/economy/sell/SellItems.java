package net.skybornmc.skyblock.economy.sell;

import io.papermc.paper.persistence.PersistentDataContainerView;
import net.skybornmc.skyblock.common.item.ItemDefinition;
import net.skybornmc.skyblock.common.item.ItemKeys;
import net.skybornmc.skyblock.common.item.ItemRegistry;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** The Bukkit side of {@link SellRules}: reads what the rules need from an ItemStack. Main thread. */
final class SellItems {

    private SellItems() {
    }

    static SellRules.ItemFacts facts(ItemStack stack, ItemRegistry items, List<String> blockedDataKeys) {
        if (stack == null || stack.isEmpty()) {
            return new SellRules.ItemFacts(null, null, null, false, false, false);
        }
        PersistentDataContainerView pdc = stack.getPersistentDataContainer();
        String material = stack.getType().getKey().getKey();
        Optional<String> customId = ItemRegistry.idOf(stack);
        String type = null;
        if (customId.isPresent() && items != null) {
            type = items.get(customId.get()).map(ItemDefinition::type).map(t -> t.id()).orElse(null);
        }
        boolean unique = pdc.has(ItemKeys.UUID);
        boolean dupe = pdc.has(ItemKeys.DUPE_FLAG);
        boolean blocked = false;
        for (String raw : blockedDataKeys) {
            NamespacedKey key = raw == null ? null : NamespacedKey.fromString(raw.trim().toLowerCase(Locale.ROOT));
            if (key != null && pdc.has(key)) {
                blocked = true;
                break;
            }
        }
        boolean staff = net.skybornmc.skyblock.common.item.StaffItems.containsMarked(stack);
        return new SellRules.ItemFacts(material, customId.orElse(null), type, unique, dupe, blocked, staff);
    }
}
