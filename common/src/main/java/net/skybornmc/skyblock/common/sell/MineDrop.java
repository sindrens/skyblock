package net.skybornmc.skyblock.common.sell;

import net.skybornmc.skyblock.common.item.ItemKeys;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Owner 7 Nov 2026 (Q-70): emerald is the last /mine ore. An emerald from /mine carries this mark and sells as
 * {@code mine_emerald} ({@code sell.yml custom-items.mine-drops}); a plain emerald (villager trades) still can't be sold.
 */
public final class MineDrop {

    public static final NamespacedKey KEY = ItemKeys.key("mine_drop");
    public static final String EMERALD_ID = "mine_emerald";

    private MineDrop() {
    }

    /** Marks a /mine emerald (other items are left as they are). */
    public static ItemStack mark(ItemStack stack) {
        if (stack != null && stack.getType() == Material.EMERALD) {
            stack.editPersistentDataContainer(pdc -> pdc.set(KEY, PersistentDataType.BYTE, (byte) 1));
        }
        return stack;
    }

    /** True for an emerald from /mine. */
    public static boolean isMineEmerald(ItemStack stack) {
        return stack != null && stack.getType() == Material.EMERALD && stack.hasItemMeta()
                && stack.getPersistentDataContainer().has(KEY, PersistentDataType.BYTE);
    }
}
