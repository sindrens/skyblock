package net.skybornmc.skyblock.common.sell;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Optional;

/**
 * Owner 7 Nov 2026 (Auto-Compactor perk): 9 of an item make one block. A plain block sells as exactly 9 of the item (same
 * price, counts 9 for the daily island price), so compacting never changes what you earn. Pure.
 */
public final class Compact {

    /** item -> its 9-block. */
    public static final Map<Material, Material> BLOCK_OF = Map.of(
            Material.WHEAT, Material.HAY_BLOCK,
            Material.IRON_INGOT, Material.IRON_BLOCK,
            Material.GOLD_INGOT, Material.GOLD_BLOCK,
            Material.DIAMOND, Material.DIAMOND_BLOCK,
            Material.EMERALD, Material.EMERALD_BLOCK,
            Material.REDSTONE, Material.REDSTONE_BLOCK,
            Material.LAPIS_LAZULI, Material.LAPIS_BLOCK,
            Material.COAL, Material.COAL_BLOCK,
            Material.MELON_SLICE, Material.MELON,
            Material.SLIME_BALL, Material.SLIME_BLOCK);

    private static final Map<Material, Material> ITEM_OF = invert();

    public static final int SIZE = 9;

    private Compact() {
    }

    private static Map<Material, Material> invert() {
        java.util.EnumMap<Material, Material> out = new java.util.EnumMap<>(Material.class);
        BLOCK_OF.forEach((item, block) -> out.put(block, item));
        return java.util.Collections.unmodifiableMap(out);
    }

    /** The item a plain (no meta) block stands for, or empty (not a compact block, or a named/custom one). */
    public static Optional<Material> itemOf(ItemStack stack) {
        if (stack == null || stack.hasItemMeta()) {
            return Optional.empty();
        }
        return Optional.ofNullable(ITEM_OF.get(stack.getType()));
    }

    /** True for a plain compact block. */
    public static boolean isBlock(ItemStack stack) {
        return itemOf(stack).isPresent();
    }
}
