package net.skybornmc.skyblock.auction;

import java.util.UUID;

/** One row of {@code ah_listings}. Immutable; state changes go through {@link AuctionStore} and come back as new rows. */
record Listing(long id, UUID seller, String sellerName, String currency, long price, long fee, long tax, ItemSnapshot item,
               ListingStatus status, long createdAt, long expiresAt, UUID buyer, String buyerName, Long buyStartedAt,
               Long endedAt, boolean sellerNotice) {

    Listing withStatus(ListingStatus status) {
        return new Listing(id, seller, sellerName, currency, price, fee, tax, item, status, createdAt, expiresAt, buyer, buyerName,
                buyStartedAt, endedAt, sellerNotice);
    }

    Listing withBuyer(UUID buyer, String buyerName, long at) {
        return new Listing(id, seller, sellerName, currency, price, fee, tax, item, ListingStatus.BUYING, createdAt, expiresAt,
                buyer, buyerName, at, endedAt, sellerNotice);
    }

    Listing sold(long tax, long at) {
        return new Listing(id, seller, sellerName, currency, price, fee, tax, item, ListingStatus.SOLD, createdAt, expiresAt,
                buyer, buyerName, buyStartedAt, at, true);
    }

    long remaining(long now) {
        return Math.max(0, expiresAt - now);
    }
}
