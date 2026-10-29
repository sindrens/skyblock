package net.skybornmc.skyblock.auction;

import io.papermc.paper.datacomponent.DataComponentTypes;
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
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;
import io.papermc.paper.persistence.PersistentDataContainerView;

import java.util.Locale;
import java.util.Optional;

/** Item handling on the Bukkit side: what may be listed, snapshots, display copies and inventory space. */
final class ItemCodec {

    private final ItemRegistry items;

    ItemCodec(ItemRegistry items) {
        this.items = items;
    }

    /**
     * Why {@code stack} can't be listed ({@code items.<key>} in the messages file), or empty if it can. Checks: dupe flag,
     * blocked custom ids (starter kit), blocked data keys, money notes, blocked materials, filled containers.
     */
    Optional<String> problem(ItemStack stack, AuctionConfig config) {
        if (stack == null || stack.isEmpty()) {
            return Optional.of("no-item");
        }
        PersistentDataContainerView pdc = stack.getPersistentDataContainer();
        if (pdc.has(ItemKeys.DUPE_FLAG)) {
            return Optional.of("dupe-flagged");
        }
        Optional<String> id = ItemRegistry.idOf(stack);
        if (id.isPresent() && ListingRules.idBlocked(id.get(), config.blockedIds)) {
            return Optional.of("starter");
        }
        for (String raw : config.blockedDataKeys) {
            NamespacedKey key = raw == null ? null : NamespacedKey.fromString(raw.trim().toLowerCase(Locale.ROOT));
            if (key != null && pdc.has(key)) {
                return Optional.of(raw.contains("starter") ? "starter" : "blocked");
            }
        }
        Optional<ItemDefinition> def = items.definitionOf(stack);
        if (def.isPresent() && def.get().type() == ItemType.NOTE && !config.allowMoneyNotes) {
            return Optional.of("note");
        }
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

    /** Serializes the stack and reads the facts the database keeps next to it. */
    ItemSnapshot snapshot(ItemStack stack) {
        Optional<String> id = ItemRegistry.idOf(stack);
        Optional<ItemDefinition> def = items.definitionOf(stack);
        Material type = stack.getType();
        Category category = Category.categorize(type.name(), def.map(d -> d.type().id()).orElse(null), type.isBlock(), type.isEdible());
        String key = id.orElse(type.getKey().getKey());
        return new ItemSnapshot(stack.serializeAsBytes(), key, ItemRegistry.uuidOf(stack).map(Object::toString).orElse(null),
                type.name(), stack.getAmount(), plainName(stack), category);
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

    /** The stored stack. */
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

    /** Whether the whole stack fits into the player's storage slots (free slot or a similar stack with room). */
    static boolean fits(PlayerInventory inventory, ItemStack stack) {
        int left = stack.getAmount();
        int max = stack.getMaxStackSize();
        for (ItemStack slot : inventory.getStorageContents()) {
            if (slot == null || slot.isEmpty()) {
                left -= max;
            } else if (slot.isSimilar(stack)) {
                left -= Math.max(0, max - slot.getAmount());
            }
            if (left <= 0) {
                return true;
            }
        }
        return false;
    }
}
