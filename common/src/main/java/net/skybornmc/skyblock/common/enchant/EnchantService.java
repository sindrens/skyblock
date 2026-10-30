package net.skybornmc.skyblock.common.enchant;

import net.skybornmc.skyblock.common.item.Rarity;
import net.skybornmc.skyblock.common.menu.Menu;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.SQLException;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Custom enchants (Phase E, docs/plans/ENCHANTS.md), published by the enchants module:
 * {@code core.services().optional(EnchantService.class)}. Read-only views plus book creation for drop sources.
 *
 * <p><b>Hooks for E2/E3</b> (drops, gathering, combat, stacked kills): read the level with {@link #level} and the per-level
 * numbers with {@link #value} (keys from {@code enchants.yml → <id>.values / params}), e.g.
 * {@code value("vein_rend", "chance", 2) == 12}. Create dropped books with {@link #createBook}. Passive percent bonuses
 * (Swiftness, Tempering, Scholar's Insight, Crystal Seeker, Prosperity, Treasure Hunter, Phoenix Edge) are already
 * reported to {@link net.skybornmc.skyblock.common.bonus.BonusService} and must not be added again.</p>
 *
 * <p>Main thread only, except the futures.</p>
 */
public interface EnchantService {

    /** Every enchant id in the registry ({@code enchants.yml}), in registry order. */
    Set<String> ids();

    default boolean exists(String id) {
        return ids().contains(id);
    }

    /** False when the enchant is turned off ({@code enabled: false}, EN19): it stays on items but does nothing. */
    boolean enabled(String id);

    /** Highest level, or 0 for an unknown id. */
    int maxLevel(String id);

    /** Rarity, or {@link Rarity#COMMON} for an unknown id. */
    Rarity rarity(String id);

    /** Plain display name with the level as a Roman numeral, e.g. {@code Vein Rend II}. */
    String displayName(String id, int level);

    /**
     * The working level of an enchant on a custom item (tool): 0 when it is not there, disabled, or the item is not a
     * custom tool.
     */
    int level(ItemStack stack, String id);

    /** All working enchants on a custom item: id → level (disabled ones left out). */
    Map<String, Integer> enchants(ItemStack stack);

    /**
     * A number of an enchant at a level: a per-level value ({@code values.<key>}, index level-1) or a constant
     * ({@code params.<key>}). {@link Double#NaN} when the enchant or key does not exist.
     */
    double value(String id, String key, int level);

    /** The book or scroll this item claims to be (not checked against the database). */
    Optional<EnchantBook> book(ItemStack stack);

    /**
     * Mints a new book (E2 drops, crates, bosses): writes its database row, then builds the item on the main thread.
     * The caller gives the item to the player. Completes exceptionally on a database error or an unknown enchant.
     *
     * @param origin short source tag for history and audit, e.g. {@code drop:mining}, {@code crate:blaze}
     * @param owner  the player it is created for (audit, collection), or null
     */
    CompletableFuture<ItemStack> createBook(String enchantId, int level, String origin, UUID owner);

    /** Mints a new Disenchant Scroll (crates, Black Market), like {@link #createBook}. */
    CompletableFuture<ItemStack> createScroll(String origin, UUID owner);

    /**
     * Builds a book item now (main thread) whose row the caller writes in its own transaction with {@link #insertBook}
     * (E5: armor enchants that a wardrobe merge returns as books). Throws {@link IllegalArgumentException} for an unknown
     * enchant or a level outside 1..max.
     */
    PreparedBook prepareBook(String enchantId, int level, String origin);

    /**
     * SQL for the caller's transaction (any thread, inside a database task): writes the {@code enchant_books} row of a
     * {@link #prepareBook prepared} book (active) and marks the enchant as found this season. The caller audits.
     *
     * @param owner   the player (account) it is made for
     * @param profile the owner's profile key (books are profile items, P4)
     * @param reason  short tag stored on the row, e.g. {@code merge:<entry id>}
     */
    void insertBook(Connection connection, PreparedBook book, String origin, UUID owner, UUID profile, String reason) throws SQLException;

    /**
     * Opens the Enchanter's Armor screen of one wardrobe entry (E5; the set screen's Enchants button). Main thread.
     *
     * @param parent the menu Back returns to, or null
     */
    void openArmor(Player player, UUID entryId, Menu parent);
}
