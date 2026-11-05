package net.skybornmc.skyblock.economy.sell;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Owner 4 Nov 2026: one Auto-Sell Chest per island by default. */
class AutoSellChestsTest {

    @Test
    void oneChestPerIsland() {
        assertTrue(AutoSellChests.roomFor(0, 1));
        assertFalse(AutoSellChests.roomFor(1, 1));
        assertTrue(AutoSellChests.roomFor(2, 3));
    }
}
