package net.skybornmc.skyblock.auction;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalDouble;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Batch 9: the "Cheapest now" hint on the sell page when there is no typical price yet. */
class CheapestNowTest {

    private static final UUID ME = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();

    private static Listing listing(UUID seller, String key, long price, int amount, long expires) {
        ItemSnapshot item = new ItemSnapshot(new byte[0], key, null, "DIAMOND", amount, "Diamond", Category.MATERIALS);
        return new Listing(1, seller, "x", "money", price, 0, 0, item, ListingStatus.ACTIVE, 0, expires, null, null, null, null, false);
    }

    @Test
    void cheapestPerItemOfOthersOnly() {
        List<Listing> all = List.of(
                listing(OTHER, "diamond", 640, 64, 100),   // 10 each
                listing(OTHER, "diamond", 90, 10, 100),    // 9 each: cheapest
                listing(ME, "diamond", 1, 1, 100),         // your own listing never counts
                listing(OTHER, "diamond", 1, 1, 5),        // expired
                listing(OTHER, "emerald", 1, 1, 100));     // another item
        assertEquals(OptionalDouble.of(9.0), AuctionModule.cheapestPerItem(all, "diamond", ME, 10));
        assertEquals(OptionalDouble.empty(), AuctionModule.cheapestPerItem(all, "gold_ingot", ME, 10));
    }
}
