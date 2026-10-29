package net.skybornmc.skyblock.economy;

import net.skybornmc.skyblock.common.economy.Currency;

import java.util.Locale;
import java.util.UUID;

/**
 * One row of {@code money_notes} (M3.2). The note item only carries {@link #id}; amount and currency always come from
 * this row when a note is redeemed.
 *
 * @param createdBy  the player who made it ({@code /withdraw}) or the staff member ({@code /eco note}); null for console
 * @param note       staff reason of an admin note, null otherwise
 * @param redeemedBy who cashed it, null while active
 */
record MoneyNote(UUID id, long amount, Currency currency, Kind kind, UUID createdBy, String createdName, long createdAt,
                 String note, UUID redeemedBy, String redeemedName, Long redeemedAt, Status status) {

    /** How the note was made. */
    enum Kind {
        /** {@code /withdraw}: the money was taken from the creator. */
        WITHDRAW,
        /** {@code /eco note}: created by staff without taking money. */
        ADMIN;

        String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        static Kind byKey(String key) {
            return "admin".equalsIgnoreCase(key) ? ADMIN : WITHDRAW;
        }
    }

    /** Lifecycle: {@code active → redeemed}, or {@code active → void} if it was never handed out. */
    enum Status {
        ACTIVE, REDEEMED, VOID;

        String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        static Status byKey(String key) {
            for (Status s : values()) {
                if (s.key().equalsIgnoreCase(key)) {
                    return s;
                }
            }
            return VOID; // unknown status: never redeemable
        }
    }

    /** First 8 characters of the id, shown on the item and in messages (the full id is in the audit log). */
    static String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }

    /** Idempotency key of the withdraw ledger row (one per note, ever). */
    static String withdrawKey(UUID id) {
        return "note_withdraw:" + id;
    }

    /** Idempotency key of the redeem ledger row (one per note, ever). */
    static String redeemKey(UUID id) {
        return "note_redeem:" + id;
    }
}
