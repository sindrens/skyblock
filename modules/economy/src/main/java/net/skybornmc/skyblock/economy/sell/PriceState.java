package net.skybornmc.skyblock.economy.sell;

/**
 * The dynamic state of one item (immutable; {@code sell_prices} row).
 *
 * @param multiplier current multiplier (1.0 = base)
 * @param impact     multiplier lost per item sold (adaptive, recomputed hourly)
 * @param lastSaleAt time of the last sale (ms), 0 = never
 * @param hourAgo    the multiplier at the last hourly recompute (for the trend)
 * @param volume     items sold per hour, last 24 h
 * @param soldTotal  items sold since the state was created (statistics)
 */
public record PriceState(String key, double multiplier, double impact, long lastSaleAt, double hourAgo, VolumeWindow volume,
                         long soldTotal) {

    public static PriceState fresh(String key, double impact) {
        return new PriceState(key, 1.0, impact, 0, 1.0, VolumeWindow.EMPTY, 0);
    }

    public PriceState withMultiplier(double m) {
        return new PriceState(key, m, impact, lastSaleAt, hourAgo, volume, soldTotal);
    }

    public boolean quiet(long now, int quietMinutes) {
        return lastSaleAt <= 0 || now - lastSaleAt >= quietMinutes * 60_000L;
    }
}
