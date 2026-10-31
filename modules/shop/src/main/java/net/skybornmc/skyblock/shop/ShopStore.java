package net.skybornmc.skyblock.shop;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.Sql;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The {@code shop_purchases} table (M3.5). A row is inserted inside the payment's database transaction
 * ({@link #insert}, used as the {@code TxGuard}); every later state change is a conditional update, so a purchase is
 * handed over exactly once.
 */
final class ShopStore {

    static final String DELIVERING = "delivering";
    static final String PENDING = "pending";
    static final String DELIVERED = "delivered";
    /**
     * SECFIX1 F-12: left {@code delivering} by a crash or stop. The items may already be in the player's saved inventory,
     * so the purchase is never handed over again automatically: staff check it ({@code shop_review} in the audit log) and
     * refund or deliver by hand.
     */
    static final String REVIEW = "review";
    /** F14: given back with "Undo last buy" (items taken back, full price refunded once). Only from {@code delivered}. */
    static final String REFUNDED = "refunded";

    /** One purchase. */
    record Purchase(UUID id, int seasonId, UUID player, UUID profile, String playerName, String itemId, String category,
                    int quantity, long unitPrice, long totalPrice, String currency, String state, long createdAt) {
    }

    private final Database db;

    ShopStore(Database db) {
        this.db = db;
    }

    /** Inserts a paid purchase as {@code delivering}, on the payment's connection. True if the row was written. */
    static boolean insert(Connection c, Purchase p, long now) throws SQLException {
        return Sql.update(c, "INSERT INTO shop_purchases (id, season_id, player_uuid, profile_key, player_name, item_id, "
                        + "category_id, quantity, unit_price, total_price, currency_key, state, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                p.id(), p.seasonId(), p.player(), p.profile(), p.playerName(), p.itemId(), p.category(), p.quantity(),
                p.unitPrice(), p.totalPrice(), p.currency(), DELIVERING, now, now) == 1;
    }

    /** {@code from → to} for one row; true if this call changed it (exactly-once hand-over). */
    CompletableFuture<Boolean> move(UUID id, String from, String to, long now) {
        return db.async(c -> move(c, id, from, to, now));
    }

    static boolean move(Connection c, UUID id, String from, String to, long now) throws SQLException {
        return Sql.update(c, "UPDATE shop_purchases SET state = ?, updated_at = ? WHERE id = ? AND state = ?", to, now, id, from) == 1;
    }

    /** Purchases waiting for this profile, oldest first. */
    CompletableFuture<List<Purchase>> pending(UUID profile) {
        return db.async(c -> pending(c, profile));
    }

    static List<Purchase> pending(Connection c, UUID profile) throws SQLException {
        return Sql.query(c, "SELECT * FROM shop_purchases WHERE profile_key = ? AND state = ? ORDER BY created_at, id",
                ShopStore::map, profile, PENDING);
    }

    /**
     * Startup: rows left {@code delivering} by a crash or stop. SECFIX1 F-12: the hand-over may have happened (items given
     * and the player file saved just before the crash), so they are NEVER moved back to {@code pending} (that delivered
     * twice). They become {@code review} for staff instead. Completes with the purchases moved.
     */
    CompletableFuture<List<Purchase>> recover(long now) {
        return db.async(c -> recover(c, now));
    }

    static List<Purchase> recover(Connection c, long now) throws SQLException {
        List<Purchase> stuck = Sql.query(c, "SELECT * FROM shop_purchases WHERE state = ? ORDER BY created_at, id", ShopStore::map, DELIVERING);
        List<Purchase> moved = new java.util.ArrayList<>(stuck.size());
        for (Purchase p : stuck) {
            if (move(c, p.id(), DELIVERING, REVIEW, now)) {
                moved.add(p);
            }
        }
        return moved;
    }

    static Purchase get(Connection c, UUID id) throws SQLException {
        return Sql.queryOne(c, "SELECT * FROM shop_purchases WHERE id = ?", ShopStore::map, id).orElse(null);
    }

    static Purchase map(ResultSet rs) throws SQLException {
        return new Purchase(Sql.uuid(rs, "id"), rs.getInt("season_id"), Sql.uuid(rs, "player_uuid"), Sql.uuid(rs, "profile_key"),
                rs.getString("player_name"), rs.getString("item_id"), rs.getString("category_id"), rs.getInt("quantity"),
                rs.getLong("unit_price"), rs.getLong("total_price"), rs.getString("currency_key"), rs.getString("state"),
                rs.getLong("created_at"));
    }
}
