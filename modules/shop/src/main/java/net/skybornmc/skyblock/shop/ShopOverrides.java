package net.skybornmc.skyblock.shop;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.Sql;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ECON-SELL-SHOP-AH B2: items staff switched off with {@code /eco shop disable} ({@code shop_overrides}, shop V003). The
 * table is read once at enable; a toggle writes the row first and changes the memory copy only when the write worked.
 */
final class ShopOverrides {

    /** One switched-off item. */
    record Off(String reason, String actor, long at) {
    }

    private final Database db;
    private final Map<String, Off> off = new ConcurrentHashMap<>();

    ShopOverrides(Database db) {
        this.db = db;
    }

    CompletableFuture<Void> load() {
        return db.async(c -> {
            Map<String, Off> rows = read(c);
            off.clear();
            off.putAll(rows);
            return null;
        });
    }

    static Map<String, Off> read(Connection c) throws SQLException {
        Map<String, Off> out = new java.util.HashMap<>();
        Sql.query(c, "SELECT item_id, reason, actor, changed_at FROM shop_overrides WHERE enabled = FALSE", rs -> {
            out.put(rs.getString("item_id"), new Off(rs.getString("reason"), rs.getString("actor"), rs.getLong("changed_at")));
            return null;
        });
        return out;
    }

    /** Writes the state; the memory copy follows once it is saved. */
    CompletableFuture<Void> set(String itemId, boolean enabled, String reason, String actor, long now) {
        String why = reason.length() > 128 ? reason.substring(0, 128) : reason;
        return db.async(c -> {
            write(c, itemId, enabled, why, actor, now);
            return null;
        }).thenRun(() -> {
            if (enabled) {
                off.remove(itemId);
            } else {
                off.put(itemId, new Off(why, actor, now));
            }
        });
    }

    static void write(Connection c, String itemId, boolean enabled, String reason, String actor, long now) throws SQLException {
        if (Sql.update(c, "UPDATE shop_overrides SET enabled = ?, reason = ?, actor = ?, changed_at = ? WHERE item_id = ?",
                enabled, reason, actor, now, itemId) == 0) {
            Sql.update(c, "INSERT INTO shop_overrides (item_id, enabled, reason, actor, changed_at) VALUES (?, ?, ?, ?, ?)",
                    itemId, enabled, reason, actor, now);
        }
    }

    Set<String> disabled() {
        return Set.copyOf(off.keySet());
    }

    Off get(String itemId) {
        return off.get(itemId);
    }
}
