package net.skybornmc.skyblock.common.economy;

import net.skybornmc.skyblock.common.audit.Actor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * One balance change for {@link EconomyService#execute}. {@code from == null} creates money (rewards, selling, admin
 * give); {@code to == null} removes it (shop purchases, fees, admin take); both set moves it between accounts.
 *
 * <pre>{@code
 * economy.execute(Transaction.transfer(AccountRef.player(buyer), AccountRef.player(seller), Currency.MONEY, price, TxReason.AH_BUY)
 *         .actor(Actor.player(buyer)).reference("ah:" + listingId).idempotencyKey("ah_buy:" + listingId));
 * }</pre>
 *
 * @param reference      what the change is about (auction id, trade id, note id …), shown in history, nullable
 * @param idempotencyKey unique key of this exact change; a second transaction with the same key is refused with
 *                       {@link TxResult.Status#DUPLICATE} (anti-dupe for retried/duplicated requests), nullable
 * @param details        extra audit details (item summary, multiplier …); {@code target_name} sets the audit target name
 */
public record Transaction(AccountRef from, AccountRef to, Currency currency, long amount, String reason, String reference,
                          String idempotencyKey, Actor actor, Map<String, Object> details) {

    public Transaction {
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(reason, "reason");
        actor = actor == null ? Actor.SYSTEM : actor;
        details = details == null ? Map.of() : Map.copyOf(details);
    }

    /** Moves {@code amount} from one account to another (fails with INSUFFICIENT_FUNDS if {@code from} has less). */
    public static Transaction transfer(AccountRef from, AccountRef to, Currency currency, long amount, String reason) {
        return new Transaction(Objects.requireNonNull(from, "from"), Objects.requireNonNull(to, "to"), currency, amount, reason,
                null, null, null, null);
    }

    /** Adds newly created currency to an account. */
    public static Transaction deposit(AccountRef to, Currency currency, long amount, String reason) {
        return new Transaction(null, Objects.requireNonNull(to, "to"), currency, amount, reason, null, null, null, null);
    }

    /** Removes currency from an account (fails with INSUFFICIENT_FUNDS if it has less). */
    public static Transaction withdraw(AccountRef from, Currency currency, long amount, String reason) {
        return new Transaction(Objects.requireNonNull(from, "from"), null, currency, amount, reason, null, null, null, null);
    }

    public Transaction actor(Actor actor) {
        return new Transaction(from, to, currency, amount, reason, reference, idempotencyKey, actor, details);
    }

    public Transaction reference(String reference) {
        return new Transaction(from, to, currency, amount, reason, reference, idempotencyKey, actor, details);
    }

    public Transaction idempotencyKey(String key) {
        return new Transaction(from, to, currency, amount, reason, reference, key, actor, details);
    }

    public Transaction detail(String key, Object value) {
        Map<String, Object> copy = new LinkedHashMap<>(details);
        if (value == null) {
            copy.remove(key);
        } else {
            copy.put(key, value);
        }
        return new Transaction(from, to, currency, amount, reason, reference, idempotencyKey, actor, copy);
    }

    /** Problems that make this transaction invalid before it reaches the database, or {@code null} if it is fine. */
    public String problem() {
        if (amount <= 0) {
            return "amount must be above 0";
        }
        if (from == null && to == null) {
            return "a transaction needs a from or a to account";
        }
        if (from != null && from.equals(to)) {
            return "from and to are the same account";
        }
        if (!TxReason.isValid(reason)) {
            return "reason must match [a-z0-9_]{1,32}";
        }
        if (reference != null && reference.length() > 64) {
            return "reference is longer than 64 characters";
        }
        if (idempotencyKey != null && (idempotencyKey.isBlank() || idempotencyKey.length() > 96)) {
            return "idempotency key must be 1-96 characters";
        }
        return null;
    }
}
