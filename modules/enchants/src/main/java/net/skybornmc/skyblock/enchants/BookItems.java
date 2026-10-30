package net.skybornmc.skyblock.enchants;

import net.skybornmc.skyblock.common.enchant.EnchantBook;
import net.skybornmc.skyblock.common.item.ItemDefinition;
import net.skybornmc.skyblock.common.item.ItemKeys;
import net.skybornmc.skyblock.common.item.ItemRegistry;
import net.skybornmc.skyblock.common.item.ItemType;
import net.skybornmc.skyblock.common.item.LoreBuilder;
import net.skybornmc.skyblock.common.item.Rarity;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Enchant books and Disenchant Scrolls as unique custom items (ENCHANTS §9, EN18): item id + UUID (= database row id) +
 * enchant id + level in the item's data. Lore is generated from the registry and {@code messages/enchants.yml}, never
 * edited by hand. Vanilla {@code enchanted_book} (glint on Java and Bedrock), vanilla stored enchantments hidden.
 */
final class BookItems {

    static final String BOOK_ID = "enchant_book";
    static final String SCROLL_ID = "disenchant_scroll";
    static final NamespacedKey ENCHANT = ItemKeys.key("enchant_id");
    static final NamespacedKey LEVEL = ItemKeys.key("enchant_level");

    private final EnchantsModule module;

    BookItems(EnchantsModule module) {
        this.module = module;
    }

    void register() {
        ItemRegistry items = module.core.items();
        items.register(ItemDefinition.builder(BOOK_ID, Material.ENCHANTED_BOOK, ItemType.ENCHANT_BOOK, Rarity.COMMON)
                .name(module.text().raw("book.fallback-name")).unique().glint().renderer(new BookRenderer()).build());
        items.register(ItemDefinition.builder(SCROLL_ID, Material.PAPER, ItemType.SCROLL, Rarity.RARE)
                .name(module.text().raw("scroll.name")).subtitle(module.text().raw("scroll.subtitle"))
                .description(module.text().plain("scroll.description"))
                .action(module.text().plain("book.action-input"), module.text().plain("book.action-text"))
                .unique().glint().build());
    }

    void unregister() {
        module.core.items().unregister(BOOK_ID);
        module.core.items().unregister(SCROLL_ID);
    }

    /** A new book item (random unique id; the caller writes the database row with {@link #id}). */
    ItemStack createBook(String enchant, int level, String source) {
        return module.core.items().create(BOOK_ID, 1, source, pdc -> {
            pdc.set(ENCHANT, PersistentDataType.STRING, enchant);
            pdc.set(LEVEL, PersistentDataType.INTEGER, level);
        });
    }

    ItemStack createScroll(String source) {
        return module.core.items().create(SCROLL_ID, source);
    }

    /** The unique id of a created item. */
    static UUID id(ItemStack stack) {
        return ItemRegistry.uuidOf(stack).orElseThrow(() -> new IllegalStateException("Created book has no unique id"));
    }

    /** What a book or scroll item says it is; empty for anything else (or a broken item). */
    static Optional<EnchantBook> read(ItemStack stack) {
        Optional<String> itemId = ItemRegistry.idOf(stack);
        if (itemId.isEmpty() || !(itemId.get().equals(BOOK_ID) || itemId.get().equals(SCROLL_ID))) {
            return Optional.empty();
        }
        Optional<UUID> uuid = ItemRegistry.uuidOf(stack);
        if (uuid.isEmpty()) {
            return Optional.empty();
        }
        if (itemId.get().equals(SCROLL_ID)) {
            return Optional.of(new EnchantBook(uuid.get(), true, null, 0));
        }
        String enchant = stack.getPersistentDataContainer().get(ENCHANT, PersistentDataType.STRING);
        Integer level = stack.getPersistentDataContainer().get(LEVEL, PersistentDataType.INTEGER);
        if (enchant == null || level == null || level < 1) {
            return Optional.empty();
        }
        return Optional.of(new EnchantBook(uuid.get(), false, enchant, level));
    }

    static boolean isOurs(ItemStack stack) {
        return ItemRegistry.idOf(stack).filter(id -> id.equals(BOOK_ID) || id.equals(SCROLL_ID)).isPresent();
    }

    /**
     * A copy for menus: same look, without the unique id and item id (so it never counts as a second copy and can't be
     * used as a real book).
     */
    static ItemStack displayCopy(ItemStack stack) {
        ItemStack copy = stack.clone();
        copy.editPersistentDataContainer(pdc -> {
            pdc.remove(ItemKeys.UUID);
            pdc.remove(ItemKeys.ID);
        });
        return copy;
    }

    // ------------------------------------------------------------------ renderer

    private final class BookRenderer implements ItemDefinition.Renderer {

        @Override
        public String name(ItemStack stack, ItemDefinition definition) {
            Optional<EnchantBook> book = read(stack);
            Optional<EnchantDef> def = book.flatMap(b -> module.registry().get(b.enchant()));
            if (book.isEmpty() || def.isEmpty()) {
                return definition.name();
            }
            return LoreBuilder.fill(module.text().raw("book.name"), Map.of(
                    "rarity", def.get().rarity().id(),
                    "name", LoreBuilder.escape(module.text().displayName(def.get().id(), book.get().level()))));
        }

        @Override
        public Rarity rarity(ItemStack stack, ItemDefinition definition) {
            return read(stack).flatMap(b -> module.registry().get(b.enchant())).map(EnchantDef::rarity).orElse(definition.rarity());
        }

        @Override
        public void lore(ItemStack stack, ItemDefinition definition, LoreBuilder lore) {
            Optional<EnchantBook> book = read(stack);
            Optional<EnchantDef> found = book.flatMap(b -> module.registry().get(b.enchant()));
            if (book.isEmpty() || found.isEmpty()) {
                lore.description(module.text().plain("book.unknown"));
                return;
            }
            EnchantDef def = found.get();
            int level = book.get().level();
            EnchantText text = module.text();
            lore.subtitle(LoreBuilder.fill(text.raw("book.subtitle"), Map.of("targets", LoreBuilder.escape(text.targets(def)))));
            lore.description(text.effect(def, level));
            LoreBuilder.Section details = lore.section(text.raw("book.details"));
            details.text(LoreBuilder.fill(text.raw("book.max-level"), Map.of("value", EnchantText.roman(def.maxLevel()))));
            if (def.mythic()) {
                details.text(text.raw("book.prestige-slot"));
            } else {
                details.text(LoreBuilder.fill(text.raw("book.slot-cost"), Map.of("value", String.valueOf(def.slotCost()))));
            }
            List<String> conflicts = new ArrayList<>();
            for (EnchantDef other : module.registry().all()) {
                if (def.conflictsWith(other) && !(def.mythic() && other.mythic())) {
                    conflicts.add(text.name(other.id()));
                }
            }
            if (!conflicts.isEmpty()) {
                details.text(LoreBuilder.fill(text.raw("book.conflicts"), Map.of("value", LoreBuilder.escape(String.join(", ", conflicts)))));
            }
            if (def.armorOnly()) {
                lore.line(text.raw("book.armor"));
            }
            if (!def.enabled()) {
                lore.line(text.raw("book.disabled"));
            }
            lore.action(text.plain("book.action-input"), text.plain("book.action-text"));
        }
    }

    /** {@code Material} of the book item (for menus that show a book look without the item). */
    static Material material() {
        return Material.ENCHANTED_BOOK;
    }
}
