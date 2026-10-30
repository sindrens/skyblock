package net.skybornmc.skyblock.progression;

import net.skybornmc.skyblock.common.db.Sql;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * SQL of the task tables (MariaDB, H2 in MySQL mode for tests). Every method takes the caller's connection, so writes can
 * share one transaction (deliveries with the item removal, completions with the rewards).
 */
final class TaskStore {

    record Row(UUID island, String counter, long amount) {
    }

    record Loaded(List<Row> rows, Map<UUID, Set<String>> done) {
    }

    static final String UPSERT = "INSERT INTO island_task_progress (island_id, season_id, task_target, amount, updated_at) "
            + "VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE amount = amount + VALUES(amount), updated_at = VALUES(updated_at)";

    Loaded load(Connection c, int season) throws SQLException {
        List<Row> rows = Sql.query(c, "SELECT island_id, task_target, amount FROM island_task_progress WHERE season_id = ?",
                rs -> new Row(Sql.uuid(rs, "island_id"), rs.getString("task_target"), rs.getLong("amount")), season);
        Map<UUID, Set<String>> done = new HashMap<>();
        for (Object[] r : Sql.query(c, "SELECT island_id, task_id FROM island_task_done WHERE season_id = ?",
                rs -> new Object[]{Sql.uuid(rs, "island_id"), rs.getString("task_id")}, season)) {
            done.computeIfAbsent((UUID) r[0], k -> new HashSet<>()).add((String) r[1]);
        }
        return new Loaded(rows, done);
    }

    /** Writes buffered deltas (call inside a transaction). */
    int write(Connection c, int season, Map<TaskBuffer.Key, Long> batch, long now) throws SQLException {
        if (batch.isEmpty()) {
            return 0;
        }
        List<Object[]> rows = new ArrayList<>();
        batch.forEach((k, v) -> rows.add(new Object[]{Sql.toBytes(k.island()), season, k.counter(), v, now}));
        Sql.batch(c, UPSERT, rows);
        return rows.size();
    }

    /** Adds one delta at once (deliveries). */
    void add(Connection c, UUID island, int season, String counter, long amount, long now) throws SQLException {
        Sql.update(c, UPSERT, Sql.toBytes(island), season, counter, amount, now);
    }

    long amount(Connection c, UUID island, int season, String counter) throws SQLException {
        return Sql.queryOne(c, "SELECT amount FROM island_task_progress WHERE island_id = ? AND season_id = ? AND task_target = ?",
                rs -> rs.getLong(1), Sql.toBytes(island), season, counter).orElse(0L);
    }

    /**
     * Marks a task done. Returns false if it already was (then nothing changes). Used as the economy guard, so it runs in
     * the same transaction as the rewards; the primary key stops two concurrent inserts.
     */
    boolean markDone(Connection c, UUID island, int season, String taskId, UUID by, long now) throws SQLException {
        if (Sql.queryOne(c, "SELECT task_id FROM island_task_done WHERE island_id = ? AND season_id = ? AND task_id = ?",
                rs -> rs.getString(1), Sql.toBytes(island), season, taskId).isPresent()) {
            return false;
        }
        Sql.update(c, "INSERT INTO island_task_done (island_id, season_id, task_id, done_by, done_at) VALUES (?, ?, ?, ?, ?)",
                Sql.toBytes(island), season, taskId, by == null ? null : Sql.toBytes(by), now);
        return true;
    }
}
