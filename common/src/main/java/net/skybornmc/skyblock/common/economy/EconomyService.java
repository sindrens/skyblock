package net.skybornmc.skyblock.common.economy;

import net.skybornmc.skyblock.common.audit.Actor;

import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The economy (M3.1) for every module: balances in four currencies for players and islands, changed only through
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

    /** Player to player payment helper (reason {@code pay} by default for {@code /pay}). */
    default CompletableFuture<TxResult> transfer(UUID from, UUID to, Currency currency, long amount, String reason, Actor actor) {
        return execute(Transaction.transfer(AccountRef.player(from), AccountRef.player(to), currency, amount, reason).actor(actor));
    }

    /** Gives a player newly created currency (rewards, selling …). */
    default CompletableFuture<TxResult> deposit(UUID player, Currency currency, long amount, String reason, Actor actor) {
        return execute(Transaction.deposit(AccountRef.player(player), currency, amount, reason).actor(actor));
    }

    /** Takes currency from a player (purchases, fees …); INSUFFICIENT_FUNDS if they have less. */
    default CompletableFuture<TxResult> withdraw(UUID player, Currency currency, long amount, String reason, Actor actor) {
        return execute(Transaction.withdraw(AccountRef.player(player), currency, amount, reason).actor(actor));
    }

    /** The balance in the database (authoritative). Accounts that never had a change hold 0. */
    CompletableFuture<Long> balance(AccountRef account, Currency currency);

    /** All four balances in the database. */
    CompletableFuture<Map<Currency, Long>> balances(AccountRef account);

    /**
     * The cached balance of an online player (for display: scoreboard, menus). Kept consistent with the database results
     * of every transaction. Empty while not loaded. Never use it to decide whether a purchase is allowed: just run the
     * transaction, it fails safely with INSUFFICIENT_FUNDS.
     */
    OptionalLong cached(UUID player, Currency currency);

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
}
