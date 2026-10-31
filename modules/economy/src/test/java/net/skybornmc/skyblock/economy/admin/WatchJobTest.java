package net.skybornmc.skyblock.economy.admin;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.DatabaseConfig;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.db.Sql;
import net.skybornmc.skyblock.common.economy.Currency;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.UnaryOperator;
import java.util.logging.Logger;

import static net.skybornmc.skyblock.economy.admin.WatchTestRows.A;
import static net.skybornmc.skyblock.economy.admin.WatchTestRows.B;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The incremental job against a real (H2, MySQL mode) database: exactly-once sums, holes, alerts, supply, dashboard. */
class WatchJobTest {

    @TempDir
    Path dir;

    private Database db;
    private WatchStore store;
    private WatchConfig config;
    private AnomalyDetector detector;
    private WatchJob job;
    private final AtomicLong clock = new AtomicLong(WatchTestRows.T0 + 30 * 60_000L);

    @BeforeEach
    void start() throws Exception {
        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.type = DatabaseConfig.Type.H2;
        dbConfig.h2File = "watch-test";
        dbConfig.poolSize = 4;
        dbConfig.slowQueryMs = 60_000;
        db = new Database(dbConfig, dir.toFile(), Logger.getLogger("watch-test"));
        db.start();
        new Migrations(db, Logger.getLogger("watch-test")).migrate("core", getClass().getClassLoader());
        new Migrations(db, Logger.getLogger("watch-test")).migrate("economy", getClass().getClassLoader());
        player(A, "Alice");
        player(B, "Bob");
        store = new WatchStore(db);
        config = new WatchConfig();
        detector = new AnomalyDetector(() -> config);
        job = new WatchJob(store, detector, () -> config, clock::get, () -> ZoneId.of("Europe/Oslo"), () -> UnaryOperator.identity());
    }

    @AfterEach
    void stop() {
        db.close();
    }

    private void player(UUID id, String name) {
        db.blocking(c -> Sql.update(c, "INSERT INTO players (uuid, name, name_lower, first_seen, last_seen) VALUES (?, ?, ?, 0, 0)",
                id, name, name.toLowerCase()));
    }

    /** Inserts a ledger row; {@code id <= 0} = AUTO_INCREMENT. */
    private void ledger(long id, long time, String fromType, UUID from, String toType, UUID to, long amount, String reason) {
        db.blocking(c -> {
            if (id > 0) {
                return Sql.update(c, "INSERT INTO eco_ledger (id, created_at, season_id, currency, from_type, from_id, to_type, to_id, "
                        + "amount, reason, actor_name) VALUES (?, ?, 0, 'money', ?, ?, ?, ?, ?, ?, 'test')", id, time, fromType, from,
                        toType, to, amount, reason);
            }
            return Sql.update(c, "INSERT INTO eco_ledger (created_at, season_id, currency, from_type, from_id, to_type, to_id, "
                    + "amount, reason, actor_name) VALUES (?, 0, 'money', ?, ?, ?, ?, ?, ?, 'test')", time, fromType, from, toType, to,
                    amount, reason);
        });
    }

    private void sale(long id, UUID to, long amount) {
        ledger(id, clock.get() - 1_000, null, null, "player", to, amount, "sell");
    }

    private WatchJob.Result run() {
        return db.blocking(c -> job.runNow(c));
    }

    private long flow(String reason, Flow flow) {
        return db.blocking(c -> Sql.queryOne(c, "SELECT SUM(amount_total) FROM eco_watch_flows WHERE reason = ? AND flow = ?",
                rs -> WatchStore.sum(rs, 1), reason, flow.key()).orElse(0L));
    }

    /** PERF11: the periodic trigger starts runs from Bukkit's async pool; concurrent starts share one run. */
    @Test
    void runsStartedFromManyThreadsShareOneRunAndCountOnce() throws Exception {
        sale(0, A, 100);
        sale(0, B, 50);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(4);
        try {
            List<java.util.concurrent.Future<java.util.concurrent.CompletableFuture<WatchJob.Result>>> started = new java.util.ArrayList<>();
            for (int i = 0; i < 8; i++) {
                started.add(pool.submit(() -> WatchTrigger.shouldStart(true, job) || job.busy() ? job.run() : null));
            }
            int rows = 0;
            java.util.Set<java.util.concurrent.CompletableFuture<WatchJob.Result>> runs =
                    java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
            for (var f : started) {
                var run = f.get(10, java.util.concurrent.TimeUnit.SECONDS);
                if (run != null) {
                    runs.add(run);
                }
            }
            for (var run : runs) {
                rows += run.get(10, java.util.concurrent.TimeUnit.SECONDS).rows();
            }
            assertEquals(2, rows, "every row counted once, however many threads started a run");
        } finally {
            pool.shutdown();
        }
        assertEquals(150, flow("sell", Flow.CREATED));
    }

    @Test
    void everyRowIsCountedExactlyOnce() {
        sale(0, A, 100);
        sale(0, A, 50);
        ledger(0, clock.get() - 500, "player", A, null, null, 30, "shop_buy");
        WatchJob.Result first = run();
        assertEquals(3, first.rows());
        assertEquals(150, flow("sell", Flow.CREATED));
        assertEquals(30, flow("shop_buy", Flow.REMOVED));

        assertEquals(0, run().rows());
        assertEquals(150, flow("sell", Flow.CREATED));

        sale(0, B, 7);
        assertEquals(1, run().rows());
        assertEquals(157, flow("sell", Flow.CREATED));
        long earnedA = db.blocking(c -> Sql.queryOne(c, "SELECT earned FROM eco_watch_accounts WHERE owner_id = ?",
                rs -> rs.getLong(1), A).orElse(0L));
        assertEquals(150, earnedA);
    }

    @Test
    void catchesUpInBatchesAcrossRuns() {
        config.batchSize = 100;
        config.maxBatchesPerRun = 2;
        for (int i = 0; i < 250; i++) {
            sale(0, A, 1);
        }
        assertEquals(200, run().rows());
        assertEquals(50, run().rows());
        assertEquals(250, flow("sell", Flow.CREATED));
        assertEquals(250, job.cursor());
    }

    @Test
    void waitsForAHoleUntilItIsOld() {
        long now = clock.get();
        ledger(1, now - 10_000, null, null, "player", A, 10, "sell");
        ledger(2, now - 10_000, null, null, "player", A, 10, "sell");
        ledger(4, now - 5_000, null, null, "player", A, 10, "sell"); // id 3 is "still committing"
        WatchJob.Result first = run();
        assertEquals(2, first.rows());
        assertEquals(2, first.cursor());
        // the late row arrives: it is picked up, then 4
        ledger(3, now - 4_000, null, null, "player", A, 10, "sell");
        assertEquals(2, run().rows());
        assertEquals(40, flow("sell", Flow.CREATED));

        // a hole that is never filled (rolled back) is skipped once the row after it is older than the grace time
        ledger(6, now - 1_000, null, null, "player", A, 10, "sell");
        assertEquals(0, run().rows());
        clock.addAndGet(config.gapGraceSeconds * 1000L + 1);
        assertEquals(1, run().rows());
        assertEquals(6, job.cursor());
    }

    @Test
    void bigTransferBecomesAStoredAlertWithNames() {
        config.bigMinMoney = 1_000;
        ledger(0, clock.get() - 1_000, "player", A, "player", B, 50_000, "pay");
        WatchJob.Result result = run();
        assertEquals(1, result.alerts().size());
        WatchAlert alert = result.alerts().getFirst();
        assertEquals(Rule.BIG_TRANSFER, alert.rule());
        assertEquals(A, alert.subject());
        assertEquals(B, alert.other());
        assertEquals("Alice", result.names().get(A));
        assertEquals("Bob", result.names().get(B));
        Optional<WatchAlert> stored = db.blocking(c -> WatchStore.alert(c, alert.id()));
        assertTrue(stored.isPresent());
        assertEquals(List.of(1L), stored.get().ledgerIds());
        assertEquals(WatchAlert.State.OPEN, stored.get().state());
        // the same transfer is not alerted again (it is behind the cursor)
        assertTrue(run().alerts().isEmpty());
    }

    @Test
    void earnRateAlertUsesTheWholeHour() {
        config.earnMinMoney = 1_000;
        sale(0, A, 600);
        assertTrue(run().alerts().isEmpty());
        sale(0, A, 600); // 1,200 this hour > 1,000
        List<WatchAlert> alerts = run().alerts();
        assertEquals(1, alerts.size());
        assertEquals(Rule.EARN_RATE, alerts.getFirst().rule());
        assertEquals(1_200, alerts.getFirst().amount());
    }

    @Test
    void catchingUpOnOldRowsFillsSumsWithoutAlerts() {
        config.bigMinMoney = 1_000;
        ledger(0, clock.get() - (config.alertMaxAgeHours + 1) * Buckets.HOUR_MS, "player", A, "player", B, 50_000, "pay");
        WatchJob.Result result = run();
        assertEquals(1, result.rows());
        assertTrue(result.alerts().isEmpty());
        assertEquals(50_000, flow("pay", Flow.MOVED));
    }

    @Test
    void reviewStatesChangeOnceAndAreKept() {
        config.bigMinMoney = 1_000;
        ledger(0, clock.get() - 1_000, "player", A, "player", B, 50_000, "pay");
        long id = run().alerts().getFirst().id();
        long now = clock.get();
        assertEquals(Optional.of(WatchAlert.State.OPEN), db.blocking(c -> WatchStore.setState(c, id, WatchAlert.State.REVIEWED, "Mod", now)));
        assertEquals(Optional.empty(), db.blocking(c -> WatchStore.setState(c, id, WatchAlert.State.REVIEWED, "Mod", now)));
        WatchAlert reviewed = db.blocking(c -> WatchStore.alert(c, id)).orElseThrow();
        assertEquals("Mod", reviewed.reviewedBy());
        assertEquals(0, db.blocking(WatchStore::openCount));
        assertEquals(Optional.of(WatchAlert.State.REVIEWED), db.blocking(c -> WatchStore.setState(c, id, WatchAlert.State.OPEN, "Mod", now)));
        assertNull(db.blocking(c -> WatchStore.alert(c, id)).orElseThrow().reviewedBy());
        assertEquals(1, db.blocking(c -> WatchStore.alerts(c, true, 0, 10)).total());
    }

    @Test
    void cursorConflictWritesNothing() {
        sale(0, A, 100);
        Aggregation agg = Aggregation.of(db.blocking(c -> WatchStore.rowsAfter(c, 0, 10)), java.util.Set.of());
        db.blocking(WatchStore::cursor);
        WatchStore.Outcome outcome = db.blocking(c -> WatchStore.apply(c, 99, 1, agg, null, clock.get()));
        assertNull(outcome);
        assertEquals(0, flow("sell", Flow.CREATED));
    }

    @Test
    void supplySnapshotAndDashboard() {
        db.blocking(c -> Sql.update(c, "INSERT INTO eco_accounts (owner_type, owner_id, currency, balance, updated_at) "
                + "VALUES ('player', ?, 'money', 5000, 0)", A));
        db.blocking(c -> Sql.update(c, "INSERT INTO eco_accounts (owner_type, owner_id, currency, balance, updated_at) "
                + "VALUES ('bank', ?, 'money', 2000, 0)", A));
        db.blocking(c -> Sql.update(c, "INSERT INTO money_notes (id, amount, currency, kind, created_name, created_at, status) "
                + "VALUES (?, 300, 'money', 'withdraw', 'Alice', 0, 'active')", UUID.randomUUID()));
        sale(0, A, 1_000);
        ledger(0, clock.get() - 900, "player", A, null, null, 400, "shop_buy");
        ledger(0, clock.get() - 800, "player", A, null, null, 10_000, "ah_buy");
        ledger(0, clock.get() - 700, null, null, "player", B, 9_500, "ah_sale");
        run();
        assertEquals(7_300L, db.blocking(c -> WatchStore.latestSupply(c)).get(Currency.MONEY));

        DashboardData data = db.blocking(c -> DashboardData.load(c, Currency.MONEY, config, ZoneId.of("Europe/Oslo"), clock.get()));
        assertEquals(7_300, data.supplyNow());
        assertEquals(300, data.notesNow());
        assertEquals(1_000, data.today().totals().created());
        assertEquals(900, data.today().totals().removed()); // shop 400 + auction tax 500
        assertEquals(500, data.week().reason("auction").removed());
        assertEquals(InflationRisk.Level.NO_DATA, data.risk().level());

        List<WatchStore.TopAccount> top = db.blocking(c -> WatchStore.topEarners(c, Currency.MONEY, 0, 10));
        assertEquals(B, top.getFirst().profile());
        assertEquals(9_500, top.getFirst().earned());
        List<LedgerRow> biggest = db.blocking(c -> WatchStore.biggest(c, Currency.MONEY, 0, Long.MAX_VALUE, List.of(), 2));
        assertEquals(List.of(10_000L, 9_500L), biggest.stream().map(LedgerRow::amount).toList());
        List<LedgerRow> sells = db.blocking(c -> WatchStore.biggest(c, Currency.MONEY, 0, Long.MAX_VALUE, List.of("sell"), 5));
        assertEquals(1, sells.size());
    }

    @Test
    void baselinesNeedEnoughHoursOfData() {
        long nowHour = Buckets.hour(clock.get());
        db.blocking(c -> {
            for (int h = 1; h <= 30; h++) {
                Sql.update(c, "INSERT INTO eco_watch_flows (bucket_hour, currency, reason, flow, amount_total, tx_count) "
                        + "VALUES (?, 'money', 'sell', 'created', 1000, 1)", nowHour - h);
                Sql.update(c, "INSERT INTO eco_watch_accounts (bucket_hour, owner_id, currency, earned, spent, tx_count) "
                        + "VALUES (?, ?, 'money', 200, 0, 1)", nowHour - h, A);
                Sql.update(c, "INSERT INTO eco_watch_accounts (bucket_hour, owner_id, currency, earned, spent, tx_count) "
                        + "VALUES (?, ?, 'money', 400, 0, 1)", nowHour - h, B);
            }
            return null;
        });
        AnomalyDetector.Baselines base = db.blocking(c -> WatchStore.baselines(c, nowHour, 14, 24, "sell"));
        assertEquals(300L, base.earnPerHour().get(Currency.MONEY));
        assertEquals(1_000L, base.sellPerHour());
        AnomalyDetector.Baselines tooEarly = db.blocking(c -> WatchStore.baselines(c, nowHour, 14, 48, "sell"));
        assertTrue(tooEarly.earnPerHour().isEmpty());
        assertEquals(0, tooEarly.sellPerHour());
    }

    @Test
    void purgeDropsOldRowsButKeepsOpenAlerts() {
        long nowHour = Buckets.hour(clock.get());
        db.blocking(c -> {
            Sql.update(c, "INSERT INTO eco_watch_flows (bucket_hour, currency, reason, flow, amount_total, tx_count) "
                    + "VALUES (?, 'money', 'sell', 'created', 1, 1)", nowHour - config.flowDays * 24L - 1);
            WatchStore.insertAlert(c, new AlertDraft(Rule.SELL_SPIKE, AlertDraft.Severity.WATCH, Currency.MONEY, null, null, 1, 1, 1,
                    nowHour, List.of()), 0);
            return null;
        });
        db.blocking(c -> WatchStore.purge(c, nowHour, config, clock.get()));
        assertEquals(0, flow("sell", Flow.CREATED));
        assertEquals(1, db.blocking(WatchStore::openCount));
    }

    @Test
    void ledgerIdsAreStoredWithinTheColumn() {
        List<Long> ids = new java.util.ArrayList<>();
        for (long i = 1_000_000_000_000L; i < 1_000_000_000_000L + 60; i++) {
            ids.add(i);
        }
        String joined = WatchAlert.joinIds(ids, WatchStore.LEDGER_IDS_CHARS);
        assertTrue(joined.length() <= WatchStore.LEDGER_IDS_CHARS);
        List<Long> back = WatchAlert.parseIds(joined);
        assertEquals(ids.getLast(), back.getLast(), "the newest ids are kept");
        assertEquals(List.of(1L, 2L), WatchAlert.parseIds("1, x,2"));
    }
}
