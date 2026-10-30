package net.skybornmc.skyblock.box;

import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.skybornmc.skyblock.common.item.ItemKeys;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/** Bukkit side of box items: snapshots, decoding, display copies, inventory fit and audit summaries. */
final class BoxItems {

    private BoxItems() {
    }

    /** Serializes non-empty stacks (copies: callers may reuse theirs). */
    static List<BoxItem> snapshot(Collection<ItemStack> stacks, String source, long now) {
        List<BoxItem> out = new ArrayList<>();
        if (stacks == null) {
            return out;
        }
        for (ItemStack s : stacks) {
            if (s != null && !s.isEmpty()) {
                out.add(snapshot(s, source, now));
            }
        }
        return out;
    }

    static BoxItem snapshot(ItemStack s, String source, long createdAt) {
        return new BoxItem(s.serializeAsBytes(), s.getType().name(), s.getAmount(), plainName(s), source, createdAt, null);
    }

    static ItemStack decode(byte[] bytes) {
        return ItemStack.deserializeBytes(bytes);
    }

    /**
     * A copy for showing in a menu: without the unique item id, so the dupe scanner never sees the same tool in two open
     * menus (menu items are marked by the framework and can never leave the menu).
     */
    static ItemStack display(ItemStack stack) {
        ItemStack copy = stack.clone();
        copy.editPersistentDataContainer(pdc -> pdc.remove(ItemKeys.UUID));
        return copy;
    }

    /** Indexes of {@code stacks} that fit the player's storage slots, in order (see {@link FitPlan}). */
    static List<Integer> fitting(PlayerInventory inventory, List<ItemStack> stacks, boolean stopAtFirstMiss) {
        List<FitPlan.Slot<ItemStack>> slots = new ArrayList<>();
        for (ItemStack slot : inventory.getStorageContents()) {
            slots.add(slot == null || slot.isEmpty() ? new FitPlan.Slot<>(null, 0, 0)
                    : new FitPlan.Slot<>(slot, slot.getAmount(), slot.getMaxStackSize()));
        }
        List<FitPlan.Stack<ItemStack>> wanted = new ArrayList<>();
        for (ItemStack s : stacks) {
            wanted.add(new FitPlan.Stack<>(s, s.getAmount(), s.getMaxStackSize()));
        }
        return FitPlan.plan(slots, wanted, ItemStack::isSimilar, stopAtFirstMiss);
    }

    /** The name players see (anvil name, custom item name, or the material in words). */
    static String plainName(ItemStack stack) {
        Component name = null;
        if (stack.isDataOverridden(DataComponentTypes.CUSTOM_NAME)) {
            name = stack.getData(DataComponentTypes.CUSTOM_NAME);
        } else if (stack.isDataOverridden(DataComponentTypes.ITEM_NAME)) {
            name = stack.getData(DataComponentTypes.ITEM_NAME);
        }
        if (name != null && !(name instanceof TranslatableComponent)) {
            String plain = PlainTextComponentSerializer.plainText().serialize(name).strip();
            if (!plain.isEmpty()) {
                return plain;
            }
        }
        return words(stack.getType().name());
    }

    /** {@code DIAMOND_PICKAXE} → {@code Diamond Pickaxe}. */
    static String words(String material) {
        StringBuilder sb = new StringBuilder();
        for (String part : material.toLowerCase(Locale.ROOT).split("[_ ]")) {
            if (part.isEmpty()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    /** Short audit summary: {@code ["diamond x64 (sell)", ...]}, at most {@code max} entries. */
    static List<String> summary(Collection<BoxItem> items, int max) {
        List<String> out = new ArrayList<>();
        for (BoxItem i : items) {
            if (out.size() >= max) {
                out.add("+" + (items.size() - max) + " more");
                break;
            }
            out.add(i.material().toLowerCase(Locale.ROOT) + " x" + i.amount() + " (" + i.source() + ")");
        }
        return out;
    }
}
