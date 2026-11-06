package net.skybornmc.skyblock.economy.sell;

import net.skybornmc.skyblock.common.sell.SellPrice;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Market tips (ECON-SELL-SHOP-AH A4): the items whose price is furthest above their base right now. Pure; the menu caches
 * the result and recomputes it every {@code sell.market-tips.refresh-seconds} from memory (no I/O).
 */
public final class MarketTips {

    private MarketTips() {
    }

    /**
     * @param prices     every sellable price right now
     * @param hotKey     today's hot item (shown on its own tile, so not a tip); may be null
     * @param minBase    keep items worth at least this (5: keeps dirt out)
     * @param minPercent keep items at least this many percent above base (5)
     * @param size       most tips (5)
     */
    public static List<SellPrice> pick(Collection<SellPrice> prices, String hotKey, double minBase, double minPercent, int size) {
        List<SellPrice> out = new ArrayList<>();
        for (SellPrice p : prices) {
            if (p == null || p.itemKey().equals(hotKey) || p.base() < minBase) {
                continue;
            }
            double rise = (p.multiplier() - 1.0) * 100.0; // the hot bonus is not a market move
            if (rise >= minPercent) {
                out.add(p);
            }
        }
        out.sort(Comparator.comparingDouble((SellPrice p) -> p.multiplier()).reversed()
                .thenComparing(Comparator.comparingDouble(SellPrice::base).reversed())
                .thenComparing(SellPrice::itemKey));
        return List.copyOf(out.subList(0, Math.min(Math.max(0, size), out.size())));
    }
}
