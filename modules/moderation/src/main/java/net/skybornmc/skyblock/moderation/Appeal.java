package net.skybornmc.skyblock.moderation;

import java.util.Locale;
import java.util.UUID;

/**
 * One row of {@code appeals} (spec 13.10): a player asks staff to lift or shorten one of their punishments.
 * {@code issuer*} = who gave the punishment, {@code handler*} = who handled the appeal, {@code response} = the deny reason
 * or what was decided, {@code oldExpires}/{@code newExpires} = the end before/after a reduce ({@code null} = permanent).
 */
record Appeal(long id, long punishmentId, Punishment.Type punishmentType, UUID player, String playerName, UUID issuer,
              String issuerName, String text, Status status, UUID handler, String handlerName, String response,
              Long oldExpires, Long newExpires, long created, Long handled, boolean playerNotified) {

    /**
     * The appeal state machine: {@code OPEN} is the only state that moves, and only once, to one of the final states
     * (first click wins, enforced in the database with {@code WHERE status = 'OPEN'}).
     */
    enum Status {
        /** Waiting for staff. */
        OPEN,
        /** Accepted: the punishment was lifted (pardon reason "Appeal accepted"). */
        ACCEPTED,
        /** Reduced: the punishment now ends earlier. */
        REDUCED,
        /** Denied with a reason; the same punishment can be appealed again after {@code appeal.cooldown}. */
        DENIED,
        /** Closed without a decision: the punishment ended or was lifted before staff handled it. */
        CLOSED;

        boolean terminal() {
            return this != OPEN;
        }

        /** Allowed moves: OPEN to any final state. Final states never change. */
        boolean canMoveTo(Status next) {
            return this == OPEN && next != OPEN;
        }

        String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    boolean open() {
        return status == Status.OPEN;
    }

    /** "Given by Alex" differs from the handler (spec: show the other staff member's name so staff can talk). */
    boolean otherStaff(UUID handler) {
        return issuer != null && handler != null && !issuer.equals(handler);
    }
}
