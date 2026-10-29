package net.skybornmc.skyblock.economy;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.audit.AuditLog;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.economy.AccountRef;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.economy.EconomyService;
import net.skybornmc.skyblock.common.economy.Transaction;
import net.skybornmc.skyblock.common.economy.TxReason;
import net.skybornmc.skyblock.common.economy.TxResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link EconomyService} on top of {@link EconomyStore}: keeps the display cache consistent with database results and
 * writes an audit row (category {@code economy}, action = reason) for every change.
 */
final class EconomyServiceImpl implements EconomyService {

    private final EconomyStore store;
    private final BalanceCache cache;
    private final AuditLog audit;
    private final Supplier<EconomyConfig> config;
    private final Function<UUID, String> names;
    private final Function<Currency, String> symbols;
    private final IntSupplier season;
    private final Logger log;

    EconomyServiceImpl(EconomyStore store, BalanceCache cache, AuditLog audit, Supplier<EconomyConfig> config,
                       Function<UUID, String> names, Function<Currency, String> symbols, IntSupplier season, Logger log) {
        this.store = store;
        this.cache = cache;
        this.audit = audit;
        this.config = config;
        this.names = names;
        this.symbols = symbols;
        this.season = season;
        this.log = log;
    }

    BalanceCache cache() {
        return cache;
    }

    EconomyStore store() {
        return store;
    }

    // ------------------------------------------------------------------------------------------------ transactions

    @Override
    public CompletableFuture<TxResult> execute(Transaction tx) {
        String problem = tx.problem();
        if (problem != null) {
            log.warning("Economy: refused invalid transaction (" + problem + "): " + tx);
            return CompletableFuture.completedFuture(TxResult.of(TxResult.Status.INVALID));
        }
        CompletableFuture<TxResult> future;
        try {
            future = store.execute(tx);
        } catch (RuntimeException e) {
            future = CompletableFuture.failedFuture(e);
        }
        return future.handle((result, error) -> {
            if (error != null) {
                log.log(Level.SEVERE, "Economy: transaction failed and was rolled back: " + tx, Database.cause(error));
                return TxResult.of(TxResult.Status.ERROR);
            }
            if (result.ok()) {
                applyToCache(tx.from(), tx.currency(), result.fromBalance(), result.ledgerId());
                applyToCache(tx.to(), tx.currency(), result.toBalance(), result.ledgerId());
                audit(tx.reason(), tx.actor(), tx.from(), tx.to(), tx.currency(), tx.amount(), result, tx.reference(), tx.details());
            }
            return result;
        });
    }

    /**
     * Staff {@code /eco set} and {@code /eco reset}: sets an exact balance (the ledger and audit get the difference).
     */
    CompletableFuture<TxResult> set(AccountRef account, Currency currency, long value, String reason, Actor actor,
                                    Map<String, Object> details) {
        return store.set(account, currency, value, reason, null, actor).handle((outcome, error) -> {
            if (error != null) {
                log.log(Level.SEVERE, "Economy: setting " + account + " " + currency.key() + " to " + value + " failed",
                        Database.cause(error));
                return TxResult.of(TxResult.Status.ERROR);
            }
            TxResult result = outcome.result();
            if (result.ok() && result.ledgerId() > 0) {
                boolean up = value > outcome.previous();
                applyToCache(account, currency, value, result.ledgerId());
                Map<String, Object> extra = new LinkedHashMap<>(details);
                extra.put("old_balance", outcome.previous());
                extra.put("new_balance", value);
                audit(reason, actor, up ? null : account, up ? account : null, currency, Math.abs(value - outcome.previous()),
                        result, null, extra);
            }
            return result;
        });
    }

    private void applyToCache(AccountRef account, Currency currency, Long balance, long ledgerId) {
        if (account != null && account.isPlayer() && balance != null) {
            cache.apply(account.owner(), currency, balance, ledgerId);
        }
    }

    private void audit(String reason, Actor actor, AccountRef from, AccountRef to, Currency currency, long amount, TxResult result,
                       String reference, Map<String, Object> details) {
        AccountRef target = to != null ? to : from;
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("currency", currency.key());
        d.put("ledger_id", result.ledgerId());
        if (from != null) {
            d.put("from", from.toString());
            d.put("from_balance", result.fromBalance());
        }
        if (to != null) {
            d.put("to", to.toString());
            d.put("to_balance", result.toBalance());
        }
        if (reference != null) {
            d.put("reference", reference);
        }
        d.put("season", season.getAsInt());
        details.forEach(d::putIfAbsent);
        Object givenName = details.get("target_name");
        String targetName = givenName != null ? String.valueOf(givenName)
                : target != null && target.isPlayer() ? names.apply(target.owner()) : null;
        String targetKey = target == null ? null : target.isPlayer() ? target.owner().toString() : target.toString();
        audit.log(AuditCategory.ECONOMY, reason, actor, targetKey, targetName, amount, null, d);
    }

    // ------------------------------------------------------------------------------------------------ players

    /**
     * On join: gives the season's starting balances (exactly once per season and player, by idempotency key) and loads
     * the balances into the display cache.
     */
    CompletableFuture<Void> loadPlayer(UUID player, String name) {
        cache.track(player);
        List<CompletableFuture<TxResult>> starts = new ArrayList<>();
        EconomyConfig cfg = config.get();
        for (Currency currency : Currency.values()) {
            long amount = cfg.start(currency);
            if (amount > 0) {
                starts.add(execute(Transaction.deposit(AccountRef.player(player), currency, amount, TxReason.START_BALANCE)
                        .actor(Actor.system("economy"))
                        .idempotencyKey(startKey(season.getAsInt(), currency, player))
                        .detail("target_name", name)));
            }
        }
        return CompletableFuture.allOf(starts.toArray(CompletableFuture[]::new))
                .thenCompose(v -> store.balances(AccountRef.player(player)))
                .thenAccept(balances -> cache.load(player, balances));
    }

    static String startKey(int season, Currency currency, UUID player) {
        return "start:" + season + ":" + currency.key() + ":" + player;
    }

    // ------------------------------------------------------------------------------------------------ reads

    @Override
    public CompletableFuture<Long> balance(AccountRef account, Currency currency) {
        return store.balance(account, currency);
    }

    @Override
    public CompletableFuture<Map<Currency, Long>> balances(AccountRef account) {
        return store.balances(account);
    }

    @Override
    public OptionalLong cached(UUID player, Currency currency) {
        return cache.get(player, currency);
    }

    @Override
    public boolean tradeable(Currency currency) {
        return config.get().tradeable(currency);
    }

    @Override
    public String format(Currency currency, long amount) {
        return Amounts.withSymbol(symbols.apply(currency), currency, amount);
    }
}
