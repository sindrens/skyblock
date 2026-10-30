package net.skybornmc.skyblock.islands;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.Sql;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Personal homes of island members ({@code island_member_homes}, ISL2). Saving is one transaction that re-checks the
 * membership and the home limit against the database, so a double click or two servers can never exceed it.
 */
final class MemberHomeStore {

    /** Result of {@link #save}. */
    enum SaveResult { SAVED, MOVED, NOT_MEMBER, LIMIT }

    private final Database db;

    MemberHomeStore(Database db) {
        this.db = db;
    }

    CompletableFuture<SaveResult> save(UUID islandId, UUID member, String name, Island.Home home, int maxHomes) {
        long now = System.currentTimeMillis();
        return db.transaction(c -> save(c, islandId, member, name, home, maxHomes, now));
    }

    static SaveResult save(Connection c, UUID islandId, UUID member, String name, Island.Home home, int maxHomes, long now)
            throws SQLException {
        boolean isMember = Sql.queryOne(c, "SELECT 1 FROM island_members WHERE island_id = ? AND uuid = ? FOR UPDATE",
                rs -> Boolean.TRUE, islandId, member).isPresent();
        if (!isMember) {
            return SaveResult.NOT_MEMBER;
        }
        int updated = Sql.update(c, "UPDATE island_member_homes SET x = ?, y = ?, z = ?, yaw = ?, pitch = ?, updated_at = ? "
                + "WHERE island_id = ? AND uuid = ? AND name = ?", home.x(), home.y(), home.z(), home.yaw(), home.pitch(), now,
                islandId, member, name);
        if (updated > 0) {
            return SaveResult.MOVED;
        }
        int count = Sql.queryOne(c, "SELECT COUNT(*) FROM island_member_homes WHERE island_id = ? AND uuid = ?",
                rs -> rs.getInt(1), islandId, member).orElse(0);
        if (count >= maxHomes) {
            return SaveResult.LIMIT;
        }
        Sql.update(c, "INSERT INTO island_member_homes (island_id, uuid, name, x, y, z, yaw, pitch, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", islandId, member, name, home.x(), home.y(), home.z(), home.yaw(), home.pitch(), now);
        return SaveResult.SAVED;
    }

    /** Every personal home of the loaded islands (rows of unknown islands or ex-members are ignored). */
    static void load(Connection c, Map<UUID, Island> islands) throws SQLException {
        Sql.query(c, "SELECT island_id, uuid, name, x, y, z, yaw, pitch FROM island_member_homes ORDER BY updated_at", rs -> {
            Island island = islands.get(Sql.uuid(rs, "island_id"));
            UUID member = Sql.uuid(rs, "uuid");
            if (island != null && island.role(member).isPresent()) {
                island.putPersonalHome(member, rs.getString("name"), new Island.Home(rs.getDouble("x"), rs.getDouble("y"),
                        rs.getDouble("z"), rs.getFloat("yaw"), rs.getFloat("pitch")));
            }
            return null;
        });
    }

    static void deleteMember(Connection c, UUID islandId, UUID member) throws SQLException {
        Sql.update(c, "DELETE FROM island_member_homes WHERE island_id = ? AND uuid = ?", islandId, member);
    }

    static void deleteIsland(Connection c, UUID islandId) throws SQLException {
        Sql.update(c, "DELETE FROM island_member_homes WHERE island_id = ?", islandId);
    }
}
