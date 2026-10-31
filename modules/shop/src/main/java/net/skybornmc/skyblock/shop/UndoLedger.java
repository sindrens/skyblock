package net.skybornmc.skyblock.shop;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * F14 "Undo last buy": the one purchase per player that can still be undone (SHOP_DESIGN §4). Pure and thread-safe
 * (unit-tested). A new purchase replaces the old offer; an offer runs out after its window (15 s by default); a claim
 * removes it, so a purchase is refunded <b>exactly once</b> even with two quick clicks. The database guard
 * ({@code delivered → refunded}) and the ledger idempotency key ({@code shop_undo:<purchase id>}) back this up.
 */
final class UndoLedger {

    /**
     * One purchase that can be undone.
     *
     * @param purchase the purchase id ({@code shop_purchases.id})
     * @param profile  the profile that paid (the refund goes there; another active profile can't undo)
     * @param itemId   catalog item id
     * @param quantity items handed over (all of them must still be in the inventory)
     * @param total    money paid (the full refund)
     * @param until    epoch millis the offer runs out
     */
    record Offer(UUID purchase, UUID profile, String itemId, int quantity, long total, long until) {

        /** Whole seconds left at {@code now} (rounded up; 0 when it ran out). */
        int secondsLeft(long now) {
            long ms = until - now;
            return ms <= 0 ? 0 : (int) Math.min(Integer.MAX_VALUE, (ms + 999) / 1000);
        }
    }

    private final Map<UUID, Offer> offers = new ConcurrentHashMap<>();

    /** Offers {@code offer} to {@code player} (replaces an older one). */
    void offer(UUID player, Offer offer) {
        offers.put(player, offer);
    }

    /** The player's live offer at {@code now}; a run-out one is dropped. */
    Optional<Offer> peek(UUID player, long now) {
        Offer o = offers.get(player);
        if (o == null) {
            return Optional.empty();
        }
        if (o.until() <= now) {
            offers.remove(player, o);
            return Optional.empty();
        }
        return Optional.of(o);
    }

    /**
     * Takes the offer for purchase {@code purchase} if it is still live: true exactly once per offer. A run-out offer,
     * another purchase or a second claim gives false.
     */
    boolean claim(UUID player, UUID purchase, long now) {
        Offer o = offers.get(player);
        if (o == null || !o.purchase().equals(purchase) || o.until() <= now) {
            return false;
        }
        return offers.remove(player, o);
    }

    /** Drops the player's offer (quit, profile switch, setting turned off). */
    void clear(UUID player) {
        offers.remove(player);
    }

    void clearAll() {
        offers.clear();
    }
}
