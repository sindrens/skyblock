package net.skybornmc.skyblock.trade;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.persistence.PersistentDataContainerView;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.skybornmc.skyblock.common.item.ItemDefinition;
import net.skybornmc.skyblock.common.item.ItemKeys;
import net.skybornmc.skyblock.common.item.ItemRegistry;
import net.skybornmc.skyblock.common.item.ItemType;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.ShulkerBox;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Item handling on the Bukkit side: what may be offered, snapshots, display copies and inventory space. */
final class TradeItems {

    private final ItemRegistry items;

    TradeItems(ItemRegistry items) {
        this.items = items;
    }

    /**
     * Why {@code stack} can't be offered ({@code items.<key>} in the messages file), or empty. Money notes, enchant books,
     * set tokens, tools, starter kit pieces and tag items are fine (unique and tracked); locked wardrobe armor,
     * menu items, dupe-flagged items, admin blocks and filled containers are not.
     */
    Optional<String> problem(ItemStack stack, TradeConfig config) {
        if (stack == null || stack.isEmpty()) {
            return Optional.of("no-item");
        }
        PersistentDataContainerView pdc = stack.getPersistentDataContainer();
        if (pdc.has(ItemKeys.DUPE_FLAG)) {
            return Optional.of("dupe-flagged");
        }
        // Owner answer 332: items made by staff (/give, /i, creative) never change hands, also not inside a container.
        if (net.skybornmc.skyblock.common.item.StaffItems.containsMarked(stack)) {
            return Optional.of("staff-item");
        }
        Optional<String> id = ItemRegistry.idOf(stack);
        if (id.isPresent() && TradeRules.idBlocked(id.get(), config.blockedIds)) {
            return Optional.of(id.get().contains("starter") ? "starter" : "blocked");
        }
        for (String raw : config.blockedDataKeys) {
            NamespacedKey key = raw == null ? null : NamespacedKey.fromString(raw.trim().toLowerCase(Locale.ROOT));
            if (key != null && pdc.has(key)) {
                return Optional.of(raw.contains("starter") ? "starter" : "blocked");
            }
        }
        Optional<ItemDefinition> def = items.definitionOf(stack);
        if (def.isPresent() && def.get().type() == ItemType.MENU) {
            return Optional.of("blocked");
        }
        for (String raw : config.blockedMaterials) {
            Material m = raw == null ? null : Material.matchMaterial(raw.trim());
            if (m != null && stack.getType() == m) {
                return Optional.of("blocked");
            }
        }
        if (!config.allowFilledContainers && filledContainer(stack)) {
            return Optional.of("filled-container");
        }
        return Optional.empty();
    }

    private static boolean filledContainer(ItemStack stack) {
        if (!stack.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta instanceof BundleMeta bundle && bundle.hasItems()) {
            return true;
        }
        if (meta instanceof BlockStateMeta bsm && bsm.hasBlockState() && bsm.getBlockState() instanceof ShulkerBox box) {
            for (ItemStack inside : box.getInventory().getContents()) {
                if (inside != null && !inside.isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Serializes the stack (row id 0 until the escrow stores it). */
    static Offer snapshot(ItemStack stack) {
        String key = ItemRegistry.idOf(stack).orElse(stack.getType().getKey().getKey());
        return new Offer(0, stack.serializeAsBytes(), key, ItemRegistry.uuidOf(stack).map(Object::toString).orElse(null),
                stack.getType().name(), stack.getAmount(), plainName(stack));
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
        for (String part : material.toLowerCase(Locale.ROOT).split("_")) {
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

    static ItemStack decode(byte[] bytes) {
        return ItemStack.deserializeBytes(bytes);
    }

    /**
     * A copy for showing in the trade window: without the unique item id, so the dupe scanner never sees the same tool
     * twice (menu items are marked by the framework and can never leave the menu).
     */
    static ItemStack display(ItemStack stack) {
        ItemStack copy = stack.clone();
        copy.editPersistentDataContainer(pdc -> pdc.remove(ItemKeys.UUID));
        return copy;
    }

    /**
     * Whether all {@code incoming} stacks fit into {@code storage} (the 36 storage slots), simulated on a copy: free slots
     * and similar stacks with room count.
     */
    static boolean fitsAll(ItemStack[] storage, List<ItemStack> incoming) {
        ItemStack[] slots = new ItemStack[storage.length];
        for (int i = 0; i < storage.length; i++) {
            slots[i] = storage[i] == null || storage[i].isEmpty() ? null : storage[i].clone();
        }
        for (ItemStack stack : incoming) {
            int left = stack.getAmount();
            int max = stack.getMaxStackSize();
            for (int i = 0; i < slots.length && left > 0; i++) {
                ItemStack s = slots[i];
                if (s != null && s.isSimilar(stack) && s.getAmount() < max) {
                    int move = Math.min(left, max - s.getAmount());
                    s.setAmount(s.getAmount() + move);
                    left -= move;
                }
            }
            for (int i = 0; i < slots.length && left > 0; i++) {
                if (slots[i] == null) {
                    ItemStack placed = stack.clone();
                    int move = Math.min(left, max);
                    placed.setAmount(move);
                    slots[i] = placed;
                    left -= move;
                }
            }
            if (left > 0) {
                return false;
            }
        }
        return true;
    }
}
