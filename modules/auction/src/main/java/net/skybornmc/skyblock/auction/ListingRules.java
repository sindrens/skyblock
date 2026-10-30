package net.skybornmc.skyblock.auction;

import java.util.Locale;
import java.util.Optional;

/** Anti-abuse rules (pure, unit-tested): price range, new accounts, listing slots, cooldown and relist spam. */
final class ListingRules {

    static final String SLOT_PERMISSION = "skyblock.auction.slots.";

    /** Why a listing or purchase is refused; texts: {@code rules.<key>}. */
    enum Problem {
        PRICE_TOO_LOW, PRICE_TOO_HIGH, NO_SLOTS, COOLDOWN, RELIST_LIMIT;

        String key() {
            return name().toLowerCase(Locale.ROOT).replace('_', '-');
        }
    }

    /**
     * @param value the limit that was hit: min/max price, slot count, milliseconds left on the cooldown, the relist
     *              window in minutes, or the playtime still needed in milliseconds
     */
    record Rejection(Problem problem, long value) {
    }

    /** A seller's listing history, read inside the listing transaction. */
    record Stats(int open, long lastCreatedAt, int sameItemRecent) {
        static final Stats NONE = new Stats(0, 0, 0);
    }

    private ListingRules() {
    }

    static Optional<Rejection> price(AuctionConfig config, long price) {
        if (price < config.priceMin) {
            return Optional.of(new Rejection(Problem.PRICE_TOO_LOW, config.priceMin));
        }
        if (price > config.priceMax) {
            return Optional.of(new Rejection(Problem.PRICE_TOO_HIGH, config.priceMax));
        }
        return Optional.empty();
    }

    /** Slots, cooldown and relist limit against the seller's history. */
    static Optional<Rejection> history(AuctionConfig config, Stats stats, int slots, long now) {
        if (stats.open() >= slots) {
            return Optional.of(new Rejection(Problem.NO_SLOTS, slots));
        }
        long cooldown = config.listingCooldownSeconds * 1000L;
        if (cooldown > 0 && stats.lastCreatedAt() > 0 && now - stats.lastCreatedAt() < cooldown) {
            return Optional.of(new Rejection(Problem.COOLDOWN, cooldown - (now - stats.lastCreatedAt())));
        }
        if (config.relistMaxSameItem > 0 && stats.sameItemRecent() >= config.relistMaxSameItem) {
            return Optional.of(new Rejection(Problem.RELIST_LIMIT, config.relistWindowMinutes));
        }
        return Optional.empty();
    }

    /** Start of the relist window. */
    static long relistSince(AuctionConfig config, long now) {
        return now - config.relistWindowMinutes * 60_000L;
    }

    /**
     * Listing slots: the configured default, raised by the highest {@code skyblock.auction.slots.<n>} the player has,
     * capped at {@code listing.max-slots}.
     *
     * @param granted permission nodes the player has (value true)
     */
    static int slots(AuctionConfig config, Iterable<String> granted) {
        int best = config.slots;
        for (String node : granted) {
            if (node == null || !node.toLowerCase(Locale.ROOT).startsWith(SLOT_PERMISSION)) {
                continue;
            }
            try {
                best = Math.max(best, Integer.parseInt(node.substring(SLOT_PERMISSION.length())));
            } catch (NumberFormatException ignored) {
                // skyblock.auction.slots.* or a typo: ignored
            }
        }
        return Math.max(1, Math.min(best, Math.max(config.slots, config.maxSlots)));
    }

    /**
     * Whether a custom item id is blocked by {@code items.blocked-ids} ({@code starter_*} = prefix match, otherwise exact,
     * case-insensitive).
     */
    static boolean idBlocked(String id, Iterable<String> patterns) {
        if (id == null) {
            return false;
        }
        String lower = id.toLowerCase(Locale.ROOT);
        for (String raw : patterns) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String p = raw.trim().toLowerCase(Locale.ROOT);
            if (p.endsWith("*") ? lower.startsWith(p.substring(0, p.length() - 1)) : lower.equals(p)) {
                return true;
            }
        }
        return false;
    }
}
