package net.skybornmc.skyblock.economy.admin;

import net.skybornmc.skyblock.common.economy.AccountRef;
import net.skybornmc.skyblock.common.economy.Currency;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Sums of one batch of ledger rows (M3.8), pure: money per hour, currency, reason and {@link Flow}, and per profile
 * (player account) and hour what came in and went out. The store adds these to the summary tables in one transaction
 * together with the new cursor. All additions saturate at {@link Long#MAX_VALUE} (a sum can never wrap negative).
 */
public final class Aggregation {

    /** One row of {@code eco_watch_flows}. */
    public record FlowKey(long hour, Currency currency, String reason, Flow flow) {
    }

    /** One row of {@code eco_watch_accounts}: a profile key (player account) per hour and currency. */
    public record AccountKey(long hour, UUID owner, Currency currency) {
    }

    /** A total and the number of transactions in it. */
    public record Sum(long amount, long count) {
        public static final Sum ZERO = new Sum(0, 0);

        public Sum plus(long value) {
            return new Sum(add(amount, value), add(count, 1));
        }

        public Sum plus(Sum other) {
            return new Sum(add(amount, other.amount), add(count, other.count));
        }
    }

    /** What a profile earned and spent in an hour, and how many transactions touched it. */
    public record AccountSum(long earned, long spent, long count) {
        public static final AccountSum ZERO = new AccountSum(0, 0, 0);

        public AccountSum plus(AccountSum other) {
            return new AccountSum(add(earned, other.earned), add(spent, other.spent), add(count, other.count));
        }
    }

    private final Map<FlowKey, Sum> flows = new LinkedHashMap<>();
    private final Map<AccountKey, AccountSum> accounts = new LinkedHashMap<>();

    private Aggregation() {
    }

    /**
     * Sums {@code rows}.
     *
     * @param ignoreReasons reasons that are not earnings/spending of a profile (own bank moves, staff changes); they still
     *                      count in the flows
     */
    public static Aggregation of(Collection<LedgerRow> rows, Set<String> ignoreReasons) {
        Aggregation out = new Aggregation();
        for (LedgerRow row : rows) {
            out.add(row, ignoreReasons);
        }
        return out;
    }

    private void add(LedgerRow row, Set<String> ignore) {
        if (row.amount() <= 0) {
            return;
        }
        long hour = row.hour();
        flows.merge(new FlowKey(hour, row.currency(), row.reason(), row.flow()), Sum.ZERO.plus(row.amount()), Sum::plus);
        if (ignore.contains(row.reason())) {
            return;
        }
        AccountRef to = row.to();
        if (to != null && to.isPlayer()) {
            accounts.merge(new AccountKey(hour, to.owner(), row.currency()), new AccountSum(row.amount(), 0, 1), AccountSum::plus);
        }
        AccountRef from = row.from();
        if (from != null && from.isPlayer()) {
            accounts.merge(new AccountKey(hour, from.owner(), row.currency()), new AccountSum(0, row.amount(), 1), AccountSum::plus);
        }
    }

    public Map<FlowKey, Sum> flows() {
        return flows;
    }

    public Map<AccountKey, AccountSum> accounts() {
        return accounts;
    }

    public boolean isEmpty() {
        return flows.isEmpty();
    }

    /** {@code a + b}, or {@link Long#MAX_VALUE} on overflow (both are never negative here). */
    public static long add(long a, long b) {
        long r = a + b;
        if (((a ^ r) & (b ^ r)) < 0) {
            return a < 0 ? Long.MIN_VALUE : Long.MAX_VALUE;
        }
        return r;
    }
}
