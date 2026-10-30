package net.skybornmc.skyblock.progression;

import net.skybornmc.skyblock.common.db.Sql;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * SQL of {@code island_upgrades} (MariaDB, H2 in MySQL mode for tests). Every method takes the caller's connection, so
 * {@link #buy} can run as the guard of the island bank payment (one transaction).
 */
final class UpgradeStore {

    /** Every bought level of the season: island → upgrade id → level. */
    Map<UUID, Map<String, Integer>> load(Connection c, int season) throws SQLException {
        Map<UUID, Map<String, Integer>> out = new HashMap<>();
        for (Object[] r : Sql.query(c, "SELECT island_id, upgrade_id, upgrade_level FROM island_upgrades WHERE season_id = ?",
                rs -> new Object[]{Sql.uuid(rs, "island_id"), rs.getString("upgrade_id"), rs.getInt("upgrade_level")}, season)) {
            out.computeIfAbsent((UUID) r[0], k -> new HashMap<>()).put((String) r[1], (Integer) r[2]);
        }
        return out;
    }

    /** The bought level of one upgrade (0 = none). */
    int level(Connection c, UUID island, int season, String upgradeId) throws SQLException {
        return current(c, island, season, upgradeId).orElse(0);
    }

    private Optional<Integer> current(Connection c, UUID island, int season, String upgradeId) throws SQLException {
        return Sql.queryOne(c, "SELECT upgrade_level FROM island_upgrades WHERE island_id = ? AND season_id = ? AND upgrade_id = ? FOR UPDATE",
                rs -> rs.getInt(1), Sql.toBytes(island), season, upgradeId);
    }

    /**
     * Raises the upgrade from {@code level - 1} to {@code level}. Returns false (nothing changes) when the island does not
     * have exactly the level before, e.g. a second click or another member bought it first. Run as the payment guard:
     * the row is locked, and two concurrent first purchases collide on the primary key (one rolls back).
     */
    boolean buy(Connection c, UUID island, int season, String upgradeId, int level, UUID by, long now) throws SQLException {
        if (level < 1) {
            return false;
        }
        Optional<Integer> current = current(c, island, season, upgradeId);
        if (current.orElse(0) != level - 1) {
            return false;
        }
        if (current.isPresent()) {
            return Sql.update(c, "UPDATE island_upgrades SET upgrade_level = ?, bought_by = ?, bought_at = ? "
                            + "WHERE island_id = ? AND season_id = ? AND upgrade_id = ? AND upgrade_level = ?",
                    level, by == null ? null : Sql.toBytes(by), now, Sql.toBytes(island), season, upgradeId, level - 1) == 1;
        }
        Sql.update(c, "INSERT INTO island_upgrades (island_id, season_id, upgrade_id, upgrade_level, bought_by, bought_at) "
                + "VALUES (?, ?, ?, ?, ?, ?)", Sql.toBytes(island), season, upgradeId, level, by == null ? null : Sql.toBytes(by), now);
        return true;
    }
}
