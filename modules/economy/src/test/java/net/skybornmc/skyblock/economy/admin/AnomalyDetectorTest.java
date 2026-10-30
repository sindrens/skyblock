package net.skybornmc.skyblock.economy.admin;

import net.skybornmc.skyblock.common.economy.AccountRef;
import net.skybornmc.skyblock.common.economy.Currency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static net.skybornmc.skyblock.economy.admin.WatchTestRows.A;
import static net.skybornmc.skyblock.economy.admin.WatchTestRows.B;
import static net.skybornmc.skyblock.economy.admin.WatchTestRows.C;
import static net.skybornmc.skyblock.economy.admin.WatchTestRows.T0;
import static net.skybornmc.skyblock.economy.admin.WatchTestRows.created;
import static net.skybornmc.skyblock.economy.admin.WatchTestRows.moved;
import static net.skybornmc.skyblock.economy.admin.WatchTestRows.removed;
import static net.skybornmc.skyblock.economy.admin.WatchTestRows.withBalances;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnomalyDetectorTest {

    private WatchConfig config;
    private AnomalyDetector detector;

    @BeforeEach
    void setUp() {
        config = new WatchConfig();
        detector = new AnomalyDetector(() -> config);
    }

    private static AnomalyDetector.Baselines supply(long money) {
        return new AnomalyDetector.Baselines(Map.of(), 0, Map.of(Currency.MONEY, money));
    }

    private static List<AlertDraft> of(List<AlertDraft> drafts, Rule rule) {
        return drafts.stream().filter(d -> d.rule() == rule).toList();
    }

    // ---------------------------------------------------------------- big transfer

    @Test
    void bigTransferUsesTheLargerOfMinimumAndSupplyShare() {
        config.bigMinMoney = 1_000;
        config.bigTransferSupplyPercent = 1.0;
        assertEquals(1_000, AnomalyDetector.bigThreshold(config, Currency.MONEY, 10_000));
        assertEquals(10_000, AnomalyDetector.bigThreshold(config, Currency.MONEY, 1_000_000));
    }

    @Test
    void bigTransferBetweenPlayersAlerts() {
        config.bigMinMoney = 1_000;
        List<AlertDraft> out = detector.rows(List.of(moved(1, T0, A, B, 999, "pay"), moved(2, T0, A, B, 5_000, "trade")),
                supply(0), T0);
        List<AlertDraft> big = of(out, Rule.BIG_TRANSFER);
        assertEquals(1, big.size());
        assertEquals(A, big.getFirst().subject());
        assertEquals(B, big.getFirst().other());
        assertEquals(List.of(2L), big.getFirst().ledgerIds());
        assertEquals(AlertDraft.Severity.HIGH, big.getFirst().severity()); // 5x the threshold
    }

    @Test
    void bigCreationAlertsButStaffAndBankMovesDoNot() {
        config.bigMinMoney = 1_000;
        LedgerRow toBank = new LedgerRow(3, T0, Currency.MONEY, AccountRef.player(A), AccountRef.bank(A), 50_000, null, null, "bank_deposit");
        List<AlertDraft> out = detector.rows(List.of(created(1, T0, A, 5_000, "admin_give"), created(2, T0, A, 2_000, "ah_sale"),
                toBank, removed(4, T0, A, 90_000, "shop_buy")), supply(0), T0);
        List<AlertDraft> big = of(out, Rule.BIG_TRANSFER);
        assertEquals(1, big.size());
        assertEquals(2L, big.getFirst().ledgerIds().getFirst());
        config.bigIncludeCreated = false;
        assertTrue(of(new AnomalyDetector(() -> config).rows(List.of(created(9, T0, A, 9_000, "sell")), supply(0), T0),
                Rule.BIG_TRANSFER).isEmpty());
    }

    // ---------------------------------------------------------------- repeat transfer

    @Test
    void repeatedNearIdenticalTransfersAlertAtTheCount() {
        config.repeatCount = 4;
        config.repeatMinAmount = 100;
        config.repeatTolerancePercent = 5;
        List<LedgerRow> rows = new ArrayList<>();
        rows.add(moved(1, T0, A, B, 10_000, "pay"));
        rows.add(moved(2, T0 + 1_000, A, B, 10_200, "pay"));
        rows.add(moved(3, T0 + 2_000, A, B, 30_000, "pay"));   // not similar
        rows.add(moved(4, T0 + 3_000, A, C, 10_000, "pay"));   // other receiver
        rows.add(moved(5, T0 + 4_000, A, B, 9_900, "pay"));
        assertTrue(of(detector.rows(rows, supply(0), T0), Rule.REPEAT_TRANSFER).isEmpty());
        List<AlertDraft> out = of(detector.rows(List.of(moved(6, T0 + 5_000, A, B, 10_100, "pay")), supply(0), T0), Rule.REPEAT_TRANSFER);
        assertEquals(1, out.size());
        assertEquals(4, out.getFirst().hits());
        assertEquals(List.of(1L, 2L, 5L, 6L), out.getFirst().ledgerIds());
        assertEquals(10_000 + 10_200 + 9_900 + 10_100, out.getFirst().amount());
    }

    @Test
    void repeatWindowForgetsOldTransfers() {
        config.repeatCount = 3;
        config.repeatMinAmount = 1;
        config.repeatWindowMinutes = 1;
        List<AlertDraft> out = detector.rows(List.of(moved(1, T0, A, B, 500, "pay"), moved(2, T0 + 1, A, B, 500, "pay"),
                moved(3, T0 + 61_000, A, B, 500, "pay")), supply(0), T0);
        assertTrue(of(out, Rule.REPEAT_TRANSFER).isEmpty());
    }

    @Test
    void smallTipsAreIgnored() {
        config.repeatCount = 2;
        config.repeatMinAmount = 1_000;
        assertTrue(of(detector.rows(List.of(moved(1, T0, A, B, 10, "pay"), moved(2, T0, A, B, 10, "pay")), supply(0), T0),
                Rule.REPEAT_TRANSFER).isEmpty());
    }

    @Test
    void similarIsRelativeToTheLargerAmount() {
        assertTrue(AnomalyDetector.similar(100, 105, 5));
        assertTrue(!AnomalyDetector.similar(100, 110, 5));
        assertTrue(AnomalyDetector.similar(7, 7, 0));
    }

    // ---------------------------------------------------------------- notes

    @Test
    void manyNotesCashedInOneHour() {
        config.noteCount = 3;
        List<LedgerRow> rows = List.of(created(1, T0, A, 100, "note_redeem"), created(2, T0 + 5, A, 100, "note_redeem"),
                created(3, T0 + Buckets.HOUR_MS, A, 100, "note_redeem"), created(4, T0 + 9, A, 100, "note_redeem"));
        List<AlertDraft> out = of(detector.rows(rows, supply(0), T0), Rule.NOTE_REDEEM);
        assertEquals(1, out.size());
        assertEquals(3, out.getFirst().hits());
        assertEquals(List.of(1L, 2L, 4L), out.getFirst().ledgerIds());
    }

    // ---------------------------------------------------------------- balance continuity

    @Test
    void balanceJumpWithoutALedgerRowIsFound() {
        List<LedgerRow> rows = List.of(
                withBalances(created(1, T0, A, 100, "sell"), null, 1_100L),       // A: 1,000 -> 1,100
                withBalances(removed(2, T0, A, 100, "shop_buy"), 1_000L, null),   // fine: 1,100 -> 1,000
                withBalances(created(3, T0, A, 50, "sell"), null, 1_000_050L));   // before = 1,000,000 but 1,000 known
        List<AlertDraft> out = of(detector.rows(rows, supply(0), T0), Rule.BALANCE_MISMATCH);
        assertEquals(1, out.size());
        assertEquals(999_000, out.getFirst().amount());
        assertEquals(1_000, out.getFirst().expected());
        assertEquals(A, out.getFirst().subject());
        assertEquals(AlertDraft.Severity.HIGH, out.getFirst().severity());
    }

    @Test
    void transfersKeepBothSidesContinuous() {
        List<LedgerRow> rows = List.of(
                withBalances(moved(1, T0, A, B, 10, "pay"), 90L, 10L),
                withBalances(moved(2, T0, B, A, 5, "pay"), 5L, 95L));
        assertTrue(of(detector.rows(rows, supply(0), T0), Rule.BALANCE_MISMATCH).isEmpty());
    }

    @Test
    void rowsAreCheckedOnceAndResetForgets() {
        LedgerRow r1 = withBalances(created(1, T0, A, 100, "sell"), null, 100L);
        LedgerRow r2 = withBalances(created(2, T0, A, 100, "sell"), null, 900L);
        assertEquals(1, of(detector.rows(List.of(r1, r2), supply(0), T0), Rule.BALANCE_MISMATCH).size());
        // the same rows again (a retried batch) are skipped
        assertTrue(detector.rows(List.of(r1, r2), supply(0), T0).isEmpty());
        detector.reset();
        assertEquals(0, detector.trackedBalances());
        assertEquals(1, of(detector.rows(List.of(r1, r2), supply(0), T0), Rule.BALANCE_MISMATCH).size());
    }

    @Test
    void trackedBalancesAreBounded() {
        config.mismatchTracked = 1_000;
        List<LedgerRow> rows = new ArrayList<>();
        for (int i = 1; i <= 1_500; i++) {
            rows.add(withBalances(created(i, T0, new java.util.UUID(0, i), 1, "sell"), null, 1L));
        }
        detector.rows(rows, supply(0), T0);
        assertEquals(1_000, detector.trackedBalances());
    }

    // ---------------------------------------------------------------- earn rate

    @Test
    void earnRateComparesTheWholeHourWithTheBaseline() {
        config.earnMinMoney = 1_000;
        config.earnMultiplier = 3;
        assertEquals(1_000, AnomalyDetector.earnThreshold(config, Currency.MONEY, 0));
        assertEquals(30_000, AnomalyDetector.earnThreshold(config, Currency.MONEY, 10_000));
        AnomalyDetector.Baselines base = new AnomalyDetector.Baselines(Map.of(Currency.MONEY, 10_000L), 0, Map.of());
        Map<Aggregation.AccountKey, Aggregation.AccountSum> totals = Map.of(
                new Aggregation.AccountKey(1000, A, Currency.MONEY), new Aggregation.AccountSum(30_001, 0, 4),
                new Aggregation.AccountKey(1000, B, Currency.MONEY), new Aggregation.AccountSum(29_000, 0, 9),
                new Aggregation.AccountKey(1000, C, Currency.GEMS), new Aggregation.AccountSum(999_999, 0, 1));
        List<LedgerRow> evidence = List.of(created(7, T0, A, 20_000, "sell"), created(8, T0, A, 1, "sell"),
                created(9, T0, B, 5, "sell"));
        List<AlertDraft> out = detector.accountTotals(totals, evidence, base);
        assertEquals(1, out.size()); // gems are not in alerts.currencies by default
        AlertDraft d = out.getFirst();
        assertEquals(Rule.EARN_RATE, d.rule());
        assertEquals(A, d.subject());
        assertEquals(30_000, d.expected());
        assertEquals(List.of(7L, 8L), d.ledgerIds());
        assertEquals(AlertDraft.Severity.WATCH, d.severity());
    }

    @Test
    void earnRateCanBeSwitchedOff() {
        config.earnRateEnabled = false;
        assertTrue(detector.accountTotals(Map.of(new Aggregation.AccountKey(1, A, Currency.MONEY),
                new Aggregation.AccountSum(Long.MAX_VALUE, 0, 1)), List.of(), AnomalyDetector.Baselines.NONE).isEmpty());
    }

    // ---------------------------------------------------------------- sell spike

    @Test
    void sellSpikeAboveTheHourlyAverage() {
        config.sellSpikeMin = 100;
        config.sellSpikeMultiplier = 4;
        AnomalyDetector.Baselines base = new AnomalyDetector.Baselines(Map.of(), 1_000, Map.of());
        Map<Aggregation.FlowKey, Aggregation.Sum> totals = Map.of(
                new Aggregation.FlowKey(1000, Currency.MONEY, "sell", Flow.CREATED), new Aggregation.Sum(4_001, 30),
                new Aggregation.FlowKey(1000, Currency.MONEY, "depths_drop", Flow.CREATED), new Aggregation.Sum(99_999, 30));
        List<AlertDraft> out = detector.flowTotals(totals, base);
        assertEquals(1, out.size());
        assertEquals(4_000, out.getFirst().expected());
        assertTrue(detector.flowTotals(Map.of(new Aggregation.FlowKey(1000, Currency.MONEY, "sell", Flow.CREATED),
                new Aggregation.Sum(4_000, 1)), base).isEmpty());
    }

    // ---------------------------------------------------------------- cooldown

    @Test
    void cooldownKeepsOnePerKeyAndHoldsRepeatsBack() {
        config.cooldownMinutes = 10;
        AlertDraft small = new AlertDraft(Rule.EARN_RATE, AlertDraft.Severity.WATCH, Currency.MONEY, A, null, 10, 1, 1, 1000, List.of());
        AlertDraft large = new AlertDraft(Rule.EARN_RATE, AlertDraft.Severity.WATCH, Currency.MONEY, A, null, 99, 1, 1, 1000, List.of());
        AlertDraft other = new AlertDraft(Rule.EARN_RATE, AlertDraft.Severity.WATCH, Currency.MONEY, B, null, 5, 1, 1, 1000, List.of());
        List<AlertDraft> first = detector.filter(List.of(small, large, other), T0);
        assertEquals(2, first.size());
        assertEquals(99, first.getFirst().amount());
        assertTrue(detector.filter(List.of(small), T0 + 60_000).isEmpty());
        assertEquals(1, detector.filter(List.of(small), T0 + 600_000).size());
    }

    @Test
    void repeatTransferCooldownIsPerPair() {
        AlertDraft ab = new AlertDraft(Rule.REPEAT_TRANSFER, AlertDraft.Severity.WATCH, Currency.MONEY, A, B, 1, 1, 5, 1, List.of());
        AlertDraft ac = new AlertDraft(Rule.REPEAT_TRANSFER, AlertDraft.Severity.WATCH, Currency.MONEY, A, C, 1, 1, 5, 1, List.of());
        assertEquals(2, detector.filter(List.of(ab, ac), T0).size());
        assertEquals(Set.of(), Set.copyOf(detector.filter(List.of(ab, ac), T0 + 1)));
    }
}
