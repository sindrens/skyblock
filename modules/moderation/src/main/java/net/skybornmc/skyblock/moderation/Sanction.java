package net.skybornmc.skyblock.moderation;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * One row of the {@code sanctions} table (P7, spec 13.1). Account-wide: {@code target} is the player UUID. For money and
 * items {@code profileKey} is the profile it was taken from (paid back / given back to exactly that profile).
 *
 * @param expires  freeze/trade end ({@code null} = until lifted); unused for money and items
 * @param amount   money removed ({@link Kind#MONEY}), else {@code null}
 * @param currency the currency key of {@code amount} ({@code money})
 * @param itemText shown summary of a confiscated item ("Diamond Sword x1")
 */
record Sanction(long id, Kind kind, UUID target, String targetName, UUID profileKey, UUID issuer, String issuerName,
                String reason, long created, Long expires, Long amount, String currency, String itemText, State state,
                UUID handledBy, String handledByName, Long handledAt, String handledReason) {

    /** FREEZE = freeze balance, TRADE = block trading, MONEY = remove money, ITEM = confiscate item. */
    enum Kind {
        FREEZE, TRADE, MONEY, ITEM;

        /** Config/permission spelling ({@code freeze}, {@code trade}, {@code money}, {@code confiscate}). */
        String id() {
            return this == ITEM ? "confiscate" : name().toLowerCase(Locale.ROOT);
        }

        /** Blocks something while active (freeze and block trading); money and items are one-off actions. */
        boolean blocking() {
            return this == FREEZE || this == TRADE;
        }

        static Optional<Kind> parse(String raw) {
            if (raw == null) {
                return Optional.empty();
            }
            String key = raw.strip().toLowerCase(Locale.ROOT);
            for (Kind k : values()) {
                if (k.id().equals(key)) {
                    return Optional.of(k);
                }
            }
            return Optional.empty();
        }
    }

    /**
     * ACTIVE = in force (freeze/trade) or held (money/item); PENDING = item row written but not yet taken from the
     * player (never restorable); LIFTED / EXPIRED = freeze/trade ended; RESTORED = money/item given back; FAILED = the
     * item could not be taken (nothing happened).
     */
    enum State { ACTIVE, PENDING, LIFTED, EXPIRED, RESTORED, FAILED }

    /** In force right now: an active freeze/trade block that has not run out. */
    boolean blocksAt(long now) {
        return kind.blocking() && state == State.ACTIVE && (expires == null || expires > now);
    }

    /** Ran out by time but not marked yet. */
    boolean expiredAt(long now) {
        return kind.blocking() && state == State.ACTIVE && expires != null && expires <= now;
    }

    /** Money or an item that can be given back with {@code /psanction restore}. */
    boolean restorable() {
        return (kind == Kind.MONEY || kind == Kind.ITEM) && state == State.ACTIVE;
    }

    /** Same row with another state and handler. */
    Sanction handled(State newState, UUID by, String byName, long at, String why) {
        return new Sanction(id, kind, target, targetName, profileKey, issuer, issuerName, reason, created, expires, amount,
                currency, itemText, newState, by, byName, at, why);
    }

    Sanction withId(long newId) {
        return new Sanction(newId, kind, target, targetName, profileKey, issuer, issuerName, reason, created, expires, amount,
                currency, itemText, state, handledBy, handledByName, handledAt, handledReason);
    }
}
