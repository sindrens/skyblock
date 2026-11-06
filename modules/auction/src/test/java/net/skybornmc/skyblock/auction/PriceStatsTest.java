package net.skybornmc.skyblock.auction;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ECON-SELL-SHOP-AH C5: the typical price (median of enough different traders) and the far-price second confirm. */
class PriceStatsTest {

    private static final UUID[] PLAYERS = new UUID[12];

    static {
        for (int i = 0; i < PLAYERS.length; i++) {
            PLAYERS[i] = UUID.randomUUID();
        }
    }

    @Test
    void medianOfOddAndEvenCounts() {
        assertEquals(3, PriceStats.median(List.of(5.0, 1.0, 3.0)));
        assertEquals(2.5, PriceStats.median(List.of(4.0, 1.0, 2.0, 3.0)));
        assertEquals(0, PriceStats.median(List.of()));
        assertEquals(0, PriceStats.median(null));
    }

    @Test
    void aTypicalPriceNeedsFiveSalesSellersAndBuyers() {
        List<AuctionStore.Sale> sales = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            sales.add(new AuctionStore.Sale("diamond", 100 + i, PLAYERS[i], PLAYERS[6 + i]));
        }
        Map<String, PriceStats.Stat> stats = PriceStats.build(sales, 5, 5, 5);
        assertEquals(102, stats.get("diamond").median());
        assertEquals(5, stats.get("diamond").sellers());
        // Two friends trading back and forth can't set a price.
        List<AuctionStore.Sale> friends = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            friends.add(new AuctionStore.Sale("emerald", 1_000_000, PLAYERS[0], PLAYERS[1]));
        }
        assertFalse(PriceStats.build(friends, 5, 5, 5).containsKey("emerald"));
        List<AuctionStore.Sale> junk = List.of(new AuctionStore.Sale(null, 5, PLAYERS[0], PLAYERS[1]),
                new AuctionStore.Sale("dirt", 0, PLAYERS[0], PLAYERS[1]));
        assertTrue(PriceStats.build(junk, 1, 1, 1).isEmpty(), "no key or no price: skipped");
    }

    @Test
    void farPricesAskTwiceBothWays() {
        assertEquals(12, PriceStats.factor(1_200, 100));
        assertEquals(12, PriceStats.factor(100, 1_200), "12x below counts like 12x above");
        assertEquals(1, PriceStats.factor(100, 0), "no typical price");
        assertTrue(PriceStats.farFromTypical(1_200, 100, 10));
        assertFalse(PriceStats.farFromTypical(900, 100, 10));
        assertFalse(PriceStats.farFromTypical(1_000_000, 0, 10), "never without a typical price");
    }
}
