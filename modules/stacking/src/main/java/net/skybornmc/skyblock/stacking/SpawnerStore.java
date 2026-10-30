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

    /** Removes the row whatever it holds. Only for tests and staff tools: a lost block is {@link #freeze frozen}, not deleted. */
    boolean deleteAny(Connection c, String world, int x, int y, int z) throws SQLException {
        return Sql.update(c, DELETE_ANY, world, x, y, z) == 1;
    }

    /** A frozen spawner row ({@code stacking_spawners_frozen}). */
    record Frozen(long id, Row row, String reason, String state, long frozenAt) {
    }

    static final String FROZEN = "frozen";

    private static final String SELECT_ONE = SELECT_ALL + " WHERE world = ? AND x = ? AND y = ? AND z = ?";
    private static final String INSERT_FROZEN = "INSERT INTO stacking_spawners_frozen (world, x, y, z, entity_type, amount, island_id, "
            + "placed_by, placed_at, reason, state, frozen_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    /**
     * SECFIX1 F-07 / R3-13: the block of this row is gone. The row moves to {@code stacking_spawners_frozen} (state
     * {@code frozen}) in one transaction (pass a transaction connection): it never spawns, can't be taken out (no dupe)
     * and is never silently lost. Returns the frozen copy, or empty when there was no row any more.
     */
    java.util.Optional<Frozen> freeze(Connection c, String world, int x, int y, int z, String reason, long now) throws SQLException {
        java.util.Optional<Row> found = Sql.queryOne(c, SELECT_ONE, rs -> new Row(rs.getString("world"), rs.getInt("x"), rs.getInt("y"),
                rs.getInt("z"), rs.getString("entity_type"), rs.getInt("amount"), Sql.uuid(rs, "island_id"), Sql.uuid(rs, "placed_by"),
                rs.getLong("placed_at")), world, x, y, z);
        if (found.isEmpty()) {
            return java.util.Optional.empty();
        }
        Row r = found.get();
        if (Sql.update(c, DELETE, r.world(), r.x(), r.y(), r.z(), r.amount(), r.type()) != 1) {
            return java.util.Optional.empty(); // changed meanwhile: the caller tries again on the next spawn tick
        }
        String why = reason == null ? "" : reason.length() > 32 ? reason.substring(0, 32) : reason;
        long id = Sql.insertReturningId(c, INSERT_FROZEN, r.world(), r.x(), r.y(), r.z(), r.type(), r.amount(), r.island(), r.placedBy(),
                r.placedAt(), why, FROZEN, now);
        return java.util.Optional.of(new Frozen(id, r, why, FROZEN, now));
    }

    /** Frozen spawners waiting for staff, oldest first (tests and staff tools). */
    List<Frozen> frozen(Connection c, int limit) throws SQLException {
        return Sql.query(c, "SELECT id, world, x, y, z, entity_type, amount, island_id, placed_by, placed_at, reason, state, frozen_at "
                        + "FROM stacking_spawners_frozen WHERE state = ? ORDER BY id LIMIT " + Math.max(1, limit),
                rs -> new Frozen(rs.getLong("id"), new Row(rs.getString("world"), rs.getInt("x"), rs.getInt("y"), rs.getInt("z"),
                        rs.getString("entity_type"), rs.getInt("amount"), Sql.uuid(rs, "island_id"), Sql.uuid(rs, "placed_by"),
                        rs.getLong("placed_at")), rs.getString("reason"), rs.getString("state"), rs.getLong("frozen_at")), FROZEN);
    }
}
