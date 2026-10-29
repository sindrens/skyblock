package net.skybornmc.skyblock.economy;

import net.skybornmc.skyblock.common.economy.Currency;

import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Display balances of online players (scoreboard, placeholders, menus). Only ever filled with values the database
 * returned: the load on join and the "balance after" of every transaction. Each value carries the ledger id that
 * produced it, so a result arriving late (transactions finish on different threads) never overwrites a newer one:
 * for one account, ledger ids grow in commit order because the row is locked until commit.
 */
final class BalanceCache {

    /** Per currency: balance and the ledger id it came from ({@code -1} = not known yet, {@code 0} = from a plain load). */
    private static final class Entry {
        final long[] balance = new long[Currency.values().length];
        final long[] version = new long[Currency.values().length];

        Entry() {
            java.util.Arrays.fill(version, -1L);
        }
    }

    private final Map<UUID, Entry> entries = new ConcurrentHashMap<>();

    /** Starts tracking a player (on join); values stay unknown until {@link #load} or a transaction result. */
    void track(UUID player) {
        entries.putIfAbsent(player, new Entry());
    }

    void forget(UUID player) {
        entries.remove(player);
    }

    boolean tracked(UUID player) {
        return entries.containsKey(player);
    }

    /** Balances read from the database; a currency already updated by a transaction result is kept. */
    void load(UUID player, Map<Currency, Long> balances) {
        Entry e = entries.get(player);
        if (e == null) {
            return;
        }
        synchronized (e) {
            balances.forEach((currency, value) -> {
                int i = currency.ordinal();
                if (e.version[i] < 0) {
                    e.balance[i] = value;
                    e.version[i] = 0;
                }
            });
        }
    }

    /** The balance after ledger row {@code ledgerId}; ignored if a newer row was applied already. */
    void apply(UUID player, Currency currency, long balance, long ledgerId) {
        Entry e = entries.get(player);
        if (e == null) {
            return;
        }
        synchronized (e) {
            int i = currency.ordinal();
            if (ledgerId > e.version[i]) {
                e.balance[i] = balance;
                e.version[i] = ledgerId;
            }
        }
    }

    /** Overwrites with an authoritative fresh read (staff reload); keeps versions so later results still apply. */
    void refresh(UUID player, Currency currency, long balance) {
        Entry e = entries.get(player);
        if (e == null) {
            return;
        }
        synchronized (e) {
            int i = currency.ordinal();
            e.balance[i] = balance;
            e.version[i] = Math.max(0, e.version[i]);
        }
    }

    OptionalLong get(UUID player, Currency currency) {
        Entry e = entries.get(player);
        if (e == null) {
            return OptionalLong.empty();
        }
        synchronized (e) {
            int i = currency.ordinal();
            return e.version[i] < 0 ? OptionalLong.empty() : OptionalLong.of(e.balance[i]);
        }
    }
}
