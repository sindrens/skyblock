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
import net.skybornmc.skyblock.common.profile.ProfileService;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link EconomyService} on top of {@link EconomyStore}: keeps the display cache consistent with database results and
 * writes an audit row (category {@code economy}, action = reason) for every change.
 *
 * <p><b>Profiles (PROF):</b> money, crystals and shards belong to a profile, gems to the player (owner decision P2/PR2).
 * A player account ref holds a profile key; for {@link Currency#GEMS} it is mapped to the player UUID here, so callers
 * can always pass the profile key. The display cache keeps one entry per account id (player UUID for gems, the active
 * profile key for the rest).</p>
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
    private final Supplier<ProfileService> profiles;
    /** Online player → the profile key whose balances are cached for them. */
    private final Map<UUID, UUID> loadedKeys = new ConcurrentHashMap<>();

    EconomyServiceImpl(EconomyStore store, BalanceCache cache, AuditLog audit, Supplier<EconomyConfig> config,
                       Function<UUID, String> names, Function<Currency, String> symbols, IntSupplier season, Logger log) {
        this(store, cache, audit, config, names, symbols, season, log, () -> ProfileService.IDENTITY);
    }

    EconomyServiceImpl(EconomyStore store, BalanceCache cache, AuditLog audit, Supplier<EconomyConfig> config,
                       Function<UUID, String> names, Function<Currency, String> symbols, IntSupplier season, Logger log,
                       Supplier<ProfileService> profiles) {
        this.profiles = profiles;
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

    ProfileService profiles() {
        return profiles.get();
    }

    // ------------------------------------------------------------------------------------------------ profiles

    /** Gems live on the player (account); every other currency on the account as given (a profile key). */
    AccountRef normalize(AccountRef ref, Currency currency) {
        if (ref == null || !ref.isPlayer() || currency != Currency.GEMS) {
            return ref;
        }
        UUID owner = profiles().ownerOf(ref.owner());
        return owner == null || owner.equals(ref.owner()) ? ref : AccountRef.player(owner);
    }

    private Transaction normalize(Transaction tx) {
        AccountRef from = normalize(tx.from(), tx.currency());
        AccountRef to = normalize(tx.to(), tx.currency());
        if (from == tx.from() && to == tx.to()) {
            return tx;
        }
        return new Transaction(from, to, tx.currency(), tx.amount(), tx.reason(), tx.reference(), tx.idempotencyKey(), tx.actor(),
                tx.details());
    }

    /** The cache entry that holds {@code currency} for an online player (player UUID for gems, else the active profile). */
    private UUID cacheKey(UUID player, Currency currency) {
        return currency == Currency.GEMS ? profiles().ownerOf(player) : profiles().profileKey(player);
    }

    // ------------------------------------------------------------------------------------------------ transactions

    @Override
    public CompletableFuture<TxResult> execute(Transaction tx) {
        return execute(tx, null);
    }

    @Override
    public CompletableFuture<TxResult> executeGuarded(Transaction tx, net.skybornmc.skyblock.common.economy.TxGuard guard) {
        return guard == null ? execute(tx, null) : execute(tx, guard::holds);
    }

    /** {@link #execute(Transaction)} with extra SQL that commits or rolls back together with it (money notes). */
    CompletableFuture<TxResult> execute(Transaction original, EconomyStore.Guard guard) {
        Transaction tx = normalize(original);
        String problem = tx.problem();
        if (problem != null) {
            log.warning("Economy: refused invalid transaction (" + problem + "): " + tx);
            return CompletableFuture.completedFuture(TxResult.of(TxResult.Status.INVALID));
        }
        CompletableFuture<TxResult> future;
        try {
            future = store.execute(tx, guard);
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

    /** All transactions (and the guard) in one database transaction; cache and audit are updated per leg on success. */
    @Override
    public CompletableFuture<net.skybornmc.skyblock.common.economy.BatchResult> executeBatch(List<Transaction> transactions,
                                                                                             net.skybornmc.skyblock.common.economy.TxGuard guard) {
        List<Transaction> txs = new ArrayList<>(transactions.size());
        for (int i = 0; i < transactions.size(); i++) {
            Transaction tx = normalize(transactions.get(i));
            String problem = tx.problem();
            if (problem != null) {
                log.warning("Economy: refused invalid batch transaction (" + problem + "): " + tx);
                return CompletableFuture.completedFuture(net.skybornmc.skyblock.common.economy.BatchResult.failed(TxResult.Status.INVALID, i));
            }
            txs.add(tx);
        }
        CompletableFuture<net.skybornmc.skyblock.common.economy.BatchResult> future;
        try {
            future = store.executeBatch(txs, guard == null ? null : guard::holds);
        } catch (RuntimeException e) {
            future = CompletableFuture.failedFuture(e);
        }
        return future.handle((result, error) -> {
            if (error != null) {
                log.log(Level.SEVERE, "Economy: batch of " + txs.size() + " failed and was rolled back: " + txs, Database.cause(error));
                return net.skybornmc.skyblock.common.economy.BatchResult.failed(TxResult.Status.ERROR, -1);
            }
            if (result.ok()) {
                for (int i = 0; i < txs.size(); i++) {
                    Transaction tx = txs.get(i);
                    TxResult r = result.results().get(i);
                    applyToCache(tx.from(), tx.currency(), r.fromBalance(), r.ledgerId());
                    applyToCache(tx.to(), tx.currency(), r.toBalance(), r.ledgerId());
                    audit(tx.reason(), tx.actor(), tx.from(), tx.to(), tx.currency(), tx.amount(), r, tx.reference(), tx.details());
                }
            }
            return result;
        });
    }

    /**
     * Staff {@code /eco set} and {@code /eco reset}: sets an exact balance (the ledger and audit get the difference).
     */
    CompletableFuture<TxResult> set(AccountRef given, Currency currency, long value, String reason, Actor actor,
                                    Map<String, Object> details) {
        AccountRef account = normalize(given, currency);
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
        // Personal banks (M3.3) belong to a profile too: logged on the player, so /logs <player> finds bank moves.
        UUID targetPlayer = target != null && (target.isPlayer() || target.isBank()) ? profiles().ownerOf(target.owner()) : null;
        if (targetPlayer != null && (!targetPlayer.equals(target.owner()) || target.isBank())) {
            d.putIfAbsent("profile", target.owner().toString());
        }
        String targetName = givenName != null ? String.valueOf(givenName)
                : targetPlayer != null ? names.apply(targetPlayer) : null;
        // The audit target is the player (account), so /logs <player> finds changes on every profile.
        String targetKey = target == null ? null : targetPlayer != null ? targetPlayer.toString() : target.toString();
        audit.log(AuditCategory.ECONOMY, reason, actor, targetKey, targetName, amount, null, d);
    }

    // ------------------------------------------------------------------------------------------------ players

    /**
     * On join: gives the season's starting balances (exactly once per season and player, by idempotency key on the
     * player UUID, so a new profile never gets a second start balance) and loads the balances into the display cache:
     * gems from the player account, the rest from the active profile.
     */
    CompletableFuture<Void> loadPlayer(UUID player, String name) {
        UUID key = profiles().profileKey(player);
        cache.track(player);
        cache.track(key);
        UUID previous = loadedKeys.put(player, key);
        if (previous != null && !previous.equals(key) && !previous.equals(player)) {
            cache.forget(previous);
        }
        List<CompletableFuture<TxResult>> starts = new ArrayList<>();
        EconomyConfig cfg = config.get();
        for (Currency currency : Currency.values()) {
            long amount = cfg.start(currency);
            if (amount > 0) {
                starts.add(execute(Transaction.deposit(AccountRef.player(key), currency, amount, TxReason.START_BALANCE)
                        .actor(Actor.system("economy"))
                        .idempotencyKey(startKey(season.getAsInt(), currency, player))
                        .detail("target_name", name)));
            }
        }
        return CompletableFuture.allOf(starts.toArray(CompletableFuture[]::new))
                .thenCompose(v -> loadKey(player, key));
    }

    /** Loads {@code key}'s balances (and the player's gems) into the cache. */
    private CompletableFuture<Void> loadKey(UUID player, UUID key) {
        CompletableFuture<Void> own = store.balances(AccountRef.player(player)).thenAccept(b -> cache.load(player, b));
        if (key.equals(player)) {
            return own;
        }
        return own.thenCompose(v -> store.balances(AccountRef.player(key))).thenAccept(b -> cache.load(key, b));
    }

    /** The player switched profile: drop the old profile's cached balances and load the new one's. */
    CompletableFuture<Void> switchedProfile(UUID player, UUID newKey) {
        cache.track(player);
        cache.track(newKey);
        UUID previous = loadedKeys.put(player, newKey);
        if (previous != null && !previous.equals(newKey) && !previous.equals(player)) {
            cache.forget(previous);
        }
        return loadKey(player, newKey);
    }

    /** On quit: forgets the player's cache entries. */
    void forgetPlayer(UUID player) {
        UUID key = loadedKeys.remove(player);
        cache.forget(player);
        if (key != null) {
            cache.forget(key);
        }
    }

    /** Puts a fresh read of {@link #balances} for an online player into the cache (each currency in its entry). */
    void refreshCached(UUID player, Map<Currency, Long> balances) {
        balances.forEach((currency, value) -> cache.refresh(cacheKey(player, currency), currency, value));
    }

    static String startKey(int season, Currency currency, UUID player) {
        return "start:" + season + ":" + currency.key() + ":" + player;
    }

    // ------------------------------------------------------------------------------------------------ reads

    @Override
    public CompletableFuture<Long> balance(AccountRef account, Currency currency) {
        return store.balance(normalize(account, currency), currency);
    }

    /** A profile's balances: money, crystals and shards of the profile, gems of its player. */
    @Override
    public CompletableFuture<Map<Currency, Long>> balances(AccountRef account) {
        AccountRef gems = normalize(account, Currency.GEMS);
        if (gems == account) {
            return store.balances(account);
        }
        return store.balances(account).thenCombine(store.balance(gems, Currency.GEMS), (all, g) -> {
            Map<Currency, Long> out = new EnumMap<>(all);
            out.put(Currency.GEMS, g);
            return out;
        });
    }

    /** For an online player: gems from the player account, everything else from the active profile. */
    @Override
    public OptionalLong cached(UUID player, Currency currency) {
        return cache.get(cacheKey(player, currency), currency);
    }

    @Override
    public boolean tradeable(Currency currency) {
        return config.get().tradeable(currency);
    }

    @Override
    public double sellBonusCapPercent() {
        return config.get().sellMultiplierCapPercent;
    }

    @Override
    public String format(Currency currency, long amount) {
        return Amounts.withSymbol(symbols.apply(currency), currency, amount);
    }
}
