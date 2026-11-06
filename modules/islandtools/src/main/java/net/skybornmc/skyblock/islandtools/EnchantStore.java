package net.skybornmc.skyblock.islandtools;

import net.skybornmc.skyblock.common.db.Sql;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Owner 6 Nov 2026: the personal grind enchant levels ({@code island_tool_enchants}). Database thread only. */
final class EnchantStore {

    private EnchantStore() {
    }

    /** {@code pickaxe:shard_burst} → level for one profile. */
    static Map<String, Integer> load(Connection c, UUID profile) throws SQLException {
        Map<String, Integer> out = new HashMap<>();
        for (Object[] row : Sql.query(c, "SELECT kind, enchant, level FROM island_tool_enchants WHERE profile_id = ?",
                rs -> new Object[]{rs.getString(1), rs.getString(2), rs.getInt(3)}, profile)) {
            out.put(row[0] + ":" + row[1], (Integer) row[2]);
        }
        return out;
    }

    /**
     * The purchase guard: moves an enchant from {@code expected} to {@code level} only if it is still at {@code expected}
     * (a double click or a second device can't buy the same level twice). Runs in the shard payment's transaction.
     */
    static boolean set(Connection c, UUID profile, Kind kind, String enchant, int expected, int level) throws SQLException {
        long now = System.currentTimeMillis();
        if (expected == 0) {
            int have = Sql.queryOne(c, "SELECT level FROM island_tool_enchants WHERE profile_id = ? AND kind = ? AND enchant = ? FOR UPDATE",
                    rs -> rs.getInt(1), profile, kind.id(), enchant).orElse(-1);
            if (have > 0) {
                return false;
            }
            if (have == 0) {
                return Sql.update(c, "UPDATE island_tool_enchants SET level = ?, updated_at = ? WHERE profile_id = ? AND kind = ? "
                        + "AND enchant = ? AND level = 0", level, now, profile, kind.id(), enchant) == 1;
            }
            return Sql.update(c, "INSERT INTO island_tool_enchants (profile_id, kind, enchant, level, updated_at) VALUES (?, ?, ?, ?, ?)",
                    profile, kind.id(), enchant, level, now) == 1;
        }
        return Sql.update(c, "UPDATE island_tool_enchants SET level = ?, updated_at = ? WHERE profile_id = ? AND kind = ? AND enchant = ? "
                + "AND level = ?", level, now, profile, kind.id(), enchant, expected) == 1;
    }

    /** A wiped or deleted profile loses its enchants. */
    static void delete(Connection c, UUID profile) throws SQLException {
        Sql.update(c, "DELETE FROM island_tool_enchants WHERE profile_id = ?", profile);
    }
}
