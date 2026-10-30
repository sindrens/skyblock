package net.skybornmc.skyblock.economy.admin;

import net.skybornmc.skyblock.common.sell.SellPrice;
import net.skybornmc.skyblock.common.sell.Trend;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SellIndexTest {

    private static SellPrice price(String key, long base, double multiplier, boolean hot) {
        return new SellPrice(key, "ores", base, multiplier, hot, hot ? 1.25 : 1.0, Trend.values()[0]);
    }

    @Test
    void basketWeightedByBasePrice() {
        SellIndex index = SellIndex.of(List.of(price("coal", 100, 0.5, false), price("diamond", 900, 1.0, false)));
        assertEquals((50 + 900) * 100.0 / 1_000, index.index(), 1e-9);
        assertEquals(2, index.items());
        assertEquals(1, index.below());
        assertEquals(0, index.above());
        assertEquals("coal", index.lowestKey());
        assertEquals(-50.0, index.lowestPercent(), 1e-9);
    }

    @Test
    void hotItemCountsAboveBase() {
        SellIndex index = SellIndex.of(List.of(price("wheat", 10, 1.0, true)));
        assertEquals(125.0, index.index(), 1e-9);
        assertEquals(1, index.above());
        assertEquals("wheat", index.hotKey());
        assertNull(index.lowestKey());
    }

    @Test
    void emptyTable() {
        SellIndex index = SellIndex.of(List.of());
        assertTrue(Double.isNaN(index.index()));
        assertEquals(0, index.items());
    }
}
