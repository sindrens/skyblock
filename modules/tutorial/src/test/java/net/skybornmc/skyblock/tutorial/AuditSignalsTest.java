package net.skybornmc.skyblock.tutorial;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.audit.AuditEntry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditSignalsTest {

    private static AuditEntry row(AuditCategory cat, String action, Actor actor, String target, Map<String, Object> details) {
        return new AuditEntry(1, 0, 1, "test", cat, action, actor, target, null, null, null, null, null, null, details);
    }

    @Test
    void theBundledStepsListenToTheirRows() {
        Set<String> wanted = AuditSignals.wanted(CatalogTest.bundled(new ArrayList<>()));
        // The 5 Nov 2026 steps listen to the shop; the quest line v2 (batch 6) brings its own signals and this list.
        assertTrue(wanted.containsAll(List.of("item:island_tool_upgrade", "island:upgrade_buy")), wanted.toString());
    }

    @Test
    void actorFirstThenTargetPlayer() {
        UUID alex = UUID.randomUUID();
        Set<String> wanted = Set.of("item:island_tool_upgrade", "economy:shop_buy");
        AuditSignals.Hit hit = AuditSignals.map(row(AuditCategory.ITEM, "island_tool_upgrade", Actor.player(alex, "Alex"),
                alex.toString(), Map.of("kind", "hoe", "levels", 1, "nested", Map.of())), wanted).orElseThrow();
        assertEquals(alex, hit.player());
        assertEquals("item:island_tool_upgrade", hit.signal().target());
        assertEquals(Map.of("kind", "hoe", "levels", "1"), hit.signal().details());
        UUID steve = UUID.randomUUID();
        assertEquals(steve, AuditSignals.map(row(AuditCategory.ECONOMY, "shop_buy", Actor.SYSTEM, steve.toString(), Map.of()),
                wanted).orElseThrow().player());
        assertTrue(AuditSignals.map(row(AuditCategory.ECONOMY, "sell", Actor.SYSTEM, steve.toString(), Map.of()), wanted).isEmpty());
        assertTrue(AuditSignals.map(row(AuditCategory.ECONOMY, "shop_buy", Actor.SYSTEM, "island:x", Map.of()), wanted).isEmpty());
    }
}
