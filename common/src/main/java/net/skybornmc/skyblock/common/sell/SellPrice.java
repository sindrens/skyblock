package net.skybornmc.skyblock.common.sell;

/**
 * The current sell price of one item (M3.4), before any sell bonus.
 *
 * @param itemKey         price table key: a vanilla item id ({@code coal}) or {@code custom:<id>} for a custom item
 * @param category        price table category ({@code crops}, {@code ores} ...)
 * @param base            base price per item from {@code sell.yml} (whole money units)
 * @param multiplier      the global dynamic multiplier (0.40 .. 1.50 by default; 1.0 = base)
 * @param hot             whether this is today's Hot Item
 * @param hotFactor       the Hot Item factor applied on top (1.25 for +25%, 1.0 when not hot)
 * @param trend           how the multiplier moved over the last hour
 */
public record SellPrice(String itemKey, String category, long base, double multiplier, boolean hot, double hotFactor,
                        Trend trend) {

    /** The price of one item right now (fractional: cheap items like cobblestone are below $1 after a drop). */
    public double unit() {
        return base * multiplier * hotFactor;
    }

    /** Percent from the base price, Hot Item included (e.g. -12.5 or +25.0). */
    public double percentFromBase() {
        return (multiplier * hotFactor - 1.0) * 100.0;
    }
}
