package net.skybornmc.skyblock.islands;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.Sql;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** SQL of {@code island_homes} (PHASE-5 M5.5 Homestead shared homes; MariaDB, H2 in MySQL mode for tests). */
final class IslandHomeStore {

    /** What a save did. */
    enum Saved { NEW, MOVED, LIMIT }

    private final Database db;

    IslandHomeStore(Database db) {
        this.db = db;
    }

    CompletableFuture<Map<UUID, Map<String, Island.Home>>> loadAll() {
        return db.async(IslandHomeStore::loadAll);
    }

    CompletableFuture<Saved> save(UUID islandId, String name, Island.Home home, UUID by, int allowed, long now) {
        return db.transaction(c -> save(c, islandId, name, home, by, allowed, now));
    }

    CompletableFuture<Boolean> delete(UUID islandId, String name) {
        return db.async(c -> delete(c, islandId, name));
    }

    static Map<UUID, Map<String, Island.Home>> loadAll(Connection c) throws SQLException {
        Map<UUID, Map<String, Island.Home>> out = new HashMap<>();
        Sql.query(c, "SELECT island_id, home_name, x, y, z, yaw, pitch FROM island_homes ORDER BY home_name", rs -> {
            out.computeIfAbsent(Sql.uuid(rs, "island_id"), k -> new LinkedHashMap<>()).put(rs.getString("home_name"),
                    new Island.Home(rs.getDouble("x"), rs.getDouble("y"), rs.getDouble("z"), rs.getFloat("yaw"), rs.getFloat("pitch")));
            return null;
        });
        return out;
    }

    /**
     * Sets a named home in one transaction: an existing name moves; a new one only while the island has fewer than
     * {@code allowed} named homes (the island's rows are locked first, so two members can't both take the last slot).
     */
    static Saved save(Connection c, UUID islandId, String name, Island.Home home, UUID by, int allowed, long now) throws SQLException {
        List<String> names = Sql.query(c, "SELECT home_name FROM island_homes WHERE island_id = ? FOR UPDATE",
                rs -> rs.getString(1), islandId);
        if (names.contains(name)) {
            Sql.update(c, "UPDATE island_homes SET x = ?, y = ?, z = ?, yaw = ?, pitch = ?, set_by = ?, updated_at = ? "
                            + "WHERE island_id = ? AND home_name = ?", home.x(), home.y(), home.z(), home.yaw(), home.pitch(),
                    by, now, islandId, name);
            return Saved.MOVED;
        }
        if (names.size() >= allowed) {
            return Saved.LIMIT;
        }
        Sql.update(c, "INSERT INTO island_homes (island_id, home_name, x, y, z, yaw, pitch, set_by, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                islandId, name, home.x(), home.y(), home.z(), home.yaw(), home.pitch(), by, now);
        return Saved.NEW;
    }

    static boolean delete(Connection c, UUID islandId, String name) throws SQLException {
        return Sql.update(c, "DELETE FROM island_homes WHERE island_id = ? AND home_name = ?", islandId, name) == 1;
    }

    /** Removes every home of an island (inside the M2.7 removal transaction). */
    static void deleteIsland(Connection c, UUID islandId) throws SQLException {
        Sql.update(c, "DELETE FROM island_homes WHERE island_id = ?", islandId);
    }
}
