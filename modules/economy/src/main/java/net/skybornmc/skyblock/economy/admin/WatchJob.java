package net.skybornmc.skyblock.economy.admin;

import net.skybornmc.skyblock.common.economy.Currency;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * The incremental background job of the economy watch (M3.8). Each run, on a database thread:
 * <ol>
 *   <li>a money supply snapshot when the last one is older than {@code job.supply-minutes} (and the sink-ratio check);</li>
 *   <li>the baselines of the anomaly rules from the summary tables;</li>
 *   <li>batches of new ledger rows after the saved cursor, up to {@code job.max-batches-per-run}: each batch is summed,
 *       checked by the {@link AnomalyDetector} and written together with the new cursor and its alerts in ONE
 *       transaction ({@link WatchStore#apply}). A hole in the ids from a transaction still committing stops the batch
 *       until it is filled or old enough ({@link CursorRules});</li>
 *   <li>old summary rows are purged once a day.</li>
 * </ol>
 * Only one run at a time ({@link #run} returns the running one's future while busy). Nothing here touches the server.
 */
public final class WatchJob {

    /** What a run did. {@code names}: player names of every profile in the alerts. */
    public record Result(int rows, long cursor, List<WatchAlert> alerts, Map<UUID, String> names) {
        static final Result IDLE = new Result(0, -1, List.of(), Map.of());
    }

    private final WatchStore store;
    private final AnomalyDetector detector;
    private final Supplier<WatchConfig> config;
    private final LongSupplier clock;
    private final Supplier<ZoneId> zone;
    private final Supplier<UnaryOperator<UUID>> owners;
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile CompletableFuture<Result> current = CompletableFuture.completedFuture(Result.IDLE);
    private volatile long lastSupply = Long.MIN_VALUE;
    private volatile long lastPurgeDay = Long.MIN_VALUE;
    private volatile LocalDate lastSinkAlert;
    private volatile long lastRun;
    private volatile long cursor = -1;

    public WatchJob(WatchStore store, AnomalyDetector detector, Supplier<WatchConfig> config, LongSupplier clock,
                    Supplier<ZoneId> zone, Supplier<UnaryOperator<UUID>> owners) {
        this.store = store;
        this.detector = detector;
        this.config = config;
        this.clock = clock;
        this.zone = zone;
        this.owners = owners;
    }

    /**
     * Starts a run (or returns the one in progress). Completes exceptionally on a database error. Any thread
     * (PERF11: the periodic trigger is an async task, the dashboard's run button is on the main thread); synchronized so
     * a caller that finds a run going always gets that run's future.
     */
    public synchronized CompletableFuture<Result> run() {
        if (!running.compareAndSet(false, true)) {
            return current;
        }
        CompletableFuture<Result> future = store.async(this::runNow);
        current = future;
        future.whenComplete((r, e) -> running.set(false));
        return future;
    }

    public boolean busy() {
        return running.get();
    }

    /** Time of the last finished run (0 = none yet). */
    public long lastRun() {
        return lastRun;
    }

    /** The cursor after the last run (-1 = unknown). */
    public long cursor() {
        return cursor;
    }

    /** One run on the calling (database) thread. Public for tests. */
    public Result runNow(Connection c) throws SQLException {
        WatchConfig cfg = config.get();
        long now = clock.getAsLong();
        long nowHour = Buckets.hour(now);
        List<WatchAlert> alerts = new ArrayList<>();
        if (lastSupply == Long.MIN_VALUE || now - lastSupply >= cfg.supplyMinutes * 60_000L) {
            WatchStore.saveSupply(c, nowHour, WatchStore.liveSupply(c), now);
            lastSupply = now;
            WatchAlert sink = sinkRatio(c, cfg, now, nowHour);
            if (sink != null) {
                alerts.add(sink);
            }
        }
        AnomalyDetector.Baselines base = WatchStore.baselines(c, nowHour, cfg.baselineDays, cfg.baselineMinHours, cfg.sellReason);
        Set<String> ignore = Set.copyOf(cfg.ignoreReasons);
        int processed = 0;
        long at = WatchStore.cursor(c);
        for (int batch = 0; batch < cfg.maxBatchesPerRun; batch++) {
            List<LedgerRow> rows = WatchStore.rowsAfter(c, at, cfg.batchSize);
            if (rows.isEmpty()) {
                break;
            }
            int n = CursorRules.safeCount(at, rows.stream().map(LedgerRow::id).toList(), rows.stream().map(LedgerRow::time).toList(),
                    now, cfg.gapGraceSeconds * 1000L);
            if (n == 0) {
                break;
            }
            List<LedgerRow> safe = List.copyOf(rows.subList(0, n));
            long next = safe.getLast().id();
            Aggregation sums = Aggregation.of(safe, ignore);
            WatchStore.Outcome outcome;
            try {
                List<AlertDraft> rowDrafts = detector.rows(safe, base, now);
                long expected = at;
                outcome = inTransaction(c, () -> WatchStore.apply(c, expected, next, sums, totals -> {
                    List<AlertDraft> drafts = new ArrayList<>(rowDrafts);
                    drafts.addAll(detector.accountTotals(totals.accounts(), safe, base));
                    drafts.addAll(detector.flowTotals(totals.flows(), base));
                    drafts.removeIf(d -> d.hour() < nowHour - cfg.alertMaxAgeHours);
                    return detector.filter(drafts, now);
                }, now));
            } catch (SQLException | RuntimeException e) {
                detector.reset();
                throw e;
            }
            if (outcome == null) {
                detector.reset(); // another writer moved the cursor: start over from the saved one next run
                break;
            }
            alerts.addAll(outcome.alerts());
            processed += n;
            at = next;
            if (n < rows.size() || rows.size() < cfg.batchSize) {
                break;
            }
        }
        long day = Math.floorDiv(now, 86_400_000L);
        if (day != lastPurgeDay) {
            lastPurgeDay = day;
            WatchStore.purge(c, nowHour, cfg, now);
        }
        Set<UUID> profiles = new HashSet<>();
        for (WatchAlert a : alerts) {
            if (a.subject() != null) {
                profiles.add(a.subject());
            }
            if (a.other() != null) {
                profiles.add(a.other());
            }
        }
        Map<UUID, String> names = profiles.isEmpty() ? Map.of() : WatchStore.names(c, profiles, owners.get());
        lastRun = now;
        cursor = at;
        return new Result(processed, at, List.copyOf(alerts), names);
    }

    /**
     * The sink-ratio alert (ECONOMY_BALANCE §7.2): money only, at most once per display day, when the badge says Act.
     */
    private WatchAlert sinkRatio(Connection c, WatchConfig cfg, long now, long nowHour) throws SQLException {
        ZoneId z = zone.get();
        LocalDate today = Buckets.day(nowHour, z);
        if (today.equals(lastSinkAlert)) {
            return null;
        }
        int days = cfg.baselineDays + cfg.actDays + 1;
        List<FlowSummary.FlowRow> rows = WatchStore.flows(c, Currency.MONEY, Buckets.firstHour(today.minusDays(days), z));
        Map<LocalDate, FlowSummary.Totals> daily = FlowSummary.daily(rows, FlowSummary.transitGroups(cfg.transitGroups), z);
        InflationRisk.Risk risk = InflationRisk.evaluate(daily, today, cfg.baselineDays, cfg.watchBelowPercent,
                cfg.actBelowPercent, cfg.actDays, cfg.riskMinDays);
        if (risk.level() != InflationRisk.Level.ACT) {
            return null;
        }
        lastSinkAlert = today;
        long ratio = Double.isNaN(risk.ratio()) ? 0 : Math.round(risk.ratio() * 100);
        AlertDraft draft = new AlertDraft(Rule.SINK_RATIO, AlertDraft.Severity.HIGH, Currency.MONEY, null, null, ratio,
                Math.round(cfg.actBelowPercent * 100), risk.daysBelowAct(), nowHour, List.of());
        return WatchStore.insertAlert(c, draft, now);
    }

    @FunctionalInterface
    private interface Work<T> {
        T run() throws SQLException;
    }

    private static <T> T inTransaction(Connection c, Work<T> work) throws SQLException {
        boolean auto = c.getAutoCommit();
        c.setAutoCommit(false);
        try {
            T result = work.run();
            if (result == null) {
                c.rollback();
            } else {
                c.commit();
            }
            return result;
        } catch (SQLException | RuntimeException e) {
            c.rollback();
            throw e;
        } finally {
            c.setAutoCommit(auto);
        }
    }
}
