package net.skybornmc.skyblock.islands;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.Sql;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** The island storage in the database ({@code island_vault}, batch 4): one row per used slot, written as a whole. */
final class IslandVaultStore {

    private final Database db;

    IslandVaultStore(Database db) {
        this.db = db;
    }

    /** Slot → serialized item of an island's storage. */
    CompletableFuture<Map<Integer, byte[]>> load(UUID islandId) {
        return db.async(c -> load(c, islandId));
    }

    static Map<Integer, byte[]> load(Connection c, UUID islandId) throws SQLException {
        Map<Integer, byte[]> out = new LinkedHashMap<>();
        for (Object[] row : Sql.query(c, "SELECT slot, item FROM island_vault WHERE island_id = ? ORDER BY slot",
                rs -> new Object[]{rs.getInt("slot"), rs.getBytes("item")}, Sql.toBytes(islandId))) {
            out.put((Integer) row[0], (byte[]) row[1]);
        }
        return out;
    }

    /** Replaces the island's rows with {@code slots} (async, one transaction). */
    CompletableFuture<Void> save(UUID islandId, Map<Integer, byte[]> slots, long now) {
        return db.transaction(c -> {
            save(c, islandId, slots, now);
            return null;
        });
    }

    /** Blocking save for the shutdown. */
    void saveNow(UUID islandId, Map<Integer, byte[]> slots, long now) {
        db.blockingTransaction(c -> {
            save(c, islandId, slots, now);
            return null;
        });
    }

    /** Replaces the island's rows inside the caller's transaction. */
    static void save(Connection c, UUID islandId, Map<Integer, byte[]> slots, long now) throws SQLException {
        byte[] id = Sql.toBytes(islandId);
        Sql.update(c, "DELETE FROM island_vault WHERE island_id = ?", id);
        if (slots.isEmpty()) {
            return;
        }
        List<Object[]> rows = new ArrayList<>(slots.size());
        slots.forEach((slot, item) -> rows.add(new Object[]{id, slot, item, now}));
        Sql.batch(c, "INSERT INTO island_vault (island_id, slot, item, updated_at) VALUES (?, ?, ?, ?)", rows);
    }
}
