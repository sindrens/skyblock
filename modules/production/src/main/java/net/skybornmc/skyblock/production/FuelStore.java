package net.skybornmc.skyblock.production;

import net.skybornmc.skyblock.common.db.Sql;
import net.skybornmc.skyblock.common.production.FuelState;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * SQL of Island Fuel ({@code island_fuel}, {@code production_used_cells}; MariaDB, H2 in MySQL mode for tests). Every
 * method takes the caller's connection, so grants and cells run inside one transaction.
 */
final class FuelStore {

    /** One island's stored fuel. */
    record Row(UUID island, int season, long fuelMs, int tankLevel, FuelState state, long stateSince, long updatedAt) {
    }

    /** What a grant or a cell did: fuel added (ms) and the tank after it (ms). */
    record Added(long addedMs, long fuelAfterMs) {
    }

    /** A Fuel Cell did not fit into the tank: the transaction rolls back (the used-cell row too) and the item is given back. */
    static final class NoRoom extends RuntimeException {
        NoRoom() {
            super("the Fuel Cell does not fit into the tank", null, false, false);
        }
    }

    private static final String UPSERT = "INSERT INTO island_fuel (island_id, season_id, fuel_ms, tank_level, last_state, state_since, "
            + "updated_at) VALUES (?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE fuel_ms = VALUES(fuel_ms), "
            + "tank_level = VALUES(tank_level), last_state = VALUES(last_state), state_since = VALUES(state_since), "
            + "updated_at = VALUES(updated_at)";

    List<Row> loadAll(Connection c) throws SQLException {
        return Sql.query(c, "SELECT island_id, season_id, fuel_ms, tank_level, last_state, state_since, updated_at FROM island_fuel",
                rs -> new Row(Sql.uuid(rs, "island_id"), rs.getInt("season_id"), rs.getLong("fuel_ms"), rs.getInt("tank_level"),
                        FuelState.byKey(rs.getString("last_state")).orElse(FuelState.ASLEEP), rs.getLong("state_since"),
                        rs.getLong("updated_at")));
    }

    /** Writes rows as they are in memory (absolute values). */
    void save(Connection c, List<Row> rows) throws SQLException {
        if (rows.isEmpty()) {
            return;
        }
        List<Object[]> params = new ArrayList<>(rows.size());
        for (Row r : rows) {
            params.add(params(r));
        }
        Sql.batch(c, UPSERT, params);
    }

    private static Object[] params(Row r) {
        return new Object[]{Sql.toBytes(r.island()), r.season(), Math.max(0, r.fuelMs()), r.tankLevel(), r.state().key(), r.stateSince(),
                r.updatedAt()};
    }

    /**
     * A grant (AFK pool, staff): writes the memory value {@code snapshot}, then adds with one conditional, capped update
     * ({@code LEAST(fuel_ms + add, capacity)}, only while the tank is not full). Returns what was added (0 = full tank).
     */
    Added grant(Connection c, Row snapshot, long addMs, long capacityMs, long now) throws SQLException {
        Sql.update(c, UPSERT, params(snapshot));
        int changed = Sql.update(c, "UPDATE island_fuel SET fuel_ms = LEAST(fuel_ms + ?, ?), updated_at = ? WHERE island_id = ? AND fuel_ms < ?",
                addMs, capacityMs, now, Sql.toBytes(snapshot.island()), capacityMs);
        long after = fuel(c, snapshot.island());
        return new Added(changed == 0 ? 0 : Math.max(0, after - Math.max(0, snapshot.fuelMs())), after);
    }

    /** True when this Fuel Cell was used before. */
    boolean cellUsed(Connection c, UUID itemUuid) throws SQLException {
        return Sql.queryOne(c, "SELECT 1 FROM production_used_cells WHERE item_uuid = ?", rs -> rs.getInt(1), Sql.toBytes(itemUuid)).isPresent();
    }

    /**
     * A Fuel Cell: marks the cell used and adds its whole fuel with one conditional update (only when it fits). Throws
     * {@link NoRoom} when it does not fit, so the surrounding transaction rolls back and the cell stays unused.
     */
    Added useCell(Connection c, Row snapshot, UUID itemUuid, UUID profileKey, long cellMs, long capacityMs, long now) throws SQLException {
        Sql.update(c, "INSERT INTO production_used_cells (item_uuid, profile_key, island_id, cell_minutes, used_at) VALUES (?, ?, ?, ?, ?)",
                Sql.toBytes(itemUuid), Sql.toBytes(profileKey), Sql.toBytes(snapshot.island()), (int) (cellMs / FuelMath.MINUTE), now);
        Sql.update(c, UPSERT, params(snapshot));
        int changed = Sql.update(c, "UPDATE island_fuel SET fuel_ms = fuel_ms + ?, updated_at = ? WHERE island_id = ? AND fuel_ms + ? <= ?",
                cellMs, now, Sql.toBytes(snapshot.island()), cellMs, capacityMs);
        if (changed != 1) {
            throw new NoRoom();
        }
        return new Added(cellMs, fuel(c, snapshot.island()));
    }

    long fuel(Connection c, UUID island) throws SQLException {
        return Sql.queryOne(c, "SELECT fuel_ms FROM island_fuel WHERE island_id = ?", rs -> rs.getLong(1), Sql.toBytes(island)).orElse(0L);
    }

    /** The island was disbanded, deleted or reset: its tank goes with it. */
    void delete(Connection c, UUID island) throws SQLException {
        Sql.update(c, "DELETE FROM island_fuel WHERE island_id = ?", Sql.toBytes(island));
    }
}
