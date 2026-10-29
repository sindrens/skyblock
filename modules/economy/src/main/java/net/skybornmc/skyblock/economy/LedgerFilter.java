package net.skybornmc.skyblock.economy;

import net.skybornmc.skyblock.common.economy.Currency;

import java.time.Duration;

/**
 * {@code /transactions} filters; {@code null} = any.
 *
 * @param reason reason or reason group ({@code ah} matches {@code ah_buy}, {@code ah_sale} …)
 * @param since  only rows newer than this
 */
record LedgerFilter(Currency currency, String reason, Duration since) {

    static final LedgerFilter ALL = new LedgerFilter(null, null, null);

    LedgerFilter withCurrency(Currency currency) {
        return new LedgerFilter(currency, reason, since);
    }

    LedgerFilter withReason(String reason) {
        return new LedgerFilter(currency, reason, since);
    }

    LedgerFilter withSince(Duration since) {
        return new LedgerFilter(currency, reason, since);
    }
}
