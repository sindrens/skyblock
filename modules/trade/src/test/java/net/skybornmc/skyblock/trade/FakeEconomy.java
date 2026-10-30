package net.skybornmc.skyblock.trade;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.economy.AccountRef;
import net.skybornmc.skyblock.common.economy.BatchResult;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.economy.EconomyService;
import net.skybornmc.skyblock.common.economy.Transaction;
import net.skybornmc.skyblock.common.economy.TxGuard;
import net.skybornmc.skyblock.common.economy.TxResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * In-memory balances with the real batch contract: all legs and the guard (run in a real H2 transaction that is rolled
 * back when it answers false) commit together or not at all; idempotency keys are recorded only on success.
 */
final class FakeEconomy implements EconomyService {

    /** Thrown inside the guard transaction to roll it back. */
    private static final class Rollback extends RuntimeException {
        Rollback() {
            super(null, null, false, false);
        }
    }

    private final Database db;
    private final Map<String, Long> balances = new HashMap<>();
    private final Set<String> keys = new HashSet<>();
    final List<Transaction> done = new ArrayList<>();
    final Set<Currency> tradeable = new HashSet<>(Set.of(Currency.MONEY, Currency.SHARDS));

    FakeEconomy(Database db) {
        this.db = db;
    }

    private static String k(AccountRef a, Currency c) {
        return a + "/" + c.key();
    }

    synchronized void set(UUID profile, Currency currency, long amount) {
        balances.put(k(AccountRef.player(profile), currency), amount);
    }

    synchronized long get(UUID profile, Currency currency) {
        return balances.getOrDefault(k(AccountRef.player(profile), currency), 0L);
    }

    @Override
    public CompletableFuture<TxResult> execute(Transaction tx) {
        return executeBatch(List.of(tx), null).thenApply(r -> r.ok() ? r.results().getFirst() : TxResult.of(r.status()));
    }

    @Override
    public CompletableFuture<BatchResult> executeBatch(List<Transaction> txs, TxGuard guard) {
        return CompletableFuture.supplyAsync(() -> run(txs, guard));
    }

    private synchronized BatchResult run(List<Transaction> txs, TxGuard guard) {
        Map<String, Long> copy = new HashMap<>(balances);
        List<TxResult> results = new ArrayList<>();
        Set<String> used = new HashSet<>();
        for (int i = 0; i < txs.size(); i++) {
            Transaction tx = txs.get(i);
            if (tx.problem() != null) {
                return BatchResult.failed(TxResult.Status.INVALID, i);
            }
            if (tx.idempotencyKey() != null && (keys.contains(tx.idempotencyKey()) || !used.add(tx.idempotencyKey()))) {
                return BatchResult.failed(TxResult.Status.DUPLICATE, i);
            }
            Long fromBal = null;
            Long toBal = null;
            if (tx.from() != null) {
                long have = copy.getOrDefault(k(tx.from(), tx.currency()), 0L);
                if (have < tx.amount()) {
                    return BatchResult.failed(TxResult.Status.INSUFFICIENT_FUNDS, i);
                }
                fromBal = have - tx.amount();
                copy.put(k(tx.from(), tx.currency()), fromBal);
            }
            if (tx.to() != null) {
                toBal = copy.getOrDefault(k(tx.to(), tx.currency()), 0L) + tx.amount();
                copy.put(k(tx.to(), tx.currency()), toBal);
            }
            results.add(new TxResult(TxResult.Status.SUCCESS, i + 1, fromBal, toBal));
        }
        if (guard != null) {
            try {
                db.blockingTransaction(c -> {
                    if (!guard.holds(c)) {
                        throw new Rollback();
                    }
                    return null;
                });
            } catch (Rollback rollback) {
                return BatchResult.failed(TxResult.Status.DUPLICATE, -1);
            } catch (RuntimeException e) {
                if (e.getCause() instanceof Rollback) {
                    return BatchResult.failed(TxResult.Status.DUPLICATE, -1);
                }
                return BatchResult.failed(TxResult.Status.ERROR, -1);
            }
        }
        balances.clear();
        balances.putAll(copy);
        keys.addAll(used);
        done.addAll(txs);
        return BatchResult.success(results);
    }

    @Override
    public CompletableFuture<Long> balance(AccountRef account, Currency currency) {
        synchronized (this) {
            return CompletableFuture.completedFuture(balances.getOrDefault(k(account, currency), 0L));
        }
    }

    @Override
    public CompletableFuture<Map<Currency, Long>> balances(AccountRef account) {
        Map<Currency, Long> out = new HashMap<>();
        synchronized (this) {
            for (Currency c : Currency.values()) {
                out.put(c, balances.getOrDefault(k(account, c), 0L));
            }
        }
        return CompletableFuture.completedFuture(out);
    }

    @Override
    public OptionalLong cached(UUID player, Currency currency) {
        return OptionalLong.of(get(player, currency));
    }

    @Override
    public boolean tradeable(Currency currency) {
        return tradeable.contains(currency);
    }

    @Override
    public String format(Currency currency, long amount) {
        return currency.key() + ":" + amount;
    }
}
