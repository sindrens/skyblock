package net.skybornmc.skyblock.economy.admin;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.Sql;
import net.skybornmc.skyblock.common.economy.AccountRef;
import net.skybornmc.skyblock.common.economy.AccountType;
import net.skybornmc.skyblock.common.economy.Currency;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.UnaryOperator;

/**
 * SQL of the economy watch (M3.8). Everything runs on database threads. The ledger is only ever read in id order after the
 * saved cursor ({@link #rowsAfter}) or in small, indexed windows for the dashboard; the summary tables are only written by
 * {@link #apply}, which adds a batch and moves the cursor in one transaction (exactly once, also after a crash).
 */
public final class WatchStore {

    static final String JOB_LEDGER = "ledger";
    /** Characters of {@code eco_watch_alerts.ledger_ids}. */
    static final int LEDGER_IDS_CHARS = 512;

    private static final String ROW_COLUMNS = "id, created_at, currency, from_type, from_id, to_type, to_id, amount, "
            + "from_balance, to_balance, reason";
    private static final String ALERT_COLUMNS = "id, created_at, bucket_hour, rule_id, severity, currency, subject_id, "
            + "other_id, amount, expected, hit_count, ledger_ids, alert_state, reviewed_by, reviewed_at";

    private final Database db;

    public WatchStore(Database db) {
        this.db = db;
    }

    Database db() {
        return db;
    }

    // ================================================================== cursor and ledger

    /** The last processed ledger id (0 before the first run). Creates the state row if missing (autocommit). */
    static long cursor(Connection c) throws SQLException {
        Optional<Long> id = Sql.queryOne(c, "SELECT last_id FROM eco_watch_state WHERE job = ?", rs -> rs.getLong(1), JOB_LEDGER);
        if (id.isPresent()) {
            return id.get();
        }
        try {
            Sql.update(c, "INSERT INTO eco_watch_state (job, last_id, updated_at) VALUES (?, 0, ?)", JOB_LEDGER,
                    System.currentTimeMillis());
        } catch (SQLException e) {
            // another server inserted it at the same time
        }
        return Sql.queryOne(c, "SELECT last_id FROM eco_watch_state WHERE job = ?", rs -> rs.getLong(1), JOB_LEDGER).orElse(0L);
    }

    /** Up to {@code limit} ledger rows after {@code cursor}, ids ascending (uses the primary key, never a full scan). */
    static List<LedgerRow> rowsAfter(Connection c, long cursor, int limit) throws SQLException {
        return Sql.query(c, "SELECT " + ROW_COLUMNS + " FROM eco_ledger WHERE id > ? ORDER BY id LIMIT ?", WatchStore::row,
                cursor, limit);
    }

    static LedgerRow row(ResultSet rs) throws SQLException {
        return new LedgerRow(rs.getLong("id"), rs.getLong("created_at"),
                Currency.byKey(rs.getString("currency")).orElse(Currency.MONEY),
                ref(rs.getString("from_type"), Sql.uuid(rs, "from_id")), ref(rs.getString("to_type"), Sql.uuid(rs, "to_id")),
                rs.getLong("amount"), Sql.nullableLong(rs, "from_balance"), Sql.nullableLong(rs, "to_balance"),
                rs.getString("reason"));
    }

    private static AccountRef ref(String type, UUID owner) {
        if (type == null || owner == null) {
            return null;
        }
        return AccountType.byKey(type).map(t -> new AccountRef(t, owner)).orElse(null);
    }

    /** New hourly totals of the keys a batch touched (whole hour, after the batch was added). */
    public record Applied(Map<Aggregation.FlowKey, Aggregation.Sum> flows,
                          Map<Aggregation.AccountKey, Aggregation.AccountSum> accounts) {
    }

    /** Turns the new totals into alerts inside the transaction (pure, fast). */
    @FunctionalInterface
    public interface Detect {
        List<AlertDraft> apply(Applied totals);
    }

    /** Result of {@link #apply}: {@code null} when another writer moved the cursor first (nothing was written). */
    public record Outcome(Applied totals, List<WatchAlert> alerts) {
    }

    /**
     * Adds a batch to the summary tables, moves the cursor from {@code expected} to {@code next} and stores the alerts
     * {@code detect} returns, all in one transaction. Returns {@code null} (rolled back, nothing written) if the cursor is
     * no longer {@code expected}.
     */
    static Outcome apply(Connection c, long expected, long next, Aggregation batch, Detect detect, long now) throws SQLException {
        Optional<Long> locked = Sql.queryOne(c, "SELECT last_id FROM eco_watch_state WHERE job = ? FOR UPDATE",
                rs -> rs.getLong(1), JOB_LEDGER);
        if (locked.isEmpty() || locked.get() != expected) {
            return null;
        }
        Map<Aggregation.FlowKey, Aggregation.Sum> flowTotals = new LinkedHashMap<>();
        for (Map.Entry<Aggregation.FlowKey, Aggregation.Sum> e : batch.flows().entrySet()) {
            Aggregation.FlowKey k = e.getKey();
            Object[] keyParams = {k.hour(), k.currency().key(), k.reason(), k.flow().key()};
            Optional<Aggregation.Sum> old = Sql.queryOne(c, "SELECT amount_total, tx_count FROM eco_watch_flows "
                    + "WHERE bucket_hour = ? AND currency = ? AND reason = ? AND flow = ? FOR UPDATE",
                    rs -> new Aggregation.Sum(rs.getLong(1), rs.getLong(2)), keyParams);
            Aggregation.Sum total = old.orElse(Aggregation.Sum.ZERO).plus(e.getValue());
            if (old.isPresent()) {
                Sql.update(c, "UPDATE eco_watch_flows SET amount_total = ?, tx_count = ? "
                        + "WHERE bucket_hour = ? AND currency = ? AND reason = ? AND flow = ?",
                        total.amount(), total.count(), k.hour(), k.currency().key(), k.reason(), k.flow().key());
            } else {
                Sql.update(c, "INSERT INTO eco_watch_flows (bucket_hour, currency, reason, flow, amount_total, tx_count) "
                        + "VALUES (?, ?, ?, ?, ?, ?)", k.hour(), k.currency().key(), k.reason(), k.flow().key(), total.amount(),
                        total.count());
            }
            flowTotals.put(k, total);
        }
        Map<Aggregation.AccountKey, Aggregation.AccountSum> accountTotals = new LinkedHashMap<>();
        for (Map.Entry<Aggregation.AccountKey, Aggregation.AccountSum> e : batch.accounts().entrySet()) {
            Aggregation.AccountKey k = e.getKey();
            Optional<Aggregation.AccountSum> old = Sql.queryOne(c, "SELECT earned, spent, tx_count FROM eco_watch_accounts "
                    + "WHERE bucket_hour = ? AND owner_id = ? AND currency = ? FOR UPDATE",
                    rs -> new Aggregation.AccountSum(rs.getLong(1), rs.getLong(2), rs.getLong(3)), k.hour(), k.owner(),
                    k.currency().key());
            Aggregation.AccountSum total = old.orElse(Aggregation.AccountSum.ZERO).plus(e.getValue());
            if (old.isPresent()) {
                Sql.update(c, "UPDATE eco_watch_accounts SET earned = ?, spent = ?, tx_count = ? "
                        + "WHERE bucket_hour = ? AND owner_id = ? AND currency = ?", total.earned(), total.spent(), total.count(),
                        k.hour(), k.owner(), k.currency().key());
            } else {
                Sql.update(c, "INSERT INTO eco_watch_accounts (bucket_hour, owner_id, currency, earned, spent, tx_count) "
                        + "VALUES (?, ?, ?, ?, ?, ?)", k.hour(), k.owner(), k.currency().key(), total.earned(), total.spent(),
                        total.count());
            }
            accountTotals.put(k, total);
        }
        Sql.update(c, "UPDATE eco_watch_state SET last_id = ?, updated_at = ? WHERE job = ?", next, now, JOB_LEDGER);
        Applied applied = new Applied(flowTotals, accountTotals);
        List<AlertDraft> drafts = detect == null ? List.of() : detect.apply(applied);
        List<WatchAlert> alerts = new ArrayList<>(drafts.size());
        for (AlertDraft d : drafts) {
            alerts.add(insertAlert(c, d, now));
        }
        return new Outcome(applied, alerts);
    }

    static WatchAlert insertAlert(Connection c, AlertDraft d, long now) throws SQLException {
        String ids = WatchAlert.joinIds(d.ledgerIds(), LEDGER_IDS_CHARS);
        long id = Sql.insertReturningId(c, "INSERT INTO eco_watch_alerts (created_at, bucket_hour, rule_id, severity, currency, "
                        + "subject_id, other_id, amount, expected, hit_count, ledger_ids, alert_state) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                now, d.hour(), d.rule().id(), d.severity().key(), d.currency().key(), d.subject(), d.other(), d.amount(),
                d.expected(), d.hits(), ids, WatchAlert.State.OPEN.key());
        return new WatchAlert(id, now, d.hour(), d.rule(), d.severity(), d.currency(), d.subject(), d.other(), d.amount(),
                d.expected(), d.hits(), WatchAlert.parseIds(ids), WatchAlert.State.OPEN, null, null);
    }

    // ================================================================== baselines and supply

    /** A sum column as a long ({@code SUM} of BIGINT is a DECIMAL on MariaDB); saturates, 0 for NULL. */
    static long sum(ResultSet rs, int column) throws SQLException {
        BigDecimal value = rs.getBigDecimal(column);
        if (value == null) {
            return 0;
        }
        if (value.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) >= 0) {
            return Long.MAX_VALUE;
        }
        return Math.max(0, value.longValue());
    }

    /**
     * The baselines of the anomaly rules: the average earning per active profile-hour and the average money from selling
     * per hour over the last {@code days} days (current hour excluded), and the latest money supply. A baseline is 0 until
     * there are {@code minHours} hours of data.
     */
    static AnomalyDetector.Baselines baselines(Connection c, long nowHour, int days, int minHours, String sellReason)
            throws SQLException {
        long from = nowHour - days * 24L;
        long first = Sql.queryOne(c, "SELECT MIN(bucket_hour) FROM eco_watch_flows", rs -> {
            long v = rs.getLong(1);
            return rs.wasNull() ? Long.MAX_VALUE : v;
        }).orElse(Long.MAX_VALUE);
        long span = first == Long.MAX_VALUE ? 0 : nowHour - Math.max(from, first);
        Map<Currency, Long> earn = new EnumMap<>(Currency.class);
        long sell = 0;
        if (span >= minHours) {
            for (Object[] r : Sql.query(c, "SELECT currency, SUM(earned), COUNT(*) FROM eco_watch_accounts "
                            + "WHERE bucket_hour >= ? AND bucket_hour < ? AND earned > 0 GROUP BY currency",
                    rs -> new Object[]{rs.getString(1), sum(rs, 2), rs.getLong(3)}, from, nowHour)) {
                long count = (Long) r[2];
                Currency.byKey((String) r[0]).ifPresent(cur -> earn.put(cur, count <= 0 ? 0 : (Long) r[1] / count));
            }
            long sold = Sql.queryOne(c, "SELECT SUM(amount_total) FROM eco_watch_flows WHERE currency = ? AND reason = ? "
                    + "AND flow = ? AND bucket_hour >= ? AND bucket_hour < ?", rs -> sum(rs, 1), Currency.MONEY.key(), sellReason,
                    Flow.CREATED.key(), from, nowHour).orElse(0L);
            sell = sold / Math.max(1, span);
        }
        return new AnomalyDetector.Baselines(earn, sell, latestSupply(c));
    }

    /** The newest supply snapshot per currency (accounts + notes). */
    static Map<Currency, Long> latestSupply(Connection c) throws SQLException {
        Map<Currency, Long> out = new EnumMap<>(Currency.class);
        for (Object[] r : Sql.query(c, "SELECT s.currency, s.account_total, s.note_total FROM eco_watch_supply s "
                        + "WHERE s.bucket_hour = (SELECT MAX(t.bucket_hour) FROM eco_watch_supply t WHERE t.currency = s.currency)",
                rs -> new Object[]{rs.getString(1), rs.getLong(2), rs.getLong(3)})) {
            Currency.byKey((String) r[0]).ifPresent(cur -> out.put(cur, Aggregation.add((Long) r[1], (Long) r[2])));
        }
        return out;
    }

    /** The money supply right now: every account balance plus notes not cashed yet, per currency ({account, notes}). */
    static Map<Currency, long[]> liveSupply(Connection c) throws SQLException {
        Map<Currency, long[]> out = new EnumMap<>(Currency.class);
        for (Currency cur : Currency.values()) {
            out.put(cur, new long[2]);
        }
        for (Object[] r : Sql.query(c, "SELECT currency, SUM(balance) FROM eco_accounts GROUP BY currency",
                rs -> new Object[]{rs.getString(1), sum(rs, 2)})) {
            Currency.byKey((String) r[0]).ifPresent(cur -> out.get(cur)[0] = (Long) r[1]);
        }
        for (Object[] r : Sql.query(c, "SELECT currency, SUM(amount) FROM money_notes WHERE status = ? GROUP BY currency",
                rs -> new Object[]{rs.getString(1), sum(rs, 2)}, "active")) {
            Currency.byKey((String) r[0]).ifPresent(cur -> out.get(cur)[1] = (Long) r[1]);
        }
        return out;
    }

    /** Saves a supply snapshot for {@code hour} (replaces one taken earlier in the same hour). */
    static void saveSupply(Connection c, long hour, Map<Currency, long[]> supply, long now) throws SQLException {
        for (Map.Entry<Currency, long[]> e : supply.entrySet()) {
            String cur = e.getKey().key();
            int changed = Sql.update(c, "UPDATE eco_watch_supply SET account_total = ?, note_total = ?, taken_at = ? "
                    + "WHERE bucket_hour = ? AND currency = ?", e.getValue()[0], e.getValue()[1], now, hour, cur);
            if (changed == 0) {
                Sql.update(c, "INSERT INTO eco_watch_supply (bucket_hour, currency, account_total, note_total, taken_at) "
                        + "VALUES (?, ?, ?, ?, ?)", hour, cur, e.getValue()[0], e.getValue()[1], now);
            }
        }
    }

    /** The supply snapshot at or before {@code hour} (accounts + notes), or -1 if none. */
    static long supplyAt(Connection c, Currency currency, long hour) throws SQLException {
        return Sql.queryOne(c, "SELECT account_total, note_total FROM eco_watch_supply WHERE currency = ? AND bucket_hour <= ? "
                        + "ORDER BY bucket_hour DESC LIMIT 1", rs -> Aggregation.add(rs.getLong(1), rs.getLong(2)),
                currency.key(), hour).orElse(-1L);
    }

    // ================================================================== dashboard reads

    /** Hourly flows of one currency from {@code fromHour} on. */
    static List<FlowSummary.FlowRow> flows(Connection c, Currency currency, long fromHour) throws SQLException {
        return Sql.query(c, "SELECT bucket_hour, reason, flow, amount_total, tx_count FROM eco_watch_flows "
                        + "WHERE currency = ? AND bucket_hour >= ? ORDER BY bucket_hour",
                rs -> new FlowSummary.FlowRow(rs.getLong(1), rs.getString(2), Flow.byKey(rs.getString(3)).orElse(Flow.MOVED),
                        rs.getLong(4), rs.getLong(5)), currency.key(), fromHour);
    }

    /** One profile in the top earners list. */
    public record TopAccount(UUID profile, long earned, long spent, long count) {
    }

    /** Profiles that earned the most since {@code fromHour}. */
    static List<TopAccount> topEarners(Connection c, Currency currency, long fromHour, int limit) throws SQLException {
        return Sql.query(c, "SELECT owner_id, SUM(earned) AS e, SUM(spent) AS s, SUM(tx_count) AS n FROM eco_watch_accounts "
                        + "WHERE currency = ? AND bucket_hour >= ? GROUP BY owner_id ORDER BY e DESC LIMIT ?",
                rs -> new TopAccount(Sql.uuid(rs, "owner_id"), sum(rs, 2), sum(rs, 3), sum(rs, 4)), currency.key(), fromHour, limit);
    }

    /** The biggest ledger rows of a currency in a time window, optionally of one reason (indexed by currency + time). */
    static List<LedgerRow> biggest(Connection c, Currency currency, long fromMs, long toMs, Collection<String> reasons, int limit)
            throws SQLException {
        List<Object> params = new ArrayList<>(List.of(currency.key(), fromMs, toMs));
        StringBuilder where = new StringBuilder("currency = ? AND created_at >= ? AND created_at < ?");
        if (reasons != null && !reasons.isEmpty()) {
            where.append(" AND reason IN (").append(String.join(", ", java.util.Collections.nCopies(reasons.size(), "?"))).append(")");
            params.addAll(reasons);
        }
        params.add(limit);
        return Sql.query(c, "SELECT " + ROW_COLUMNS + " FROM eco_ledger WHERE " + where + " ORDER BY amount DESC, id DESC LIMIT ?",
                WatchStore::row, params.toArray());
    }

    /** Ledger rows by id, ids ascending. */
    static List<LedgerRow> byIds(Connection c, Collection<Long> ids) throws SQLException {
        if (ids.isEmpty()) {
            return List.of();
        }
        List<Long> list = new ArrayList<>(new LinkedHashSet<>(ids));
        return Sql.query(c, "SELECT " + ROW_COLUMNS + " FROM eco_ledger WHERE id IN ("
                + String.join(", ", java.util.Collections.nCopies(list.size(), "?")) + ") ORDER BY id", WatchStore::row, list.toArray());
    }

    /** What an account received in a time window, biggest first (index on to_id). */
    static List<LedgerRow> incoming(Connection c, UUID profile, Currency currency, long fromMs, long toMs, int limit)
            throws SQLException {
        return Sql.query(c, "SELECT " + ROW_COLUMNS + " FROM eco_ledger WHERE to_type = ? AND to_id = ? AND currency = ? "
                        + "AND created_at >= ? AND created_at < ? ORDER BY amount DESC, id DESC LIMIT ?", WatchStore::row,
                AccountType.PLAYER.key(), profile, currency.key(), fromMs, toMs, limit);
    }

    /**
     * Player names of profile keys: {@code owners} maps a profile key to its player; the name is the player's current
     * name. Keys without a known player are left out.
     */
    static Map<UUID, String> names(Connection c, Collection<UUID> profiles, UnaryOperator<UUID> owners) throws SQLException {
        Map<UUID, String> byPlayer = new HashMap<>();
        Map<UUID, String> out = new HashMap<>();
        for (UUID key : new LinkedHashSet<>(profiles)) {
            if (key == null) {
                continue;
            }
            UUID player = owners.apply(key);
            if (player == null) {
                continue;
            }
            String name = byPlayer.containsKey(player) ? byPlayer.get(player)
                    : Sql.queryOne(c, "SELECT name FROM players WHERE uuid = ?", rs -> rs.getString(1), player).orElse(null);
            byPlayer.put(player, name);
            if (name != null) {
                out.put(key, name);
            }
        }
        return out;
    }

    // ================================================================== alerts

    static WatchAlert alert(ResultSet rs) throws SQLException {
        return new WatchAlert(rs.getLong("id"), rs.getLong("created_at"), rs.getLong("bucket_hour"),
                Rule.byId(rs.getString("rule_id")).orElse(Rule.EARN_RATE), AlertDraft.Severity.byKey(rs.getString("severity")),
                Currency.byKey(rs.getString("currency")).orElse(Currency.MONEY), Sql.uuid(rs, "subject_id"),
                Sql.uuid(rs, "other_id"), rs.getLong("amount"), rs.getLong("expected"), rs.getInt("hit_count"),
                WatchAlert.parseIds(rs.getString("ledger_ids")), WatchAlert.State.byKey(rs.getString("alert_state")),
                rs.getString("reviewed_by"), Sql.nullableLong(rs, "reviewed_at"));
    }

    /** One page of alerts, newest first; {@code openOnly} = only open ones. */
    public record AlertPage(List<WatchAlert> alerts, long total) {
    }

    static AlertPage alerts(Connection c, boolean openOnly, int offset, int limit) throws SQLException {
        String where = openOnly ? " WHERE alert_state = ?" : "";
        Object[] params = openOnly ? new Object[]{WatchAlert.State.OPEN.key()} : new Object[0];
        long total = Sql.queryOne(c, "SELECT COUNT(*) FROM eco_watch_alerts" + where, rs -> rs.getLong(1), params).orElse(0L);
        List<Object> page = new ArrayList<>(List.of(params));
        page.add(Math.max(1, limit));
        page.add(Math.max(0, offset));
        List<WatchAlert> rows = Sql.query(c, "SELECT " + ALERT_COLUMNS + " FROM eco_watch_alerts" + where
                + " ORDER BY id DESC LIMIT ? OFFSET ?", WatchStore::alert, page.toArray());
        return new AlertPage(rows, total);
    }

    static Optional<WatchAlert> alert(Connection c, long id) throws SQLException {
        return Sql.queryOne(c, "SELECT " + ALERT_COLUMNS + " FROM eco_watch_alerts WHERE id = ?", WatchStore::alert, id);
    }

    /** Number of open alerts. */
    static long openCount(Connection c) throws SQLException {
        return Sql.queryOne(c, "SELECT COUNT(*) FROM eco_watch_alerts WHERE alert_state = ?", rs -> rs.getLong(1),
                WatchAlert.State.OPEN.key()).orElse(0L);
    }

    /** Newest alert ids (tab completion of {@code /eco admin}). */
    static List<Long> recentIds(Connection c, int limit) throws SQLException {
        return Sql.query(c, "SELECT id FROM eco_watch_alerts ORDER BY id DESC LIMIT ?", rs -> rs.getLong(1), limit);
    }

    /**
     * Sets an alert's state (reviewed/dismissed/open again). Returns the state before, or empty if the alert does not exist
     * or already had that state (nothing changed).
     */
    static Optional<WatchAlert.State> setState(Connection c, long id, WatchAlert.State state, String by, long now)
            throws SQLException {
        Optional<WatchAlert.State> before = Sql.queryOne(c, "SELECT alert_state FROM eco_watch_alerts WHERE id = ?",
                rs -> WatchAlert.State.byKey(rs.getString(1)), id);
        if (before.isEmpty() || before.get() == state) {
            return Optional.empty();
        }
        int changed = Sql.update(c, "UPDATE eco_watch_alerts SET alert_state = ?, reviewed_by = ?, reviewed_at = ? "
                        + "WHERE id = ? AND alert_state = ?", state.key(), state == WatchAlert.State.OPEN ? null : by,
                state == WatchAlert.State.OPEN ? null : now, id, before.get().key());
        return changed == 1 ? before : Optional.empty();
    }

    // ================================================================== housekeeping

    /** Deletes old summary rows and closed alerts. Returns the number of rows removed. */
    static int purge(Connection c, long nowHour, WatchConfig cfg, long now) throws SQLException {
        int removed = 0;
        removed += Sql.update(c, "DELETE FROM eco_watch_flows WHERE bucket_hour < ?", nowHour - cfg.flowDays * 24L);
        removed += Sql.update(c, "DELETE FROM eco_watch_accounts WHERE bucket_hour < ?", nowHour - cfg.accountDays * 24L);
        removed += Sql.update(c, "DELETE FROM eco_watch_supply WHERE bucket_hour < ?", nowHour - cfg.supplyDays * 24L);
        removed += Sql.update(c, "DELETE FROM eco_watch_alerts WHERE alert_state <> ? AND created_at < ?",
                WatchAlert.State.OPEN.key(), now - cfg.alertDays * 86_400_000L);
        return removed;
    }

    // ================================================================== async wrappers

    <T> CompletableFuture<T> async(net.skybornmc.skyblock.common.db.Sql.SqlFunction<T> work) {
        return db.async(work);
    }
}
