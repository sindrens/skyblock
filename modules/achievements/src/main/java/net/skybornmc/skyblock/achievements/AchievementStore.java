package net.skybornmc.skyblock.achievements;

import net.skybornmc.skyblock.common.db.Sql;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** SQL of the achievements tables (same statements on MariaDB and H2 in MySQL mode). Callers run it off the main thread. */
final class AchievementStore {

    record Loaded(Map<Metric, Long> values, Map<String, Integer> unlocks) {
    }

    /** Every island's stored progress and unlocks (AC3, loaded once at start). */
    record IslandsLoaded(Map<UUID, Map<IslandMetric, Long>> values, Map<UUID, Map<String, Integer>> unlocks) {
    }

    /** Longest stored title list (the column is VARCHAR(1024)). */
    static final int TITLES_LENGTH = 1024;
    private static final String ISLAND_ADD = "UPDATE achievement_island_progress SET amount = amount + ?, updated_at = ? WHERE island_id = ? AND metric = ?";
    private static final String ISLAND_MAX = "UPDATE achievement_island_progress SET amount = GREATEST(amount, ?), updated_at = ? WHERE island_id = ? AND metric = ?";
    private static final String ISLAND_INSERT = "INSERT INTO achievement_island_progress (island_id, metric, amount, updated_at) VALUES (?, ?, ?, ?)";

    private static final String ADD = "UPDATE achievement_progress SET amount = amount + ?, updated_at = ? WHERE profile_key = ? AND metric = ?";
    private static final String MAX = "UPDATE achievement_progress SET amount = GREATEST(amount, ?), updated_at = ? WHERE profile_key = ? AND metric = ?";
    private static final String INSERT = "INSERT INTO achievement_progress (profile_key, metric, amount, updated_at) VALUES (?, ?, ?, ?)";
    private static final String INSERT_UNLOCK = "INSERT INTO achievement_unlocks (profile_key, achievement, tier, owner_uuid, unlocked_at) "
            + "VALUES (?, ?, ?, ?, ?)";

    /** Everything of one profile. Unknown metric keys (removed in a later version) are ignored. */
    Loaded load(Connection c, UUID profileKey) throws SQLException {
        Map<Metric, Long> values = new EnumMap<>(Metric.class);
        for (Object[] row : Sql.query(c, "SELECT metric, amount FROM achievement_progress WHERE profile_key = ?",
                rs -> new Object[]{rs.getString("metric"), rs.getLong("amount")}, profileKey)) {
            Metric.byKey((String) row[0]).ifPresent(m -> values.put(m, (Long) row[1]));
        }
        Map<String, Integer> unlocks = new HashMap<>();
        for (Object[] row : Sql.query(c, "SELECT achievement, tier FROM achievement_unlocks WHERE profile_key = ?",
                rs -> new Object[]{rs.getString("achievement"), rs.getInt("tier")}, profileKey)) {
            unlocks.merge((String) row[0], (Integer) row[1], Math::max);
        }
        return new Loaded(values, unlocks);
    }

    /** Writes one profile's rows: counters as deltas, gauges as the highest value. */
    void save(Connection c, UUID profileKey, List<ProfileProgress.Row> rows, long now) throws SQLException {
        for (ProfileProgress.Row row : rows) {
            String update = row.metric().counter() ? ADD : MAX;
            if (Sql.update(c, update, row.value(), now, profileKey, row.metric().key()) == 0) {
                Sql.update(c, INSERT, profileKey, row.metric().key(), row.value(), now);
            }
        }
    }

    /** Stores one tier unlock. Returns false if this profile already had it (the primary key refused it). */
    boolean unlock(Connection c, UUID profileKey, String achievement, int tier, UUID owner, long now) throws SQLException {
        try {
            Sql.update(c, INSERT_UNLOCK, profileKey, achievement, tier, owner, now);
            return true;
        } catch (SQLException e) {
            if (e.getErrorCode() == 1062 || "23505".equals(e.getSQLState())) {
                return false;
            }
            throw e;
        }
    }

    /** Removes every row of a deleted profile (titles stay on the account). Returns the rows removed. */
    int delete(Connection c, UUID profileKey) throws SQLException {
        return Sql.update(c, "DELETE FROM achievement_progress WHERE profile_key = ?", profileKey)
                + Sql.update(c, "DELETE FROM achievement_unlocks WHERE profile_key = ?", profileKey);
    }

    // ------------------------------------------------------------------ islands (AC3)

    /** Every island's rows. Unknown metric keys are ignored. */
    IslandsLoaded loadIslands(Connection c) throws SQLException {
        Map<UUID, Map<IslandMetric, Long>> values = new HashMap<>();
        for (Object[] row : Sql.query(c, "SELECT island_id, metric, amount FROM achievement_island_progress",
                rs -> new Object[]{Sql.uuid(rs, "island_id"), rs.getString("metric"), rs.getLong("amount")})) {
            IslandMetric.byKey((String) row[1]).ifPresent(m -> values.computeIfAbsent((UUID) row[0], k -> new EnumMap<>(IslandMetric.class))
                    .put(m, (Long) row[2]));
        }
        Map<UUID, Map<String, Integer>> unlocks = new HashMap<>();
        for (Object[] row : Sql.query(c, "SELECT island_id, achievement, tier FROM achievement_island_unlocks",
                rs -> new Object[]{Sql.uuid(rs, "island_id"), rs.getString("achievement"), rs.getInt("tier")})) {
            unlocks.computeIfAbsent((UUID) row[0], k -> new HashMap<>()).merge((String) row[1], (Integer) row[2], Math::max);
        }
        return new IslandsLoaded(values, unlocks);
    }

    void saveIsland(Connection c, UUID islandId, List<IslandProgress.Row> rows, long now) throws SQLException {
        for (IslandProgress.Row row : rows) {
            String update = row.metric().counter() ? ISLAND_ADD : ISLAND_MAX;
            if (Sql.update(c, update, row.value(), now, islandId, row.metric().key()) == 0) {
                Sql.update(c, ISLAND_INSERT, islandId, row.metric().key(), row.value(), now);
            }
        }
    }

    /** Stores one island tier. False if the island already had it. */
    boolean unlockIsland(Connection c, UUID islandId, String achievement, int tier, long now) throws SQLException {
        try {
            Sql.update(c, "INSERT INTO achievement_island_unlocks (island_id, achievement, tier, unlocked_at) VALUES (?, ?, ?, ?)",
                    islandId, achievement, tier, now);
            return true;
        } catch (SQLException e) {
            if (e.getErrorCode() == 1062 || "23505".equals(e.getSQLState())) {
                return false;
            }
            throw e;
        }
    }

    /** Removes every row of a removed island. Returns the rows removed. */
    int deleteIsland(Connection c, UUID islandId) throws SQLException {
        return Sql.update(c, "DELETE FROM achievement_island_progress WHERE island_id = ?", islandId)
                + Sql.update(c, "DELETE FROM achievement_island_unlocks WHERE island_id = ?", islandId);
    }

    // ------------------------------------------------------------------ season reset and history (AC4)

    List<SeasonSummary.UnlockRow> allUnlocks(Connection c) throws SQLException {
        return Sql.query(c, "SELECT profile_key, owner_uuid, achievement, tier FROM achievement_unlocks",
                rs -> new SeasonSummary.UnlockRow(Sql.uuid(rs, "profile_key"), Sql.uuid(rs, "owner_uuid"), rs.getString("achievement"),
                        rs.getInt("tier")));
    }

    List<SeasonSummary.IslandUnlockRow> allIslandUnlocks(Connection c) throws SQLException {
        return Sql.query(c, "SELECT island_id, achievement, tier FROM achievement_island_unlocks",
                rs -> new SeasonSummary.IslandUnlockRow(Sql.uuid(rs, "island_id"), rs.getString("achievement"), rs.getInt("tier")));
    }

    /** True when a summary of this season was already written (the reset refuses to run twice). */
    boolean hasHistory(Connection c, int season) throws SQLException {
        return Sql.queryOne(c, "SELECT 1 FROM achievement_history WHERE season_id = ? LIMIT 1", rs -> true, season).isPresent();
    }

    void insertHistory(Connection c, List<SeasonSummary.Entry> entries, long now) throws SQLException {
        for (SeasonSummary.Entry e : entries) {
            Sql.update(c, "INSERT INTO achievement_history (season_id, profile_key, owner_uuid, tiers_done, points, completed, "
                            + "island_tiers, island_points, titles, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    e.season(), e.profileKey(), e.owner(), e.tiers(), e.points(), e.completed(), e.islandTiers(), e.islandPoints(),
                    e.titlesText(TITLES_LENGTH), now);
        }
    }

    /** Every season summary of an account (all its profiles), newest season first. */
    List<SeasonSummary.Entry> history(Connection c, UUID owner) throws SQLException {
        return Sql.query(c, "SELECT season_id, profile_key, owner_uuid, tiers_done, points, completed, island_tiers, island_points, titles "
                        + "FROM achievement_history WHERE owner_uuid = ? ORDER BY season_id DESC, created_at DESC",
                rs -> new SeasonSummary.Entry(rs.getInt("season_id"), Sql.uuid(rs, "profile_key"), Sql.uuid(rs, "owner_uuid"),
                        rs.getInt("tiers_done"), rs.getInt("points"), rs.getInt("completed"), rs.getInt("island_tiers"),
                        rs.getInt("island_points"), SeasonSummary.Entry.parseTitles(rs.getString("titles"))), owner);
    }

    /** Wipes every progress and unlock row, personal and island (history stays). Returns the rows removed. */
    int wipeSeason(Connection c) throws SQLException {
        int rows = 0;
        for (String table : new String[]{"achievement_progress", "achievement_unlocks", "achievement_island_progress", "achievement_island_unlocks"}) {
            rows += Sql.update(c, "DELETE FROM " + table);
        }
        return rows;
    }
}
