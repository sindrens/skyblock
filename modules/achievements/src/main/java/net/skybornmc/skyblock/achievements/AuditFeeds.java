package net.skybornmc.skyblock.achievements;

import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.audit.AuditEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Turns audit rows other modules already write into achievement progress (pure, unit-tested), so sales, trades, applied
 * enchants and island visits need no new events. The rows (category/action) are the modules' documented audit actions:
 * <ul>
 *   <li>{@code economy/sell}: target = the seller's account, {@code details.profile} = the profile when it is not the
 *       first one, amount = money paid, {@code details.items} = item key → amount.</li>
 *   <li>{@code trade/trade_complete}: actor = player A, target = player B.</li>
 *   <li>{@code item/enchant_apply}, {@code item/enchant_combine_tool}: a book put on a tool by the actor.</li>
 *   <li>{@code island/island_visit}: the actor's first visit of that island today.</li>
 * </ul>
 */
final class AuditFeeds {

    /**
     * One change for one player.
     *
     * @param profileKey the profile the row names, or null = the player's active profile
     */
    record Feed(UUID player, UUID profileKey, Metric metric, long amount) {
    }

    static final String SELL = "sell";
    static final String TRADE_COMPLETE = "trade_complete";
    static final Set<String> ENCHANT_ACTIONS = Set.of("enchant_apply", "enchant_combine_tool");
    static final String ISLAND_VISIT = "island_visit";
    /** {@code island/island_like}: target = the liked island's id (AC3 island metric {@code likes}). */
    static final String ISLAND_LIKE = "island_like";

    private AuditFeeds() {
    }

    /** Cheap pre-filter for the audit thread (most rows are chat and commands). */
    static boolean relevant(AuditEntry e) {
        return switch (e.category()) {
            case ECONOMY -> SELL.equals(e.action());
            case TRADE -> TRADE_COMPLETE.equals(e.action());
            case ITEM -> ENCHANT_ACTIONS.contains(e.action());
            case ISLAND -> ISLAND_VISIT.equals(e.action()) || ISLAND_LIKE.equals(e.action());
            default -> false;
        };
    }

    /** The island a like row is about, or null for every other row. */
    static UUID islandLiked(AuditEntry e) {
        return e.category() == AuditCategory.ISLAND && ISLAND_LIKE.equals(e.action()) ? uuid(e.target()) : null;
    }

    static List<Feed> map(AuditEntry e) {
        if (!relevant(e) || ISLAND_LIKE.equals(e.action())) {
            return List.of();
        }
        List<Feed> out = new ArrayList<>(3);
        if (e.category() == AuditCategory.ECONOMY) {
            UUID seller = uuid(e.target());
            long paid = e.amount() == null ? 0 : e.amount();
            if (seller == null || paid <= 0) {
                return List.of();
            }
            UUID profile = uuid(String.valueOf(e.details().get("profile")));
            out.add(new Feed(seller, profile == null ? seller : profile, Metric.MONEY_EARNED, paid));
            out.add(new Feed(seller, profile == null ? seller : profile, Metric.BEST_SALE, paid));
            long items = items(e.details().get("items"));
            if (items > 0) {
                out.add(new Feed(seller, profile == null ? seller : profile, Metric.ITEMS_SOLD, items));
            }
            return out;
        }
        UUID actor = e.actor().uuid();
        if (actor == null) {
            return List.of();
        }
        switch (e.category()) {
            case TRADE -> {
                out.add(new Feed(actor, null, Metric.TRADES_COMPLETED, 1));
                UUID other = uuid(e.target());
                if (other != null && !other.equals(actor)) {
                    out.add(new Feed(other, null, Metric.TRADES_COMPLETED, 1));
                }
            }
            case ITEM -> out.add(new Feed(actor, null, Metric.ENCHANTS_APPLIED, 1));
            case ISLAND -> out.add(new Feed(actor, null, Metric.ISLANDS_VISITED, 1));
            default -> {
            }
        }
        return out;
    }

    private static long items(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return 0;
        }
        long total = 0;
        for (Object v : map.values()) {
            if (v instanceof Number n && n.longValue() > 0) {
                total = ProgressMath.add(total, n.longValue());
            }
        }
        return total;
    }

    private static UUID uuid(String text) {
        if (text == null || text.length() != 36) {
            return null;
        }
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
