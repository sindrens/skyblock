package net.skybornmc.skyblock.economy.admin;

import net.skybornmc.skyblock.common.sell.SellPrice;

import java.util.Collection;

/**
 * The sell price index of the dashboard (M3.8), pure: today's price of the whole sell table as a basket weighted by base
 * price, in percent of the base prices (100 = every item at its base price; dynamic prices move between 40 and 150, the
 * Hot Item adds on top). Also how many items sit below or above their base price and the cheapest one.
 *
 * @param index         basket price in percent of base ({@code NaN} for an empty table)
 * @param lowestKey     the item furthest below its base ({@code null} if none)
 * @param lowestPercent its percent from base (e.g. -35.0)
 * @param hotKey        today's Hot Item ({@code null} if none)
 */
public record SellIndex(double index, int items, int below, int above, String lowestKey, double lowestPercent, String hotKey) {

    /** Items within this many percent of base count as at base. */
    static final double AT_BASE_PERCENT = 0.5;

    public static SellIndex of(Collection<SellPrice> prices) {
        double base = 0;
        double now = 0;
        int below = 0;
        int above = 0;
        String lowest = null;
        double lowestPercent = Double.POSITIVE_INFINITY;
        String hot = null;
        int items = 0;
        for (SellPrice p : prices) {
            if (p == null || p.base() <= 0) {
                continue;
            }
            items++;
            base += p.base();
            now += p.unit();
            double percent = p.percentFromBase();
            if (percent < -AT_BASE_PERCENT) {
                below++;
            } else if (percent > AT_BASE_PERCENT) {
                above++;
            }
            if (percent < lowestPercent) {
                lowestPercent = percent;
                lowest = p.itemKey();
            }
            if (p.hot() && hot == null) {
                hot = p.itemKey();
            }
        }
        if (items == 0) {
            return new SellIndex(Double.NaN, 0, 0, 0, null, 0, null);
        }
        return new SellIndex(now * 100.0 / base, items, below, above, lowestPercent < -AT_BASE_PERCENT ? lowest : null,
                lowestPercent, hot);
    }
}
