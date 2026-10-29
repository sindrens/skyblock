package net.skybornmc.skyblock.economy;

import net.skybornmc.skyblock.common.economy.AccountRef;
import net.skybornmc.skyblock.common.economy.Currency;

/**
 * One ledger row. {@code from}/{@code to} are {@code null} for created/removed currency. Names are the players'
 * current names (null for islands and unknown players).
 */
record LedgerEntry(long id, long time, int season, Currency currency, AccountRef from, AccountRef to, long amount,
                   Long fromBalance, Long toBalance, String reason, String reference, String actorName,
                   String fromName, String toName) {

    /** True if {@code account} received the amount in this row. */
    boolean incoming(AccountRef account) {
        return account.equals(to);
    }

    /** {@code +amount} for incoming rows, {@code -amount} for outgoing ones, from {@code account}'s point of view. */
    long signedAmount(AccountRef account) {
        return incoming(account) ? amount : -amount;
    }

    /** {@code account}'s balance right after this change. */
    Long balanceAfter(AccountRef account) {
        return incoming(account) ? toBalance : fromBalance;
    }

    /** The other side of the change from {@code account}'s point of view, or {@code null} (created/removed currency). */
    AccountRef counterparty(AccountRef account) {
        return incoming(account) ? from : to;
    }

    /** The other side's name, or {@code null}. */
    String counterpartyName(AccountRef account) {
        return incoming(account) ? fromName : toName;
    }
}
