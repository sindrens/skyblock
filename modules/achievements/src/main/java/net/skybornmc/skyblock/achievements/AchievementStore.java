package net.skybornmc.skyblock.achievements;

import net.skybornmc.skyblock.common.db.Sql;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** SQL of the achievements tables (same statements on MariaDB and H2 in MySQL mode). Callers run it off the main thread. */
final class AchievementStore {

    record Loaded(Map<Metric, Long> values, Map<String, Integer> unlocks) {
    }

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
}
