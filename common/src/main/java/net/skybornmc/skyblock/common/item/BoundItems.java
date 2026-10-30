package net.skybornmc.skyblock.common.item;

import org.bukkit.block.ShulkerBox;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Bound items (REVIEW3 R3-2, PHASE-10 M10.1): loot from a <b>bound</b> source (keys from votes, the AFK pool, the daily
 * calendar, playtime, rank claims) is bound to the profile that earned it, so alts can't farm free loot and hand it to a
 * main. A bound item carries the hidden {@link #KEY} tag with the owning profile key. It can be used, stored and sold to
 * the server, but never traded, auctioned or picked up by another profile. Trade, auction and pickup paths check
 * {@link #containsBound(ItemStack)} / {@link #boundTo(ItemStack)}; like {@link StaffItems} it also looks inside shulker
 * boxes and bundles.
 */
public final class BoundItems {

    /** Persistent data key: the profile key (UUID string) the item is bound to. */
    public static final org.bukkit.NamespacedKey KEY = ItemKeys.key("bound_profile");

    private BoundItems() {
    }

    /** Binds {@code stack} in place to a profile (no-op for empty stacks). */
    public static void mark(ItemStack stack, UUID profileKey) {
        if (stack == null || stack.isEmpty() || profileKey == null) {
            return;
        }
        String value = profileKey.toString();
        stack.editPersistentDataContainer(pdc -> pdc.set(KEY, PersistentDataType.STRING, value));
    }

    /** Binds every stack of {@code stacks} in place. */
    public static void markAll(Collection<ItemStack> stacks, UUID profileKey) {
        if (stacks != null) {
            for (ItemStack stack : stacks) {
                mark(stack, profileKey);
            }
        }
    }

    /** True if this stack itself is bound. */
    public static boolean isBound(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getPersistentDataContainer().has(KEY);
    }

    /** The profile key this stack is bound to (empty when it is not bound or the tag is broken). */
    public static Optional<UUID> boundTo(ItemStack stack) {
        if (!isBound(stack)) {
            return Optional.empty();
        }
        return parse(stack.getPersistentDataContainer().get(KEY, PersistentDataType.STRING));
    }

    /** {@code raw} as a profile key, empty when blank or not a UUID. Pure. */
    public static Optional<UUID> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(raw.trim()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * True when a bound item may go to {@code receiverProfile}: unbound items always, bound ones only to their own
     * profile. A broken tag counts as bound to nobody (refused). Pure apart from reading the tag.
     */
    public static boolean mayReceive(ItemStack stack, UUID receiverProfile) {
        if (!isBound(stack)) {
            return true;
        }
        return boundTo(stack).map(p -> p.equals(receiverProfile)).orElse(false);
    }

    /** True if this stack, or anything inside it (shulker box, bundle), is bound. */
    public static boolean containsBound(ItemStack stack) {
        return containsBound(stack, 0);
    }

    private static boolean containsBound(ItemStack stack, int depth) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (isBound(stack)) {
            return true;
        }
        if (depth > 2 || !stack.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta instanceof BundleMeta bundle && bundle.hasItems()) {
            for (ItemStack inner : bundle.getItems()) {
                if (containsBound(inner, depth + 1)) {
                    return true;
                }
            }
        }
        if (meta instanceof BlockStateMeta bsm && bsm.hasBlockState() && bsm.getBlockState() instanceof ShulkerBox box) {
            for (ItemStack inner : box.getInventory().getContents()) {
                if (containsBound(inner, depth + 1)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** True if any stack of {@code stacks} is or contains a bound item. */
    public static boolean anyBound(Collection<ItemStack> stacks) {
        if (stacks == null) {
            return false;
        }
        for (ItemStack stack : stacks) {
            if (containsBound(stack)) {
                return true;
            }
        }
        return false;
    }
}
