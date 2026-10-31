package net.skybornmc.skyblock.common.economy;

import net.skybornmc.skyblock.common.audit.Actor;

import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The economy (M3.1) for every module: balances in the {@link Currency} ledgers for players and islands, changed only through
 * <b>atomic database transactions</b> with a ledger row for every change. Published by the economy module:
 * {@code core.services().optional(EconomyService.class)}. No Vault.
 *
 * <ul>
 *   <li>Every change is one SQL transaction ({@code UPDATE … SET balance = balance - ? WHERE balance >= ?}); there is no
 *       read-modify-write in Java, so two purchases can never spend the same money.</li>
 *   <li>Every change writes a ledger row (time, season, currency, both accounts, amount, both balances after, reason,
 *       reference) and an audit row (category {@code economy}, action = reason).</li>
 *   <li>Use an idempotency key for anything that could be retried or replayed (auction purchase, note redeem, trade).</li>
 *   <li>Futures complete off the main thread; hop back with {@code thenAcceptAsync(…, mainThread)} to touch players.</li>
 *   <li><b>Profiles (PROF):</b> money, crystals and shards belong to a profile, credits (both ledger halves) to the player.
 *       A player account ({@link AccountRef#player}) holds a <b>profile key</b> ({@code ProfileService#profileKey}); credit
 *       changes are moved
 *       to the profile's player automatically, so always pass the profile key. Anything paid out later (auction sales,
 *       refunds) stores and pays the profile key it belongs to, never the player's currently active profile.</li>
 * </ul>
 */
public interface EconomyService {

    /** Runs one transaction. Never completes exceptionally: errors become {@link TxResult.Status#ERROR}. */
    CompletableFuture<TxResult> execute(Transaction transaction);

    /**
     * Runs one transaction together with extra SQL ({@code guard}) in the same database transaction: both commit or
     * both roll back (enchant books consumed + crystals paid + tool perks saved, ENCHANTS §4.2). A guard returning false
     * gives {@link TxResult.Status#DUPLICATE}. Implementations without guard support refuse with ERROR, never run the
     * transaction without its guard.
     */
    default CompletableFuture<TxResult> executeGuarded(Transaction transaction, TxGuard guard) {
        return guard == null ? execute(transaction) : CompletableFuture.completedFuture(TxResult.of(TxResult.Status.ERROR));
    }

    /**
     * Runs several transactions and an optional {@code guard} in <b>one</b> database transaction: all of them commit or
     * none does (secure trade M3.7: both sides' money in one go, together with the item escrow switch). Every
     * transaction still writes its own ledger and audit row and should carry its own idempotency key. An empty list
     * with a guard runs just the guard. Implementations without batch support refuse with ERROR, never run part of it.
     */
    default CompletableFuture<BatchResult> executeBatch(java.util.List<Transaction> transactions, TxGuard guard) {
        return CompletableFuture.completedFuture(BatchResult.failed(TxResult.Status.ERROR, -1));
    }

    /** Player to player payment helper (reason {@code pay} by default for {@code /pay}); both ids are profile keys. */
    default CompletableFuture<TxResult> transfer(UUID from, UUID to, Currency currency, long amount, String reason, Actor actor) {
        return execute(Transaction.transfer(AccountRef.player(from), AccountRef.player(to), currency, amount, reason).actor(actor));
    }

    /** Gives a player newly created currency (rewards, selling …); {@code player} is the profile key. */
    default CompletableFuture<TxResult> deposit(UUID player, Currency currency, long amount, String reason, Actor actor) {
        return execute(Transaction.deposit(AccountRef.player(player), currency, amount, reason).actor(actor));
    }

    /** Takes currency from a player (purchases, fees …); INSUFFICIENT_FUNDS if they have less. {@code player}: profile key. */
    default CompletableFuture<TxResult> withdraw(UUID player, Currency currency, long amount, String reason, Actor actor) {
        return execute(Transaction.withdraw(AccountRef.player(player), currency, amount, reason).actor(actor));
    }

    /** The balance in the database (authoritative). Accounts that never had a change hold 0. */
    CompletableFuture<Long> balance(AccountRef account, Currency currency);

    /** Every currency's balance in the database (credits of the profile's player, the rest of the profile). */
    CompletableFuture<Map<Currency, Long>> balances(AccountRef account);

    /**
     * The cached balance of an online player, by <b>player UUID</b> (credits of the player, the rest of the active profile),
     * for display: scoreboard, menus. For credits this is one ledger half; show the total with
     * {@link CreditView#cachedShown}. Kept consistent with the database results
     * of every transaction. Empty while not loaded. Never use it to decide whether a purchase is allowed: just run the
     * transaction, it fails safely with INSUFFICIENT_FUNDS.
     */
    OptionalLong cached(UUID player, Currency currency);

    /**
     * A credit spend (card C1): {@code template} is a withdraw in {@link Currency#CREDITS}; inside <b>one</b> database
     * transaction (with {@code guard}) both credit rows are locked, the earned-first split is computed from the locked,
     * current balances and each half is debited with its own ledger row ({@code key}, {@code key:2}). Result:
     * {@code fromBalance} = the credit total left. Use {@link CreditSpend#execute}. Implementations without it refuse with
     * ERROR and never charge.
     */
    default CompletableFuture<TxResult> spendCredits(Transaction template, TxGuard guard) {
        return CompletableFuture.completedFuture(TxResult.of(TxResult.Status.ERROR));
    }

    /**
     * A player's credits (both ledger halves) from the database; {@code profileOrPlayer} may be a profile key or the
     * player UUID (credits always belong to the player).
     */
    default CompletableFuture<CreditView> credits(UUID profileOrPlayer) {
        return balances(AccountRef.player(profileOrPlayer)).thenApply(CreditView::of);
    }

    /**
     * What a credit spend took, per ledger half, found by the spend's idempotency key ({@link CreditSpend}: the key and
     * {@code key:2}); empty when none was found. Used by {@link CreditSpend#refund}. Implementations without ledger
     * access return an empty map (a refund then comes back as earned credits).
     */
    default CompletableFuture<Map<Currency, Long>> creditLegs(String spendKey) {
        return CompletableFuture.completedFuture(Map.of());
    }

    /** Whether players may {@code /pay} and trade this currency ({@code economy.yml}). */
    boolean tradeable(Currency currency);

    /** Player-facing plain text of an amount, e.g. {@code $12,500}, {@code $1.2M}, {@code ✦ 1,500}. */
    String format(Currency currency, long amount);

    /**
     * The global cap on the total sell bonus in percent ({@code economy.yml → sell.multiplier-cap-percent}, owner decision
     * #46: 300). Sell code applies it with {@link MultiplierCap#apply(long, double, double)}.
     */
    default double sellBonusCapPercent() {
        return MultiplierCap.DEFAULT_CAP_PERCENT;
    }

    /**
     * The richest players in a currency, richest first, one line per player from their best live profile (owner decision
     * P7, like {@code /baltop}); for leaderboard holograms. Reads the database off the main thread; callers cache the
     * result (the npcs module refreshes every few minutes). Implementations without it return an empty list.
     */
    default CompletableFuture<java.util.List<net.skybornmc.skyblock.common.leaderboard.TopLine>> top(Currency currency, int limit) {
        return CompletableFuture.completedFuture(java.util.List.of());
    }
}
