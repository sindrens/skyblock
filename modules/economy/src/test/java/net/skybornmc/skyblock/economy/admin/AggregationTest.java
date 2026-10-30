package net.skybornmc.skyblock.economy.admin;

import net.skybornmc.skyblock.common.economy.AccountRef;
import net.skybornmc.skyblock.common.economy.Currency;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static net.skybornmc.skyblock.economy.admin.WatchTestRows.A;
import static net.skybornmc.skyblock.economy.admin.WatchTestRows.B;
import static net.skybornmc.skyblock.economy.admin.WatchTestRows.T0;
import static net.skybornmc.skyblock.economy.admin.WatchTestRows.created;
import static net.skybornmc.skyblock.economy.admin.WatchTestRows.moved;
import static net.skybornmc.skyblock.economy.admin.WatchTestRows.removed;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AggregationTest {

    @Test
    void sumsPerHourReasonAndFlow() {
        Aggregation agg = Aggregation.of(List.of(
                created(1, T0, A, 100, "sell"),
                created(2, T0 + 10, A, 50, "sell"),
                created(3, T0 + Buckets.HOUR_MS, A, 7, "sell"),
                removed(4, T0, A, 30, "shop_buy"),
                moved(5, T0, A, B, 20, "pay")), Set.of());
        assertEquals(new Aggregation.Sum(150, 2), agg.flows().get(new Aggregation.FlowKey(1000, Currency.MONEY, "sell", Flow.CREATED)));
        assertEquals(new Aggregation.Sum(7, 1), agg.flows().get(new Aggregation.FlowKey(1001, Currency.MONEY, "sell", Flow.CREATED)));
        assertEquals(new Aggregation.Sum(30, 1), agg.flows().get(new Aggregation.FlowKey(1000, Currency.MONEY, "shop_buy", Flow.REMOVED)));
        assertEquals(new Aggregation.Sum(20, 1), agg.flows().get(new Aggregation.FlowKey(1000, Currency.MONEY, "pay", Flow.MOVED)));
    }

    @Test
    void accountsEarnAndSpend() {
        Aggregation agg = Aggregation.of(List.of(
                created(1, T0, A, 100, "sell"),
                removed(2, T0, A, 30, "shop_buy"),
                moved(3, T0, A, B, 20, "pay")), Set.of());
        assertEquals(new Aggregation.AccountSum(100, 50, 3), agg.accounts().get(new Aggregation.AccountKey(1000, A, Currency.MONEY)));
        assertEquals(new Aggregation.AccountSum(20, 0, 1), agg.accounts().get(new Aggregation.AccountKey(1000, B, Currency.MONEY)));
    }

    @Test
    void ignoredReasonsCountInFlowsButNotForProfiles() {
        LedgerRow toBank = new LedgerRow(1, T0, Currency.MONEY, AccountRef.player(A), AccountRef.bank(A), 500, null, null, "bank_deposit");
        Aggregation agg = Aggregation.of(List.of(toBank, created(2, T0, A, 1_000, "admin_give")), Set.of("bank_deposit", "admin_give"));
        assertEquals(2, agg.flows().size());
        assertTrue(agg.accounts().isEmpty());
    }

    @Test
    void onlyPlayerAccountsAreTracked() {
        LedgerRow interest = new LedgerRow(1, T0, Currency.MONEY, null, AccountRef.island(7L), 10, null, null, "interest");
        Aggregation agg = Aggregation.of(List.of(interest), Set.of());
        assertFalse(agg.isEmpty());
        assertTrue(agg.accounts().isEmpty());
    }

    @Test
    void sumsSaturateInsteadOfWrapping() {
        assertEquals(Long.MAX_VALUE, Aggregation.add(Long.MAX_VALUE - 1, 5));
        Aggregation agg = Aggregation.of(List.of(created(1, T0, A, Long.MAX_VALUE, "sell"), created(2, T0, A, Long.MAX_VALUE, "sell")),
                Set.of());
        assertEquals(Long.MAX_VALUE, agg.flows().values().iterator().next().amount());
    }

    @Test
    void hourBucketsAreUtcHours() {
        assertEquals(1000, Buckets.hour(T0));
        assertEquals(999, Buckets.hour(T0 - 1));
        assertEquals(T0, Buckets.start(1000));
    }
}
