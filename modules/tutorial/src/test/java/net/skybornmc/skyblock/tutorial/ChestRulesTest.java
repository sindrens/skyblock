package net.skybornmc.skyblock.tutorial;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** TUT-FIX: the chest step detects the real starter chest and can always be finished. */
class ChestRulesTest {

    private static final ChestRules.Spot CHEST = new ChestRules.Spot("islands", 999, 101, -2);
    private static final ChestRules.Spot OLD = new ChestRules.Spot("islands", -1, 101, -2);

    @Test
    void theRememberedChestCounts() {
        assertTrue(ChestRules.counts(List.of(CHEST), CHEST, false));
        assertTrue(ChestRules.counts(List.of(new ChestRules.Spot("islands", 1000, 101, -2), CHEST), CHEST, false), "either half of a double chest");
    }

    @Test
    void withoutAPointForThisIslandAnyStorageOnTheOwnIslandCounts() {
        // The owner's report: the remembered point was from an older island, so the real chest never counted. A point
        // that is not on the player's current island is dropped before this rule (Targets.chest), so known is null.
        assertTrue(ChestRules.counts(List.of(CHEST), null, true), "stale point dropped, barrel, chest not found at paste");
        assertFalse(ChestRules.counts(List.of(CHEST), null, false), "someone else's island");
    }

    @Test
    void withAPointOnlyThatChestCounts() {
        assertFalse(ChestRules.counts(List.of(OLD), CHEST, true), "an empty chest the player placed is not the starter chest");
        assertFalse(ChestRules.counts(List.of(), CHEST, true), "menus and entity inventories have no blocks");
    }

    @Test
    void withoutTheIslandsModuleEveryContainerCounts() {
        assertTrue(ChestRules.counts(List.of(CHEST), null, null));
    }

    @Test
    void doneWhenOpenedAndTheToolIsTakenOrThereWasNone() {
        assertFalse(ChestRules.done(false, true, true, false), "never without an open");
        assertTrue(ChestRules.done(true, true, true, false), "tool taken");
        assertFalse(ChestRules.done(true, true, false, true), "the pickaxe is still in the chest");
        assertTrue(ChestRules.done(true, true, false, false), "no tool in the chest (kit claimed before): opening is enough");
        assertTrue(ChestRules.done(true, false, false, true), "need-tool empty");
    }
}
