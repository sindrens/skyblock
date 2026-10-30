package net.skybornmc.skyblock.common.enchant;

import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.UUID;

/**
 * An enchant book item built on the main thread whose database row is written later, inside the caller's own
 * transaction ({@link EnchantService#insertBook}). Until that transaction commits the item must not be handed out.
 *
 * @param id      the item's unique id (= the future {@code enchant_books} row id)
 * @param enchant enchant id
 * @param level   book level
 * @param item    the book item
 */
public record PreparedBook(UUID id, String enchant, int level, ItemStack item) {

    public PreparedBook {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(enchant, "enchant");
        Objects.requireNonNull(item, "item");
        if (level < 1) {
            throw new IllegalArgumentException("A book needs level 1 or more");
        }
    }
}
