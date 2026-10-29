package net.skybornmc.skyblock.auction;

import net.skybornmc.skyblock.common.economy.AccountRef;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.economy.EconomyService;
import net.skybornmc.skyblock.common.economy.Transaction;
import net.skybornmc.skyblock.common.economy.TxResult;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * In-memory EconomyService with the same contract as the real one: atomic, idempotency keys only recorded on success,
 * INSUFFICIENT_FUNDS changes nothing. {@link #failNext} makes the next transactions with a reason return ERROR (nothing
 * changed), {@link #failAfterCommit} applies the change but reports ERROR (the "unknown outcome" case).
 */
final class FakeEconomy implements EconomyService {

    private static final java.util.concurrent.Executor EXECUTOR = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();

    private final Map<AccountRef, Long> money = new HashMap<>();
    private final Set<String> keys = new HashSet<>();
    private final Map<String, Integer> failNext = new HashMap<>();
    private final Map<String, Integer> failAfterCommit = new HashMap<>();
    final List<Transaction> done = new ArrayList<>();

    synchronized void set(java.util.UUID player, long amount) {
        money.put(AccountRef.player(player), amount);
    }

    synchronized long get(java.util.UUID player) {
        return money.getOrDefault(AccountRef.player(player), 0L);
    }

    synchronized void failNext(String reason, int times) {
        failNext.put(reason, times);
    }

    synchronized void failAfterCommit(String reason, int times) {
        failAfterCommit.put(reason, times);
    }

    synchronized long count(String reason) {
        return done.stream().filter(t -> t.reason().equals(reason)).count();
    }

    @Override
    public CompletableFuture<TxResult> execute(Transaction tx) {
        return CompletableFuture.supplyAsync(() -> run(tx), EXECUTOR);
    }

    private synchronized TxResult run(Transaction tx) {
        if (tx.problem() != null) {
            return TxResult.of(TxResult.Status.INVALID);
        }
        if (failNext.getOrDefault(tx.reason(), 0) > 0) {
            failNext.merge(tx.reason(), -1, Integer::sum);
            return TxResult.of(TxResult.Status.ERROR);
        }
        if (tx.idempotencyKey() != null && keys.contains(tx.idempotencyKey())) {
            return TxResult.of(TxResult.Status.DUPLICATE);
        }
        Long from = null;
        Long to = null;
        if (tx.from() != null) {
            long have = money.getOrDefault(tx.from(), 0L);
            if (have < tx.amount()) {
                return TxResult.of(TxResult.Status.INSUFFICIENT_FUNDS);
            }
            from = have - tx.amount();
            money.put(tx.from(), from);
        }
        if (tx.to() != null) {
            to = money.getOrDefault(tx.to(), 0L) + tx.amount();
            money.put(tx.to(), to);
        }
        if (tx.idempotencyKey() != null) {
            keys.add(tx.idempotencyKey());
        }
        done.add(tx);
        if (failAfterCommit.getOrDefault(tx.reason(), 0) > 0) {
            failAfterCommit.merge(tx.reason(), -1, Integer::sum);
            return TxResult.of(TxResult.Status.ERROR);
        }
        return new TxResult(TxResult.Status.SUCCESS, done.size(), from, to);
    }

    @Override
    public synchronized CompletableFuture<Long> balance(AccountRef account, Currency currency) {
        return CompletableFuture.completedFuture(money.getOrDefault(account, 0L));
    }

    @Override
    public synchronized CompletableFuture<Map<Currency, Long>> balances(AccountRef account) {
        Map<Currency, Long> m = new EnumMap<>(Currency.class);
        m.put(Currency.MONEY, money.getOrDefault(account, 0L));
        return CompletableFuture.completedFuture(m);
    }

    @Override
    public OptionalLong cached(java.util.UUID player, Currency currency) {
        return OptionalLong.of(get(player));
    }

    @Override
    public boolean tradeable(Currency currency) {
        return currency == Currency.MONEY;
    }

    @Override
    public String format(Currency currency, long amount) {
        return "$" + amount;
    }
}
