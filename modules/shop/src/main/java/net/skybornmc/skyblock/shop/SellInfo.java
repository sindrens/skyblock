package net.skybornmc.skyblock.shop;

import net.skybornmc.skyblock.common.sell.SellPrice;

import java.util.Optional;

/**
 * GUI-P3 5.4: whether a shop item can be sold now, its live sell price and how many the player carries. Built on the
 * main thread by {@link ShopModule#sellInfo}.
 *
 * @param sellable the sell service exists, the item is not a spawner and the sell table buys it
 * @param price    today's sell price (before bonuses), empty when not sellable
 * @param carried  sellable items of this kind in the storage slots (0 when not asked for)
 */
record SellInfo(boolean sellable, Optional<SellPrice> price, long carried) {

    static final SellInfo NONE = new SellInfo(false, Optional.empty(), 0);
}
