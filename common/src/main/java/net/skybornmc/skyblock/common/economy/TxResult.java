package net.skybornmc.skyblock.common.economy;

/**
 * The outcome of a {@link Transaction}. Balances are the values the database holds right after the change (the
 * scoreboard cache is updated from them), {@code null} for a side that is not part of the transaction.
 *
 * @param ledgerId    the ledger row written (0 when nothing changed)
 * @param fromBalance the paying account's balance after the change
 * @param toBalance   the receiving account's balance after the change
 */
public record TxResult(Status status, long ledgerId, Long fromBalance, Long toBalance) {

    public enum Status {
        /** Done: balances changed and a ledger row was written. */
        SUCCESS,
        /** The paying account has less than the amount. Nothing changed. */
        INSUFFICIENT_FUNDS,
        /** The receiving account would go above the largest possible balance. Nothing changed. */
        OVERFLOW,
        /** A transaction with the same idempotency key was already done (a retried or duplicated request). Nothing changed. */
        DUPLICATE,
        /** Amount ≤ 0, same account on both sides, bad reason, … Nothing changed. */
        INVALID,
        /** Database error. Nothing changed (the transaction was rolled back). */
        ERROR
    }

    public boolean ok() {
        return status == Status.SUCCESS;
    }

    public static TxResult of(Status status) {
        return new TxResult(status, 0, null, null);
    }
}
