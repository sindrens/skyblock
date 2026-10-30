package net.skybornmc.skyblock.common.shop;

import java.util.OptionalLong;

/**
 * Builder shop prices for other modules (published by the shop module: {@code core.services().optional(ShopPrices.class)}).
 * The island level uses them for block worth (PHASE-5 M5.2: 50% of the {@code /shop} price by default). Any thread.
 */
public interface ShopPrices {

    /** Price of one item in {@code /shop} by vanilla item id ({@code obsidian}), empty if the shop does not sell it. */
    OptionalLong buyPrice(String itemId);
}
