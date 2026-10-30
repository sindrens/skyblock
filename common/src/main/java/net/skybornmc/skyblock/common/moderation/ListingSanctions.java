package net.skybornmc.skyblock.common.moderation;

import net.skybornmc.skyblock.common.audit.Actor;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Phase P spec 13.1 "Block trading": the auction module cancels a player's active listings when staff block their
 * trading. Published by the auction module ({@code core.services().optional(ListingSanctions.class)}); the moderation
 * module calls it. Listings are never deleted: each one is cancelled by staff and its item goes to the seller's
 * collection bin, exactly like a staff removal.
 */
public interface ListingSanctions {

    /**
     * Cancels every active listing of every profile of {@code player} (player UUID). Listings that are being bought right
     * now are left alone (the purchase finishes). Completes with the number of listings cancelled; never exceptionally.
     */
    CompletableFuture<Integer> cancelListings(UUID player, String reason, Actor actor);
}
