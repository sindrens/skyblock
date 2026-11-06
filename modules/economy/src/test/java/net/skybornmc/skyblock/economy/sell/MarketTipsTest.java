package net.skybornmc.skyblock.economy.sell;

import net.skybornmc.skyblock.common.sell.SellPrice;
import net.skybornmc.skyblock.common.sell.Trend;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** ECON-SELL-SHOP-AH A4: the items furthest above base, hot item and cheap items left out. */
class MarketTipsTest {

    static SellPrice p(String key, double base, double multiplier) {
        return new SellPrice(key, "ores", base, multiplier, false, 1.0, Trend.STEADY);
    }

    @Test
    void picksTheBiggestRisersAboveTheMinimums() {
        List<SellPrice> all = List.of(p("dirt", 0.5, 1.5), p("coal", 10, 1.30), p("iron", 18, 1.30), p("gold", 25, 1.10),
                p("diamond", 61, 1.02), p("hot", 50, 1.5), p("wheat", 10, 0.8));
        List<String> keys = MarketTips.pick(all, "hot", 5, 5, 5).stream().map(SellPrice::itemKey).toList();
        assertEquals(List.of("iron", "coal", "gold"), keys, "same rise: the dearer first; dirt, hot, small and falling left out");
        assertEquals(1, MarketTips.pick(all, null, 5, 5, 1).size());
        assertEquals("hot", MarketTips.pick(all, null, 5, 5, 1).getFirst().itemKey());
    }

    @Test
    void tipsAreCentredOnRowTwo() {
        assertEquals(13, MarketTipsMenu.firstSlot(1));
        assertEquals(11, MarketTipsMenu.firstSlot(5));
        assertEquals(10, MarketTipsMenu.firstSlot(7));
        assertEquals(9, MarketTipsMenu.firstSlot(20));
    }
}
