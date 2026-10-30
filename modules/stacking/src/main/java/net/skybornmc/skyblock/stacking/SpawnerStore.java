package net.skybornmc.skyblock.stacking;

import net.skybornmc.skyblock.common.db.Sql;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

/**
 * SQL of {@code stacking_spawners} ({@code db/stacking/V001__spawners.sql}). Every change is conditional on the amount
 * the caller expects, so a change based on an old value fails instead of overwriting (no dupes, no lost spawners).
 * Pass a connection from {@code Database}.
 */
final class SpawnerStore {

    /** One stacked spawner block. */
    record Row(String world, int x, int y, int z, String type, int amount, UUID island, UUID placedBy, long placedAt) {
    }

    private static final String SELECT_ALL = "SELECT world, x, y, z, entity_type, amount, island_id, placed_by, placed_at FROM stacking_spawners";
    private static final String INSERT = "INSERT INTO stacking_spawners (world, x, y, z, entity_type, amount, island_id, placed_by, placed_at, updated_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String EXISTS = "SELECT 1 FROM stacking_spawners WHERE world = ? AND x = ? AND y = ? AND z = ?";
    private static final String CHANGE = "UPDATE stacking_spawners SET amount = ?, updated_at = ? "
            + "WHERE world = ? AND x = ? AND y = ? AND z = ? AND amount = ? AND entity_type = ?";
    private static final String DELETE = "DELETE FROM stacking_spawners WHERE world = ? AND x = ? AND y = ? AND z = ? AND amount = ? AND entity_type = ?";
    private static final String DELETE_ANY = "DELETE FROM stacking_spawners WHERE world = ? AND x = ? AND y = ? AND z = ?";

    List<Row> loadAll(Connection c) throws SQLException {
        return Sql.query(c, SELECT_ALL, rs -> new Row(rs.getString("world"), rs.getInt("x"), rs.getInt("y"), rs.getInt("z"),
                rs.getString("entity_type"), rs.getInt("amount"), Sql.uuid(rs, "island_id"), Sql.uuid(rs, "placed_by"),
                rs.getLong("placed_at")));
    }

    /** Inserts a new spawner block; false if the position already has one. */
    boolean insert(Connection c, Row row, long now) throws SQLException {
        if (Sql.queryOne(c, EXISTS, rs -> 1, row.world(), row.x(), row.y(), row.z()).isPresent()) {
            return false;
        }
        try {
            return Sql.update(c, INSERT, row.world(), row.x(), row.y(), row.z(), row.type(), row.amount(), row.island(),
                    row.placedBy(), row.placedAt(), now) == 1;
        } catch (SQLException e) {
            // A primary-key race (two inserts at once) counts as "already there".
            if (Sql.queryOne(c, EXISTS, rs -> 1, row.world(), row.x(), row.y(), row.z()).isPresent()) {
                return false;
            }
            throw e;
        }
    }

    /**
     * Changes the amount from {@code expected} to {@code amount} (0 deletes the row). False if the stored row is not
     * exactly {@code expected} of {@code type} (someone else changed it first).
     */
    boolean change(Connection c, String world, int x, int y, int z, String type, int expected, int amount, long now) throws SQLException {
        if (amount < 0) {
            throw new IllegalArgumentException("amount below 0");
        }
        if (amount == 0) {
            return Sql.update(c, DELETE, world, x, y, z, expected, type) == 1;
        }
        return Sql.update(c, CHANGE, amount, now, world, x, y, z, expected, type) == 1;
    }

    /** Removes the row whatever it holds (the block is gone: island reset, world edit). */
    boolean deleteAny(Connection c, String world, int x, int y, int z) throws SQLException {
        return Sql.update(c, DELETE_ANY, world, x, y, z) == 1;
    }
}
