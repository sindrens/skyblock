package net.skybornmc.skyblock.achievements;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.audit.AuditEntry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AuditFeedsTest {

    private final UUID steve = UUID.randomUUID();
    private final UUID alex = UUID.randomUUID();

    private static AuditEntry row(AuditCategory category, String action, Actor actor, String target, Long amount, Map<String, Object> details) {
        return new AuditEntry(0, 0, 0, "s", category, action, actor, target, null, amount, null, null, null, null, details);
    }

    @Test
    void salesCountItemsMoneyAndTheBestSaleOfTheNamedProfile() {
        UUID profile2 = UUID.randomUUID();
        List<AuditFeeds.Feed> feeds = AuditFeeds.map(row(AuditCategory.ECONOMY, "sell", Actor.player(steve, "Steve"), steve.toString(), 12_500L,
                Map.of("items", Map.of("cobblestone", 640L, "coal", 64L), "profile", profile2.toString())));
        assertEquals(List.of(new AuditFeeds.Feed(steve, profile2, Metric.MONEY_EARNED, 12_500),
                new AuditFeeds.Feed(steve, profile2, Metric.BEST_SALE, 12_500),
                new AuditFeeds.Feed(steve, profile2, Metric.ITEMS_SOLD, 704)), feeds);
        // Profile 1 has no "profile" detail: the player UUID is the profile key.
        assertEquals(steve, AuditFeeds.map(row(AuditCategory.ECONOMY, "sell", Actor.SYSTEM, steve.toString(), 5L, Map.of())).getFirst().profileKey());
    }

    @Test
    void tradesCountForBothSidesEnchantsAndVisitsForTheActor() {
        assertEquals(List.of(new AuditFeeds.Feed(steve, null, Metric.TRADES_COMPLETED, 1), new AuditFeeds.Feed(alex, null, Metric.TRADES_COMPLETED, 1)),
                AuditFeeds.map(row(AuditCategory.TRADE, "trade_complete", Actor.player(steve, "Steve"), alex.toString(), null, Map.of())));
        assertEquals(List.of(new AuditFeeds.Feed(steve, null, Metric.ENCHANTS_APPLIED, 1)),
                AuditFeeds.map(row(AuditCategory.ITEM, "enchant_apply", Actor.player(steve, "Steve"), steve.toString(), 100L, Map.of())));
        assertEquals(List.of(new AuditFeeds.Feed(steve, null, Metric.ISLANDS_VISITED, 1)),
                AuditFeeds.map(row(AuditCategory.ISLAND, "island_visit", Actor.player(steve, "Steve"), UUID.randomUUID().toString(), null, Map.of())));
    }

    @Test
    void otherRowsAreIgnored() {
        assertFalse(AuditFeeds.relevant(row(AuditCategory.ECONOMY, "pay", Actor.player(steve, "Steve"), alex.toString(), 5L, Map.of())));
        assertFalse(AuditFeeds.relevant(row(AuditCategory.CHAT, "chat", Actor.player(steve, "Steve"), null, null, Map.of())));
        assertEquals(List.of(), AuditFeeds.map(row(AuditCategory.ECONOMY, "sell", Actor.SYSTEM, "not-a-uuid", 5L, Map.of())));
        assertEquals(List.of(), AuditFeeds.map(row(AuditCategory.ITEM, "enchant_apply", Actor.SYSTEM, null, null, Map.of())));
        assertEquals(List.of(), AuditFeeds.map(row(AuditCategory.ITEM, "enchant_undo", Actor.player(steve, "Steve"), null, null, Map.of())));
    }
}
