package net.skybornmc.skyblock.common.item;

import org.bukkit.block.ShulkerBox;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Collection;
import java.util.Locale;

/**
 * Staff items (owner answer 332): everything staff create out of nothing ({@code /give}, {@code /i}, {@code /more},
 * {@code /skull}, the creative inventory) carries the hidden {@link ItemKeys#STAFF_ITEM} tag. A staff item can be used,
 * but never sold (sell chest, {@code /sell}, shop), traded, auctioned or sent to another player. The tag follows the item
 * into crafting results, smelting results and placed blocks (the essentials module keeps it on them), so it can't be
 * washed off by turning diamonds into a block and back.
 *
 * <p>Every value path checks {@link #containsMarked(ItemStack)}: it also looks inside shulker boxes and bundles.</p>
 */
public final class StaffItems {

    /** The longest stored "made by" text. */
    static final int MAX_SOURCE = 64;

    private StaffItems() {
    }

    /** Tags {@code stack} in place (no-op for empty stacks). {@code source} says who made it, e.g. {@code give:Steve}. */
    public static void mark(ItemStack stack, String source) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        String value = cleanSource(source);
        stack.editPersistentDataContainer(pdc -> pdc.set(ItemKeys.STAFF_ITEM, PersistentDataType.STRING, value));
    }

    /** Tags every stack of {@code stacks} in place. */
    public static void markAll(Collection<ItemStack> stacks, String source) {
        if (stacks != null) {
            for (ItemStack stack : stacks) {
                mark(stack, source);
            }
        }
    }

    /** True if this stack itself carries the staff tag. */
    public static boolean isMarked(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getPersistentDataContainer().has(ItemKeys.STAFF_ITEM);
    }

    /** Who made the item ({@code give:Steve}), or null when it is not a staff item. */
    public static String source(ItemStack stack) {
        if (!isMarked(stack)) {
            return null;
        }
        return stack.getPersistentDataContainer().get(ItemKeys.STAFF_ITEM, PersistentDataType.STRING);
    }

    /** True if this stack, or anything inside it (shulker box, bundle), is a staff item. */
    public static boolean containsMarked(ItemStack stack) {
        return containsMarked(stack, 0);
    }

    private static boolean containsMarked(ItemStack stack, int depth) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (isMarked(stack)) {
            return true;
        }
        if (depth > 2 || !stack.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta instanceof BundleMeta bundle && bundle.hasItems()) {
            for (ItemStack inner : bundle.getItems()) {
                if (containsMarked(inner, depth + 1)) {
                    return true;
                }
            }
        }
        if (meta instanceof BlockStateMeta bsm && bsm.hasBlockState() && bsm.getBlockState() instanceof ShulkerBox box) {
            for (ItemStack inner : box.getInventory().getContents()) {
                if (containsMarked(inner, depth + 1)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** True if any stack of {@code stacks} is or contains a staff item. */
    public static boolean anyMarked(Collection<ItemStack> stacks) {
        if (stacks == null) {
            return false;
        }
        for (ItemStack stack : stacks) {
            if (containsMarked(stack)) {
                return true;
            }
        }
        return false;
    }

    /** {@code kind:name} cut to a safe length, lowercase kind; blank → {@code staff}. Pure. */
    public static String cleanSource(String source) {
        if (source == null || source.isBlank()) {
            return "staff";
        }
        String s = source.strip();
        int colon = s.indexOf(':');
        if (colon > 0) {
            s = s.substring(0, colon).toLowerCase(Locale.ROOT) + s.substring(colon);
        }
        return s.length() > MAX_SOURCE ? s.substring(0, MAX_SOURCE) : s;
    }

    /**
     * The chunk data key name that remembers a placed staff block at these world coordinates (essentials keeps the tag
     * on placed blocks): {@code staff_block/x/y/z}. Only lowercase letters, digits, {@code _ / -} so it is a valid
     * key. Pure.
     */
    public static String blockKeyName(int x, int y, int z) {
        return "staff_block/" + x + "/" + y + "/" + z;
    }
}
