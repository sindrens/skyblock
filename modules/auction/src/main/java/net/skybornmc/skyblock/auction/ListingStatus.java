package net.skybornmc.skyblock.auction;

import java.util.Locale;
import java.util.Optional;

/**
 * The state machine of a listing (stored as the lowercase key). Every transition is one conditional UPDATE on the
 * current state, so two transitions out of the same state can never both succeed.
 *
 * <pre>
 *   PENDING --fee paid--> ACTIVE --buyer claims--> BUYING --paid--> SOLD
 *      |                    |  \--seller/staff--> CANCELLED         |
 *      |                    \----time is up----> EXPIRED            \--buyer can't pay--> ACTIVE
 *      \--fee can't be paid--> FAILED
 * </pre>
 * PENDING and BUYING are short-lived; a crash can leave a listing in them, and recovery resolves it with the same
 * idempotency keys (so a payment is never taken twice).
 */
enum ListingStatus {
    PENDING,
    ACTIVE,
    BUYING,
    SOLD,
    CANCELLED,
    EXPIRED,
    FAILED;

    String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Still occupies one of the seller's listing slots. */
    boolean open() {
        return this == PENDING || this == ACTIVE || this == BUYING;
    }

    static Optional<ListingStatus> byKey(String key) {
        for (ListingStatus s : values()) {
            if (s.key().equalsIgnoreCase(key)) {
                return Optional.of(s);
            }
        }
        return Optional.empty();
    }
}
