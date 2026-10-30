package net.skybornmc.skyblock.islands;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.Sql;
import net.skybornmc.skyblock.common.island.IslandService.Role;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Island chat role colors ({@code island_chat_colors}, M2.5). A change is one transaction that re-checks against the
 * database that the changer still leads the island, so a stale menu or a demoted leader can never change them.
 */
final class IslandChatStore {

    /** Result of {@link #save}. */
    enum SaveResult { SAVED, NOT_LEADER }

    private final Database db;

    IslandChatStore(Database db) {
        this.db = db;
    }

    CompletableFuture<Map<UUID, Map<Role, String>>> loadAll() {
        return db.async(IslandChatStore::loadAll);
    }

    static Map<UUID, Map<Role, String>> loadAll(Connection c) throws SQLException {
        Map<UUID, Map<Role, String>> all = new HashMap<>();
        Sql.query(c, "SELECT island_id, member_role, chat_color FROM island_chat_colors", rs -> {
            Role role = role(rs.getString("member_role"));
            if (role != null) {
                all.computeIfAbsent(Sql.uuid(rs, "island_id"), k -> new EnumMap<>(Role.class)).put(role, rs.getString("chat_color"));
            }
            return null;
        });
        return all;
    }

    /** Saves the color of one role ({@code null} = back to the default), if {@code leader} still leads the island. */
    CompletableFuture<SaveResult> save(UUID islandId, UUID leader, Role role, String color) {
        long now = System.currentTimeMillis();
        return db.transaction(c -> save(c, islandId, leader, role, color, now));
    }

    static SaveResult save(Connection c, UUID islandId, UUID leader, Role role, String color, long now) throws SQLException {
        boolean leads = Sql.queryOne(c, "SELECT role FROM island_members WHERE island_id = ? AND uuid = ? FOR UPDATE",
                rs -> rs.getString("role"), islandId, leader).map("leader"::equals).orElse(false);
        if (!leads) {
            return SaveResult.NOT_LEADER;
        }
        String key = MemberStore.key(role);
        Sql.update(c, "DELETE FROM island_chat_colors WHERE island_id = ? AND member_role = ?", islandId, key);
        if (color != null) {
            Sql.update(c, "INSERT INTO island_chat_colors (island_id, member_role, chat_color, updated_at, updated_by) VALUES (?, ?, ?, ?, ?)",
                    islandId, key, color, now, leader);
        }
        return SaveResult.SAVED;
    }

    static void deleteIsland(Connection c, UUID islandId) throws SQLException {
        Sql.update(c, "DELETE FROM island_chat_colors WHERE island_id = ?", islandId);
    }

    /** A stored role key, or null for unknown ones (coop never has a chat color; rows from a newer version are skipped). */
    static Role role(String key) {
        if (key == null) {
            return null;
        }
        return switch (key.toLowerCase(Locale.ROOT)) {
            case "leader" -> Role.LEADER;
            case "admin" -> Role.ADMIN;
            case "mod" -> Role.MOD;
            case "member" -> Role.MEMBER;
            default -> null;
        };
    }
}
