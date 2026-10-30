package net.skybornmc.skyblock.progression;

import net.skybornmc.skyblock.common.db.Sql;
import net.skybornmc.skyblock.common.progression.Pillar;

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
 * SQL of the pillar tables (MariaDB, H2 in MySQL mode for tests). Every method takes the caller's connection, so writes
 * can share one transaction. XP is written as deltas ({@code xp = xp + ?}): a batch either commits whole or not at all,
 * which keeps "never counted twice" without locking.
 */
final class PillarStore {

    /** One stored pillar row. */
    record Row(UUID island, Pillar pillar, double xp, int level) {
    }

    /** Everything stored for a season. */
    record Loaded(List<Row> rows, Map<UUID, Set<Integer>> milestones) {
    }

    private static final String UPSERT_PILLAR = "INSERT INTO island_pillars (island_id, season_id, pillar, xp, pillar_level, updated_at) "
            + "VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE xp = xp + VALUES(xp), "
            + "pillar_level = GREATEST(pillar_level, VALUES(pillar_level)), updated_at = VALUES(updated_at)";

    private static final String UPSERT_WEEK = "INSERT INTO island_pillar_weekly (island_id, season_id, pillar, week_start, xp_gained, levels_gained, updated_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE xp_gained = xp_gained + VALUES(xp_gained), "
            + "levels_gained = levels_gained + VALUES(levels_gained), updated_at = VALUES(updated_at)";

    /** All pillar rows and milestones of a season (unknown pillar ids are skipped). */
    Loaded load(Connection c, int season) throws SQLException {
        List<Row> rows = new ArrayList<>();
        for (Row row : Sql.query(c, "SELECT island_id, pillar, xp, pillar_level FROM island_pillars WHERE season_id = ?", rs -> {
            Pillar pillar = Pillar.byId(rs.getString("pillar")).orElse(null);
            return pillar == null ? null : new Row(Sql.uuid(rs, "island_id"), pillar, rs.getDouble("xp"), rs.getInt("pillar_level"));
        }, season)) {
            if (row != null) {
                rows.add(row);
            }
        }
        Map<UUID, Set<Integer>> milestones = new HashMap<>();
        for (Object[] m : Sql.query(c, "SELECT island_id, milestone FROM island_pillar_milestones WHERE season_id = ?",
                rs -> new Object[]{Sql.uuid(rs, "island_id"), rs.getInt("milestone")}, season)) {
            milestones.computeIfAbsent((UUID) m[0], k -> new HashSet<>()).add((Integer) m[1]);
        }
        return new Loaded(rows, milestones);
    }

    /** Writes one drained batch (call inside a transaction). Returns the rows written. */
    int write(Connection c, int season, PillarBuffer.Batch batch, long now) throws SQLException {
        List<Object[]> pillars = new ArrayList<>();
        batch.xp().forEach((k, xp) -> pillars.add(new Object[]{Sql.toBytes(k.island()), season, k.pillar().id(), xp,
                batch.levels().getOrDefault(k, 0), now}));
        List<Object[]> weeks = new ArrayList<>();
        batch.weekly().forEach((k, w) -> weeks.add(new Object[]{Sql.toBytes(k.island()), season, k.pillar().id(), k.weekStart(),
                w.xp(), w.levels(), now}));
        if (!pillars.isEmpty()) {
            Sql.batch(c, UPSERT_PILLAR, pillars);
        }
        if (!weeks.isEmpty()) {
            Sql.batch(c, UPSERT_WEEK, weeks);
        }
        return pillars.size() + weeks.size();
    }

    /**
     * Marks a milestone reached. Returns false if it already was (then nothing changes). Used as the economy guard, so
     * it runs in the same transaction as the rewards; the primary key stops two concurrent inserts.
     */
    boolean markMilestone(Connection c, UUID island, int season, int milestone, long now) throws SQLException {
        if (Sql.queryOne(c, "SELECT milestone FROM island_pillar_milestones WHERE island_id = ? AND season_id = ? AND milestone = ?",
                rs -> rs.getInt(1), Sql.toBytes(island), season, milestone).isPresent()) {
            return false;
        }
        Sql.update(c, "INSERT INTO island_pillar_milestones (island_id, season_id, milestone, reached_at) VALUES (?, ?, ?, ?)",
                Sql.toBytes(island), season, milestone, now);
        return true;
    }

    /** Pillar levels gained per pillar in one week (for the weekly board, M5.7). */
    Map<Pillar, Integer> weeklyLevels(Connection c, UUID island, int season, int weekStart) throws SQLException {
        Map<Pillar, Integer> out = new HashMap<>();
        for (Object[] r : Sql.query(c, "SELECT pillar, levels_gained FROM island_pillar_weekly WHERE island_id = ? AND season_id = ? AND week_start = ?",
                rs -> new Object[]{rs.getString("pillar"), rs.getInt("levels_gained")}, Sql.toBytes(island), season, weekStart)) {
            Pillar.byId((String) r[0]).ifPresent(p -> out.merge(p, (Integer) r[1], Integer::sum));
        }
        return out;
    }
}
