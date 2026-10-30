package net.skybornmc.skyblock.economy.admin;

import net.skybornmc.skyblock.common.economy.AccountRef;
import net.skybornmc.skyblock.common.economy.Currency;

/**
 * One ledger row as the economy watch reads it (M3.8): only what the sums and the anomaly rules need.
 *
 * @param from        the paying account, {@code null} when the money was created
 * @param to          the receiving account, {@code null} when the money was removed
 * @param fromBalance the payer's balance after the change (nullable)
 * @param toBalance   the receiver's balance after the change (nullable)
 */
public record LedgerRow(long id, long time, Currency currency, AccountRef from, AccountRef to, long amount, Long fromBalance,
                        Long toBalance, String reason) {

    public Flow flow() {
        if (from == null) {
            return Flow.CREATED;
        }
        return to == null ? Flow.REMOVED : Flow.MOVED;
    }

    /** UTC hour bucket (epoch millis / 1 h). */
    public long hour() {
        return Buckets.hour(time);
    }
}
