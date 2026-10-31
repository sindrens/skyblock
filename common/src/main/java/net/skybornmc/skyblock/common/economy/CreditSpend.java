package net.skybornmc.skyblock.common.economy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Spending credits (card C1, C1-R1-34 / C1-R2-02): <b>earned credits first, bought credits last</b> (bought ones stay
 * tradeable longer). One spend is <b>one</b> database transaction ({@link EconomyService#executeBatch}, with the caller's
 * {@link TxGuard}) with one ledger row per ledger half that pays (at most two).
 *
 * <p><b>Races.</b> The split is planned from a fresh database read; the batch then debits each half with
 * {@code balance >= amount} in SQL, so no half can ever go below 0 and nothing is spent twice. If another spend of the same
 * player got there first, the batch fails with INSUFFICIENT_FUNDS, rolls back as a whole (guard included) and the split is
 * planned again from the new balances, up to {@link #attempts()} times. A credit that arrives between the read and the
 * batch only means the split took a little more from one half than "earned first" strictly needs; never more than the
 * price, never below 0.</p>
 *
 * <p><b>Replays.</b> The first leg always carries the caller's idempotency key unchanged and the second {@code key:2}.
 * Whatever the balances are by then, a replay hits the first key and is refused as DUPLICATE: one purchase is never charged
 * twice. Keys must therefore be at most 94 characters.</p>
 *
 * <p><b>Refunds</b> ({@link #refund}) give back exactly the halves the ledger shows for the spend's key; when that can't be
 * proven, everything comes back as earned credits, so a refund can never create tradeable credits.</p>
 */
public final class CreditSpend {

    /** Suffix of the second leg's idempotency key. */
    public static final String SECOND_LEG = ":2";

    /** How often a spend is planned again after losing a race; set by the economy module ({@code credits.spend-attempts}). */
    private static volatile int attempts = 4;

    private CreditSpend() {
    }

    /** Sets how often a spend is planned again after losing a race (at least 1). */
    public static void attempts(int value) {
        attempts = Math.max(1, value);
    }

    public static int attempts() {
        return attempts;
    }

    /** How a price is split over the two ledger halves. */
    public record Split(long earned, long bought) {

        public long total() {
            return earned + bought;
        }
    }

    /**
     * Earned first, then bought; {@code null} when {@code price} is not positive, a balance is negative or the two halves
     * together hold less than the price. Pure.
     */
    public static Split plan(long earnedBalance, long boughtBalance, long price) {
        if (price <= 0 || earnedBalance < 0 || boughtBalance < 0) {
            return null;
        }
        long earned = Math.min(price, earnedBalance);
        long bought = price - earned;
        if (bought > boughtBalance) {
            return null;
        }
        return new Split(earned, bought);
    }

    /**
     * The ledger legs of a split: {@link Currency#CREDITS_EARNED} first (when above 0), then {@link Currency#CREDITS}.
     * The first leg keeps the template's idempotency key, the second gets {@code key:2}. Pure.
     */
    public static List<Transaction> legs(Transaction template, Split split) {
        return legs(template, split.earned(), split.bought());
    }

    private static List<Transaction> legs(Transaction template, long earned, long bought) {
        List<Transaction> legs = new ArrayList<>(2);
        String key = template.idempotencyKey();
        if (earned > 0) {
            legs.add(leg(template, Currency.CREDITS_EARNED, earned, key));
        }
        if (bought > 0) {
            legs.add(leg(template, Currency.CREDITS, bought, key == null || legs.isEmpty() ? key : key + SECOND_LEG));
        }
        return legs;
    }

    private static Transaction leg(Transaction t, Currency currency, long amount, String key) {
        return new Transaction(t.from(), t.to(), currency, amount, t.reason(), t.reference(), key, t.actor(), t.details());
    }

    /** True when the template's key leaves room for the second leg's suffix (keys are at most 96 characters). */
    static boolean keyFits(Transaction template) {
        String key = template.idempotencyKey();
        return key == null || key.length() + SECOND_LEG.length() <= 96;
    }

    /**
     * Runs a payment. A withdraw in {@link Currency#CREDITS} is a <b>credit spend</b>: split earned-first into up to two
     * legs in one database transaction together with {@code guard}; the result's {@code fromBalance} is the player's credit
     * total after the spend and {@code ledgerId} the first leg's row. Every other transaction (any other currency, or not a
     * plain withdraw) runs unchanged, so a call site can use this for every price currency. INSUFFICIENT_FUNDS means both
     * halves together hold less than the price.
     *
     * @param guard extra SQL committed or rolled back with the payment (may run more than once), or null
     */
    public static CompletableFuture<TxResult> execute(EconomyService economy, Transaction template, TxGuard guard) {
        if (template.currency() != Currency.CREDITS || template.from() == null || template.to() != null) {
            return guard == null ? economy.execute(template) : economy.executeGuarded(template, guard);
        }
        if (template.amount() <= 0 || !keyFits(template)) {
            return CompletableFuture.completedFuture(TxResult.of(TxResult.Status.INVALID));
        }
        return attempt(economy, template, guard, 1);
    }

    private static CompletableFuture<TxResult> attempt(EconomyService economy, Transaction template, TxGuard guard, int attempt) {
        return economy.balances(template.from()).thenCompose(balances -> {
            CreditView have = CreditView.of(balances);
            Split split = plan(have.earned(), have.bought(), template.amount());
            if (split == null) {
                return CompletableFuture.completedFuture(TxResult.of(TxResult.Status.INSUFFICIENT_FUNDS));
            }
            return economy.executeBatch(legs(template, split), guard).thenCompose(batch -> {
                if (batch.ok()) {
                    return CompletableFuture.completedFuture(merged(batch.results(), split, have));
                }
                if (batch.status() == TxResult.Status.INSUFFICIENT_FUNDS && attempt < attempts) {
                    return attempt(economy, template, guard, attempt + 1); // lost a race: plan again from fresh balances
                }
                return CompletableFuture.completedFuture(TxResult.of(batch.status()));
            });
        }).exceptionally(error -> TxResult.of(TxResult.Status.ERROR));
    }

    /** One result for the whole spend: the first leg's ledger row and the credit total left. Pure. */
    static TxResult merged(List<TxResult> results, Split split, CreditView before) {
        long earnedLeft = before.earned() - split.earned();
        long boughtLeft = before.bought() - split.bought();
        int i = 0;
        if (split.earned() > 0 && i < results.size()) {
            Long after = results.get(i++).fromBalance();
            earnedLeft = after == null ? earnedLeft : after;
        }
        if (split.bought() > 0 && i < results.size()) {
            Long after = results.get(i).fromBalance();
            boughtLeft = after == null ? boughtLeft : after;
        }
        long ledger = results.isEmpty() ? 0 : results.getFirst().ledgerId();
        return new TxResult(TxResult.Status.SUCCESS, ledger, new CreditView(boughtLeft, earnedLeft).total(), null);
    }

    /**
     * Gives a payment back. A deposit in {@link Currency#CREDITS} is a <b>credit refund</b>: the halves the ledger shows for
     * {@code spendKey} (the original spend's idempotency key) come back as they were taken, in one database transaction
     * keyed by the template's key (and {@code key:2}); if the ledger does not show exactly the refunded amount, all of it
     * comes back as earned credits (never as bought ones). Every other transaction runs unchanged. The result's
     * {@code toBalance} is the last leg's balance (display only).
     */
    public static CompletableFuture<TxResult> refund(EconomyService economy, Transaction template, String spendKey) {
        if (template.currency() != Currency.CREDITS || template.to() == null || template.from() != null) {
            return economy.execute(template);
        }
        if (template.amount() <= 0 || !keyFits(template)) {
            return CompletableFuture.completedFuture(TxResult.of(TxResult.Status.INVALID));
        }
        CompletableFuture<Map<Currency, Long>> taken = spendKey == null
                ? CompletableFuture.completedFuture(Map.of()) : economy.creditLegs(spendKey);
        return taken.thenCompose(legsTaken -> economy.executeBatch(refundLegs(template, legsTaken), null))
                .thenApply(batch -> batch.ok()
                        ? new TxResult(TxResult.Status.SUCCESS, batch.results().getFirst().ledgerId(), null,
                                batch.results().getLast().toBalance())
                        : TxResult.of(batch.status()))
                .exceptionally(error -> TxResult.of(TxResult.Status.ERROR));
    }

    /**
     * The deposits of a credit refund: the original halves (earned first) when they add up to the template's amount, else
     * everything as earned credits. Pure.
     */
    public static List<Transaction> refundLegs(Transaction template, Map<Currency, Long> taken) {
        long amount = template.amount();
        long earned = taken == null ? 0 : taken.getOrDefault(Currency.CREDITS_EARNED, 0L);
        long bought = taken == null ? 0 : taken.getOrDefault(Currency.CREDITS, 0L);
        if (earned < 0 || bought < 0 || earned + bought != amount) {
            earned = amount;
            bought = 0;
        }
        return legs(template, earned, bought);
    }
}
