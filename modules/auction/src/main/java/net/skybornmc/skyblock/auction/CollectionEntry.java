package net.skybornmc.skyblock.auction;

import java.util.UUID;

/**
 * One item waiting in a player's collection bin ({@code ah_collection}).
 *
 * @param listingId the listing it came from, or {@code null} for an item put back after a partial delivery
 * @param source    {@link Source} key
 */
record CollectionEntry(long id, UUID owner, Long listingId, String source, byte[] bytes, String material, int amount, String name,
                       long createdAt) {

    /** Why an item is in the bin (stored as the lowercase key; texts: {@code collection.source.<key>}). */
    enum Source {
        EXPIRED, CANCELLED, REMOVED, BOUGHT, FAILED, RETURNED;

        String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }
}
