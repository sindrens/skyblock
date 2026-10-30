package net.skybornmc.skyblock.islands.stats;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.Sql;
import net.skybornmc.skyblock.common.island.IslandStat;
import net.skybornmc.skyblock.common.island.IslandStatsService.Board;
import net.skybornmc.skyblock.common.island.IslandStatsService.Period;
import net.skybornmc.skyblock.common.island.IslandStatsService.TopEntry;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Island stats in the database (M2.6, tables of {@code V010__island_stats.sql}): {@code island_stats_daily},
 * {@code island_stats_weekly} and {@code island_stats_total}. Every flush adds to the counters with one batched upsert
 * per table inside one transaction.
 */
public final class StatsStore implements StatsStorage {

    static final String UPSERT_DAILY = "INSERT INTO island_stats_daily (island_id, member_key, stat_key, bucket_day, amount, updated_at) "
            + "VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE amount = amount + VALUES(amount), updated_at = VALUES(updated_at)";
    static final String UPSERT_WEEKLY = "INSERT INTO island_stats_weekly (island_id, member_key, stat_key, bucket_week, amount, updated_at) "
            + "VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE amount = amount + VALUES(amount), updated_at = VALUES(updated_at)";
    static final String UPSERT_TOTAL = "INSERT INTO island_stats_total (island_id, member_key, stat_key, amount, updated_at) "
            + "VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE amount = amount + VALUES(amount), updated_at = VALUES(updated_at)";

    private final Database db;

    public StatsStore(Database db) {
        this.db = db;
    }

    @Override
    public CompletableFuture<Integer> write(StatsBatch batch) {
        if (batch.isEmpty()) {
            return CompletableFuture.completedFuture(0);
        }
        return db.transaction(c -> write(c, batch, System.currentTimeMillis()));
    }

    @Override
    public int writeBlocking(StatsBatch batch) {
        if (batch.isEmpty()) {
            return 0;
        }
        return db.blockingTransaction(c -> write(c, batch, System.currentTimeMillis()));
    }

    static int write(Connection c, StatsBatch batch, long now) throws SQLException {
        List<Object[]> daily = new ArrayList<>(batch.daily().size());
        for (Map.Entry<StatKey, Long> e : batch.daily().entrySet()) {
            StatKey k = e.getKey();
            daily.add(new Object[]{k.island(), k.member(), k.stat().key(), k.day(), e.getValue(), now});
        }
        List<Object[]> weekly = new ArrayList<>(batch.weekly().size());
        for (Map.Entry<StatsBatch.WeekKey, Long> e : batch.weekly().entrySet()) {
            StatsBatch.WeekKey k = e.getKey();
            weekly.add(new Object[]{k.island(), k.member(), k.stat().key(), k.week(), e.getValue(), now});
        }
        List<Object[]> totals = new ArrayList<>(batch.totals().size());
        for (Map.Entry<StatsBatch.TotalKey, Long> e : batch.totals().entrySet()) {
            StatsBatch.TotalKey k = e.getKey();
            totals.add(new Object[]{k.island(), k.member(), k.stat().key(), e.getValue(), now});
        }
        Sql.batch(c, UPSERT_DAILY, daily);
        Sql.batch(c, UPSERT_WEEKLY, weekly);
        Sql.batch(c, UPSERT_TOTAL, totals);
        return batch.rows();
    }

    @Override
    public CompletableFuture<Integer> reset(UUID island) {
        return db.transaction(c -> Sql.update(c, "DELETE FROM island_stats_daily WHERE island_id = ?", island)
                + Sql.update(c, "DELETE FROM island_stats_weekly WHERE island_id = ?", island)
                + Sql.update(c, "DELETE FROM island_stats_total WHERE island_id = ?", island));
    }

    @Override
    public CompletableFuture<Integer> purge(int minDay, int minWeek) {
        return db.async(c -> Sql.update(c, "DELETE FROM island_stats_daily WHERE bucket_day < ?", minDay)
                + Sql.update(c, "DELETE FROM island_stats_weekly WHERE bucket_week < ?", minWeek));
    }

    @Override
    public CompletableFuture<IslandRows> load(UUID island, int today) {
        int thisWeek = StatsClock.weekStart(today);
        return db.async(c -> new IslandRows(
                rows(c, "SELECT member_key, stat_key, 0 AS bucket, amount FROM island_stats_total WHERE island_id = ?", island),
                rows(c, "SELECT member_key, stat_key, bucket_day AS bucket, amount FROM island_stats_daily "
                        + "WHERE island_id = ? AND bucket_day = ?", island, today),
                rows(c, "SELECT member_key, stat_key, bucket_week AS bucket, amount FROM island_stats_weekly "
                        + "WHERE island_id = ? AND bucket_week IN (?, ?)", island, thisWeek, thisWeek - 7)));
    }

    private static List<SnapshotBuilder.Row> rows(Connection c, String sql, Object... params) throws SQLException {
        List<Optional<SnapshotBuilder.Row>> raw = Sql.query(c, sql, StatsStore::row, params);
        List<SnapshotBuilder.Row> out = new ArrayList<>(raw.size());
        raw.forEach(row -> row.ifPresent(out::add));
        return out;
    }

    /** A stored row; rows of a category this version doesn't know (a newer/removed key) are skipped. */
    private static Optional<SnapshotBuilder.Row> row(ResultSet rs) throws SQLException {
        Optional<IslandStat> stat = IslandStat.byKey(rs.getString("stat_key"));
        if (stat.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new SnapshotBuilder.Row(Sql.uuid(rs, "member_key"), stat.get(), rs.getInt("bucket"), rs.getLong("amount")));
    }

    @Override
    public CompletableFuture<List<TopEntry>> top(IslandStat stat, Period period, Board board, int today, int limit) {
        int thisWeek = StatsClock.weekStart(today);
        String source;
        Object bucket;
        switch (period) {
            case TODAY -> {
                source = "island_stats_daily s";
                bucket = "s.bucket_day = ?";
            }
            case THIS_WEEK, LAST_WEEK -> {
                source = "island_stats_weekly s";
                bucket = "s.bucket_week = ?";
            }
            default -> {
                source = "island_stats_total s";
                bucket = null;
            }
        }
        StringBuilder sql = new StringBuilder("SELECT s.island_id, SUM(s.amount) AS total FROM ").append(source)
                .append(" JOIN islands i ON i.id = s.island_id WHERE s.stat_key = ? AND i.deleted_at IS NULL");
        List<Object> params = new ArrayList<>();
        params.add(stat.key());
        if (bucket != null) {
            sql.append(" AND ").append(bucket);
            params.add(switch (period) {
                case TODAY -> today;
                case THIS_WEEK -> thisWeek;
                default -> thisWeek - 7;
            });
        }
        if (board != Board.ALL) {
            sql.append(" AND i.type = ?");
            params.add(board == Board.TEAM ? "team" : "solo");
        }
        sql.append(" GROUP BY s.island_id ORDER BY total DESC, s.island_id LIMIT ?");
        params.add(Math.max(1, Math.min(1000, limit)));
        String query = sql.toString();
        return db.async(c -> Sql.query(c, query, rs -> new TopEntry(Sql.uuid(rs, "island_id"), rs.getLong("total")), params.toArray()));
    }
}
