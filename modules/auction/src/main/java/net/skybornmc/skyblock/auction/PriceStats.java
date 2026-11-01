package net.skybornmc.skyblock.auction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * ECON-SELL-SHOP-AH C5 (decided 560-562, R3-38): the typical price of an item = the median price per item of its sales in
 * the last {@code hints.median-days}, shown only when enough different players traded it ({@code hints.min-sales},
 * {@code min-distinct-sellers}, {@code min-distinct-buyers}, 5 each), so one player (or two friends) cannot set it. Pure.
 */
final class PriceStats {

    /** The typical price of one item. */
    record Stat(double median, int sales, int sellers, int buyers) {
    }

    private PriceStats() {
    }

    /** Median of the values (mean of the two middle values for an even count); 0 for none. */
    static double median(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return 0;
        }
        List<Double> sorted = new ArrayList<>(values);
        sorted.sort(null);
        int n = sorted.size();
        return n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
    }

    /** True when an item has enough sales, sellers and buyers for a typical price. */
    static boolean qualifies(int sales, int sellers, int buyers, int minSales, int minSellers, int minBuyers) {
        return sales >= minSales && sellers >= minSellers && buyers >= minBuyers;
    }

    /** Builds the stats of every qualifying item from the sales (item key → stat). */
    static Map<String, Stat> build(Collection<AuctionStore.Sale> sales, int minSales, int minSellers, int minBuyers) {
        Map<String, List<Double>> prices = new HashMap<>();
        Map<String, Set<UUID>> sellers = new HashMap<>();
        Map<String, Set<UUID>> buyers = new HashMap<>();
        for (AuctionStore.Sale s : sales) {
            if (s.itemKey() == null || s.unitPrice() <= 0) {
                continue;
            }
            prices.computeIfAbsent(s.itemKey(), k -> new ArrayList<>()).add(s.unitPrice());
            if (s.seller() != null) {
                sellers.computeIfAbsent(s.itemKey(), k -> new HashSet<>()).add(s.seller());
            }
            if (s.buyer() != null) {
                buyers.computeIfAbsent(s.itemKey(), k -> new HashSet<>()).add(s.buyer());
            }
        }
        Map<String, Stat> out = new HashMap<>();
        prices.forEach((key, list) -> {
            int sel = sellers.getOrDefault(key, Set.of()).size();
            int buy = buyers.getOrDefault(key, Set.of()).size();
            if (qualifies(list.size(), sel, buy, minSales, minSellers, minBuyers)) {
                out.put(key, new Stat(median(list), list.size(), sel, buy));
            }
        });
        return out;
    }

    /**
     * How far a price per item is from the typical price, as a factor of at least 1 (12x above and 12x below both give
     * 12). 1 when there is no typical price.
     */
    static double factor(double unitPrice, double typical) {
        if (typical <= 0 || unitPrice <= 0) {
            return 1;
        }
        return unitPrice >= typical ? unitPrice / typical : typical / unitPrice;
    }

    /** True when a second confirm is needed ({@code factor > warnFactor}). */
    static boolean farFromTypical(double unitPrice, double typical, double warnFactor) {
        return typical > 0 && factor(unitPrice, typical) > warnFactor;
    }
}
