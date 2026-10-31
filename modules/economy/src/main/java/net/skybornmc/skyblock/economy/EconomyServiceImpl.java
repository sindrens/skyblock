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
 * <p><b>Profiles (PROF):</b> money, crystals and shards belong to a profile, credits (both ledger halves) to the player
 * (owner decision P2/PR2, C1-R1-01). A player account ref holds a profile key; for {@link Currency#accountWide()} currencies
 * it is mapped to the player UUID here, so callers can always pass the profile key. The display cache keeps one entry per
 * account id (player UUID for credits, the active profile key for the rest).</p>
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
    /** Phase P7 (spec 13.1): the moderation module's sanction check ({@code null} = none). */
    private volatile Supplier<net.skybornmc.skyblock.common.moderation.SanctionService> sanctions = () -> null;

    /** Sets where the sanction check comes from (the module passes the service registry lookup). */
    void sanctions(Supplier<net.skybornmc.skyblock.common.moderation.SanctionService> source) {
        this.sanctions = source == null ? () -> null : source;
    }

    /**
     * Safety net (P7): true if an active sanction forbids {@code tx} (money leaving a frozen player, or a trade-like
     * transfer touching a trade-blocked player). Refused and audited ({@code sanction_blocked}); nothing changes.
     */
    private boolean sanctioned(Transaction tx) {
        boolean blocked;
        try {
            net.skybornmc.skyblock.common.moderation.SanctionService s = sanctions.get();
            blocked = s != null && s.blocks(tx);
        } catch (RuntimeException e) {
            // SECFIX1 F-15: the check itself failed. Money leaving an account is refused (fail closed); earning and staff
            // actions go through (a frozen player may still sell, staff must be able to fix things).
            boolean refuse = refuseWhenCheckFails(tx);
            log.log(Level.SEVERE, "Economy: the sanction check failed; " + (refuse ? "refused" : "allowed") + " " + tx, e);
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("reason", tx.reason());
            details.put("currency", tx.currency().key());
            details.put("amount", tx.amount());
            details.put("refused", refuse);
            details.put("error", String.valueOf(e));
            audit.log(AuditCategory.ECONOMY, "sanction_check_failed", tx.actor(), tx.from() != null ? tx.from().owner().toString()
                    : tx.to() != null ? tx.to().owner().toString() : null, null, null, null, details);
            return refuse;
        }
        if (!blocked) {
            return false;
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("reason", tx.reason());
        details.put("currency", tx.currency().key());
        details.put("amount", tx.amount());
        details.put("from", String.valueOf(tx.from()));
        details.put("to", String.valueOf(tx.to()));
        audit.log(AuditCategory.ECONOMY, "sanction_blocked", tx.actor(), tx.from() != null ? tx.from().owner().toString()
                : tx.to() != null ? tx.to().owner().toString() : null, null, null, null, details);
        return true;
    }

    /**
     * SECFIX1 F-15: whether a transaction is refused when the sanction check throws: every transaction that takes money out
     * of an account (pay, trade, auction, notes, shop, bank), except staff actions ({@code admin_*}). Deposits only
     * (selling, rewards) go through. Pure.
     */
    static boolean refuseWhenCheckFails(Transaction tx) {
        return tx.from() != null && (tx.reason() == null || !tx.reason().startsWith("admin_"));
    }

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

    /** Credits live on the player (account); every other currency on the account as given (a profile key). */
    AccountRef normalize(AccountRef ref, Currency currency) {
        if (ref == null || !ref.isPlayer() || !currency.accountWide()) {
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

    /** The cache entry that holds {@code currency} for an online player (player UUID for credits, else the active profile). */
    private UUID cacheKey(UUID player, Currency currency) {
        return currency.accountWide() ? profiles().ownerOf(player) : profiles().profileKey(player);
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
        if (sanctioned(tx)) {
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

    /**
     * A credit spend (card C1): one database transaction that locks both credit rows, splits earned first from the locked
     * balances and debits each half ({@link EconomyStore#spendCredits}). Sanctions are checked for both halves first;
     * cache and audit are updated per leg on success.
     */
    @Override
    public CompletableFuture<TxResult> spendCredits(Transaction original, net.skybornmc.skyblock.common.economy.TxGuard guard) {
        Transaction tx = normalize(original);
        String problem = tx.problem();
        if (problem != null || tx.currency() != Currency.CREDITS || tx.from() == null || tx.to() != null) {
            log.warning("Economy: refused invalid credit spend (" + (problem == null ? "not a credits withdraw" : problem) + "): " + tx);
            return CompletableFuture.completedFuture(TxResult.of(TxResult.Status.INVALID));
        }
        Transaction earnedLeg = new Transaction(tx.from(), null, Currency.CREDITS_EARNED, tx.amount(), tx.reason(), tx.reference(),
                tx.idempotencyKey(), tx.actor(), tx.details());
        if (sanctioned(tx) || sanctioned(earnedLeg)) {
            return CompletableFuture.completedFuture(TxResult.of(TxResult.Status.INVALID));
        }
        CompletableFuture<EconomyStore.CreditOutcome> future;
        try {
            future = store.spendCredits(tx, guard == null ? null : guard::holds);
        } catch (RuntimeException e) {
            future = CompletableFuture.failedFuture(e);
        }
        return future.handle((outcome, error) -> {
            if (error != null) {
                log.log(Level.SEVERE, "Economy: credit spend failed and was rolled back: " + tx, Database.cause(error));
                return TxResult.of(TxResult.Status.ERROR);
            }
            for (EconomyStore.CreditLeg leg : outcome.legs()) {
                applyToCache(tx.from(), leg.currency(), leg.balanceAfter(), leg.ledgerId());
                audit(tx.reason(), tx.actor(), tx.from(), null, leg.currency(), leg.amount(),
                        new TxResult(TxResult.Status.SUCCESS, leg.ledgerId(), leg.balanceAfter(), null), tx.reference(), tx.details());
            }
            return outcome.result();
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
            if (sanctioned(tx)) {
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
     * On join: gives the active profile its season starting balances and loads the balances into the display cache: credits
     * from the player account, the rest from the active profile. Owner answers 121/121b (round 1): every profile gets the
     * start money (once per season and profile, by idempotency key on the profile key; profile 1's key is the player UUID,
     * so old grants still count). Credits are account-wide and keep one start balance per player.
     */
    CompletableFuture<Void> loadPlayer(UUID player, String name) {
        UUID key = profiles().profileKey(player);
        cache.track(player);
        cache.track(key);
        UUID previous = loadedKeys.put(player, key);
        if (previous != null && !previous.equals(key) && !previous.equals(player)) {
            cache.forget(previous);
        }
        return grantStart(player, key, name).thenCompose(v -> loadKey(player, key));
    }

    /** The start balances of profile {@code key} of {@code player} (each at most once per season; see {@link #loadPlayer}). */
    private CompletableFuture<Void> grantStart(UUID player, UUID key, String name) {
        List<CompletableFuture<TxResult>> starts = new ArrayList<>();
        EconomyConfig cfg = config.get();
        for (Currency currency : Currency.values()) {
            long amount = cfg.start(currency);
            if (amount > 0) {
                starts.add(execute(Transaction.deposit(AccountRef.player(key), currency, amount, TxReason.START_BALANCE)
                        .actor(Actor.system("economy"))
                        .idempotencyKey(startKey(season.getAsInt(), currency, startOwner(currency, player, key)))
                        .detail("target_name", name == null ? "" : name)));
            }
        }
        return CompletableFuture.allOf(starts.toArray(CompletableFuture[]::new));
    }

    /** Whose start balance a currency is: credits per player (account-wide), everything else per profile. Pure. */
    static UUID startOwner(Currency currency, UUID player, UUID profileKey) {
        return currency.accountWide() ? player : profileKey;
    }

    /** Loads {@code key}'s balances (and the player's credits) into the cache. */
    private CompletableFuture<Void> loadKey(UUID player, UUID key) {
        CompletableFuture<Void> own = store.balances(AccountRef.player(player)).thenAccept(b -> cache.load(player, b));
        if (key.equals(player)) {
            return own;
        }
        return own.thenCompose(v -> store.balances(AccountRef.player(key))).thenAccept(b -> cache.load(key, b));
    }

    /**
     * The player switched profile: drop the old profile's cached balances, give a new profile its start money (owner
     * answer 121) and load the new one's balances.
     */
    CompletableFuture<Void> switchedProfile(UUID player, UUID newKey) {
        cache.track(player);
        cache.track(newKey);
        UUID previous = loadedKeys.put(player, newKey);
        if (previous != null && !previous.equals(newKey) && !previous.equals(player)) {
            cache.forget(previous);
        }
        return grantStart(player, newKey, names.apply(player)).thenCompose(v -> loadKey(player, newKey));
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

    /** A profile's balances: money, crystals and shards of the profile, both credit halves of its player. */
    @Override
    public CompletableFuture<Map<Currency, Long>> balances(AccountRef account) {
        AccountRef owner = normalize(account, Currency.CREDITS);
        if (owner == account) {
            return store.balances(account);
        }
        return store.balances(account).thenCombine(store.balances(owner), (all, player) -> {
            Map<Currency, Long> out = new EnumMap<>(all);
            for (Currency c : Currency.values()) {
                if (c.accountWide()) {
                    out.put(c, player.getOrDefault(c, 0L));
                }
            }
            return out;
        });
    }

    @Override
    public CompletableFuture<Map<Currency, Long>> creditLegs(String spendKey) {
        return store.creditLegs(spendKey);
    }

    /** For an online player: credits from the player account, everything else from the active profile. */
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

    /** Leaderboard holograms (npcs module): the /baltop query (best live profile per player, P7). */
    @Override
    public CompletableFuture<List<net.skybornmc.skyblock.common.leaderboard.TopLine>> top(Currency currency, int limit) {
        if (currency == null || limit <= 0 || !config.get().baltopPublic(currency)) {
            return CompletableFuture.completedFuture(List.of()); // crystals are never on a public list (C1-R1-28)
        }
        return store.topBest(currency, limit, profiles.get()).thenApply(rows -> rows.stream()
                .map(r -> new net.skybornmc.skyblock.common.leaderboard.TopLine(r.player(), r.name(), r.balance()))
                .toList());
    }
}
