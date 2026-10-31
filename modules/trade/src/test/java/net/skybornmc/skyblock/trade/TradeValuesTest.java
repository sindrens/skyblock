package net.skybornmc.skyblock.trade;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Owner R3-38: the auction median needs 5+ different sellers and buyers, and same-person/same-IP pairs never count. */
class TradeValuesTest {

    private static List<UUID> people(int n) {
        List<UUID> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(UUID.randomUUID());
        }
        return out;
    }

    @Test
    void fiveSellersAndFiveBuyersGiveAMedian() {
        List<UUID> sellers = people(5);
        List<UUID> buyers = people(5);
        List<TradeValues.Sale> sales = new ArrayList<>();
        long[] prices = {100, 300, 200, 500, 400};
        for (int i = 0; i < 5; i++) {
            sales.add(new TradeValues.Sale(sellers.get(i), buyers.get(i), prices[i]));
        }
        assertEquals(OptionalLong.of(300), TradeValues.median(sales, (a, b) -> false, 5, 5));
        assertEquals(OptionalLong.empty(), TradeValues.median(sales.subList(0, 4), (a, b) -> false, 5, 5), "only 4 sellers");
    }

    @Test
    void oneSellerSellingToManyIsNotEnough() {
        UUID seller = UUID.randomUUID();
        List<TradeValues.Sale> sales = new ArrayList<>();
        for (UUID buyer : people(10)) {
            sales.add(new TradeValues.Sale(seller, buyer, 1_000_000));
        }
        assertEquals(OptionalLong.empty(), TradeValues.median(sales, (a, b) -> false, 5, 5));
    }

    @Test
    void sameAddressPairsAreIgnored() {
        List<UUID> sellers = people(5);
        List<UUID> buyers = people(5);
        List<TradeValues.Sale> sales = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            sales.add(new TradeValues.Sale(sellers.get(i), buyers.get(i), 100));
        }
        // an alt pair pumps the price: one of the five sales is between two accounts on one address
        UUID alt = buyers.get(4);
        sales.set(4, new TradeValues.Sale(sellers.get(4), alt, 9_999_999));
        assertEquals(OptionalLong.empty(), TradeValues.median(sales, (a, b) -> b.equals(alt), 5, 5),
                "without the alt pair only 4 sellers are left");
        assertEquals(OptionalLong.of(100), TradeValues.median(sales, (a, b) -> b.equals(alt), 4, 4));
    }

    @Test
    void evenCountsTakeTheLowerMiddle() {
        List<TradeValues.Sale> sales = new ArrayList<>();
        long[] prices = {10, 20, 30, 40};
        for (long p : prices) {
            sales.add(new TradeValues.Sale(UUID.randomUUID(), UUID.randomUUID(), p));
        }
        assertEquals(OptionalLong.of(20), TradeValues.median(sales, (a, b) -> false, 4, 4));
    }

    @Test
    void defaultsMatchTheOwner() {
        TradeConfig cfg = new TradeConfig();
        assertEquals(5, cfg.valueAhMinSellers);
        assertEquals(5, cfg.valueAhMinBuyers);
    }
}
