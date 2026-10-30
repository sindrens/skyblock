package net.skybornmc.skyblock.enchants;

import net.skybornmc.skyblock.common.tools.ToolPerk;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Everything one Enchanter action changes, decided on the main thread before any database work (ENCHANTS §4).
 * {@link OpRunner} runs it as <b>one</b> database transaction (books consumed, new books inserted, daily roll counted,
 * tool perks saved and crystals paid or refunded); {@link EnchantOps} then changes the items, or undoes the database part
 * if the items are not where they were.
 *
 * @param id          unique operation id (idempotency key {@code enchant:<id>}, stored as the book rows' reason)
 * @param action      apply, upgrade, combine, combine_tool, remove, shatter, roll, scroll_buy
 * @param toolId      the tool whose perks change, or null
 * @param perksBefore the tool's perks now (the item must still have exactly these when the result comes back)
 * @param perksAfter  the tool's perks after the action
 * @param consume     books/scrolls used up (must be active rows matching kind, enchant and level)
 * @param mint        new books/scrolls (rows inserted active; the items are built before and handed out after)
 * @param cost        crystals the player pays (0 = none)
 * @param refund      crystals the player gets back (shatter; 0 = none)
 * @param daily       a daily roll counter to use, or null
 * @param ledgerReason economy ledger reason ({@code enchant_apply} …)
 * @param details     audit details
 */
record OpPlan(UUID id, String action, UUID player, String playerName, int season, UUID toolId, List<ToolPerk> perksBefore,
              List<ToolPerk> perksAfter, List<Book> consume, List<Book> mint, long cost, long refund, Daily daily,
              String ledgerReason, Map<String, Object> details) {

    /** A book or scroll row (expected or new). {@code origin} only matters for new rows. */
    record Book(UUID id, boolean scroll, String enchant, int level, String origin) {
        Book {
            Objects.requireNonNull(id, "id");
        }

        static Book book(UUID id, String enchant, int level, String origin) {
            return new Book(id, false, enchant, level, origin);
        }

        static Book scroll(UUID id, String origin) {
            return new Book(id, true, null, 0, origin);
        }
    }

    /** One use of a daily roll counter. */
    record Daily(String day, String roll, int limit) {
    }

    OpPlan {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(player, "player");
        perksBefore = perksBefore == null ? List.of() : List.copyOf(perksBefore);
        perksAfter = perksAfter == null ? List.of() : List.copyOf(perksAfter);
        consume = consume == null ? List.of() : List.copyOf(consume);
        mint = mint == null ? List.of() : List.copyOf(mint);
        details = details == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(details));
        if (cost < 0 || refund < 0 || (cost > 0 && refund > 0)) {
            throw new IllegalArgumentException("An operation either costs or refunds crystals");
        }
    }

    /** Stored as the reason of changed book rows, so an undo only touches rows of this operation. */
    String reason() {
        return "op:" + id;
    }
}
