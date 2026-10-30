package net.skybornmc.skyblock.common.enchant;

import java.util.Objects;
import java.util.UUID;

/**
 * What an enchant book or Disenchant Scroll item says it is. The database row with the same id is the truth
 * ({@code enchant_books}); an item whose row is not active any more is a copy and gets confiscated when used.
 *
 * @param id      the item's unique id (= database row id)
 * @param scroll  true for a Disenchant Scroll (then {@code enchant} is null and {@code level} 0)
 * @param enchant enchant id, e.g. {@code vein_rend}
 * @param level   book level, 1 or more
 */
public record EnchantBook(UUID id, boolean scroll, String enchant, int level) {

    public EnchantBook {
        Objects.requireNonNull(id, "id");
        if (!scroll && (enchant == null || level < 1)) {
            throw new IllegalArgumentException("A book needs an enchant and a level");
        }
    }
}
