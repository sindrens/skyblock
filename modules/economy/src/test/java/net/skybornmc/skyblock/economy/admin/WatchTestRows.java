package net.skybornmc.skyblock.economy.admin;

import net.skybornmc.skyblock.common.economy.AccountRef;
import net.skybornmc.skyblock.common.economy.Currency;

import java.util.UUID;

/** Builders for ledger rows in the economy watch tests. */
final class WatchTestRows {

    static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    static final UUID C = UUID.fromString("00000000-0000-0000-0000-00000000000c");
    static final long T0 = 1_000L * Buckets.HOUR_MS; // start of hour 1000

    private WatchTestRows() {
    }

    static LedgerRow created(long id, long time, UUID to, long amount, String reason) {
        return new LedgerRow(id, time, Currency.MONEY, null, AccountRef.player(to), amount, null, null, reason);
    }

    static LedgerRow removed(long id, long time, UUID from, long amount, String reason) {
        return new LedgerRow(id, time, Currency.MONEY, AccountRef.player(from), null, amount, null, null, reason);
    }

    static LedgerRow moved(long id, long time, UUID from, UUID to, long amount, String reason) {
        return new LedgerRow(id, time, Currency.MONEY, AccountRef.player(from), AccountRef.player(to), amount, null, null, reason);
    }

    static LedgerRow withBalances(LedgerRow r, Long fromBalance, Long toBalance) {
        return new LedgerRow(r.id(), r.time(), r.currency(), r.from(), r.to(), r.amount(), fromBalance, toBalance, r.reason());
    }
}
