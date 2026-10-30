package net.skybornmc.skyblock.economy.sell;

import com.google.gson.Gson;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.Sql;
import net.skybornmc.skyblock.common.sell.SellQuote;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Database access of selling ({@code db/economy/V004__sell.sql}): price states, sale records, pending refunds. */
public final class SellStore {

    private static final Gson GSON = new Gson();

    private final Database db;

    public SellStore(Database db) {
        this.db = db;
    }

    // ------------------------------------------------------------------ price states

    /** Saved price states plus their last step time. */
    public record Loaded(List<PriceState> states, Map<String, Long> updatedAt) {
    }

    public CompletableFuture<Loaded> loadPrices() {
        return db.async(this::loadPrices);
    }

    Loaded loadPrices(Connection c) throws SQLException {
        Map<String, Long> updated = new HashMap<>();
        List<PriceState> states = Sql.query(c, "SELECT item_key, multiplier, impact, last_sale_at, hour_ago, volume, sold_total, updated_at "
                + "FROM sell_prices", rs -> {
            String key = rs.getString("item_key");
            updated.put(key, rs.getLong("updated_at"));
            return new PriceState(key, rs.getDouble("multiplier"), rs.getDouble("impact"), rs.getLong("last_sale_at"),
                    rs.getDouble("hour_ago"), VolumeWindow.decode(rs.getString("volume")), rs.getLong("sold_total"));
        });
        return new Loaded(states, updated);
    }

    /** Saves states (update, or insert when new) in one transaction, off the main thread. */
    public CompletableFuture<Void> savePrices(List<PriceState> states, long now) {
        if (states.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        return db.transaction(c -> {
            savePrices(c, states, now);
            return null;
        });
    }

    /** Shutdown save: blocks (the scheduler is gone by then). */
    public void savePricesBlocking(List<PriceState> states, long now) {
        if (!states.isEmpty()) {
            db.blockingTransaction(c -> {
                savePrices(c, states, now);
                return null;
            });
        }
    }

    void savePrices(Connection c, List<PriceState> states, long now) throws SQLException {
        for (PriceState s : states) {
            String volume = s.volume().encode();
            int updated = Sql.update(c, "UPDATE sell_prices SET multiplier = ?, impact = ?, last_sale_at = ?, hour_ago = ?, volume = ?, "
                            + "sold_total = ?, updated_at = ? WHERE item_key = ?",
                    s.multiplier(), s.impact(), s.lastSaleAt(), s.hourAgo(), volume, s.soldTotal(), now, s.key());
            if (updated == 0) {
                Sql.update(c, "INSERT INTO sell_prices (item_key, multiplier, impact, last_sale_at, hour_ago, volume, sold_total, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)", s.key(), s.multiplier(), s.impact(), s.lastSaleAt(), s.hourAgo(), volume,
                        s.soldTotal(), now);
            }
        }
    }

    // ------------------------------------------------------------------ sales

    /** One sale as stored in {@code sell_sales}. */
    public record SaleRecord(UUID id, long createdAt, UUID profile, UUID player, String playerName, String source, SellQuote quote) {
    }

    /** Inserts the sale record inside the payment transaction (the {@code TxGuard}). */
    public boolean insertSale(Connection c, SaleRecord r) throws SQLException {
        String name = r.playerName().length() > 32 ? r.playerName().substring(0, 32) : r.playerName();
        Sql.update(c, "INSERT INTO sell_sales (id, created_at, profile_id, player_id, player_name, source, item_count, subtotal, "
                        + "bonus_percent, total, sale_lines) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                r.id(), r.createdAt(), r.profile(), r.player(), name, r.source(), r.quote().items(), r.quote().subtotal(),
                r.quote().cappedBonusPercent(), r.quote().total(), linesJson(r.quote()));
        return true;
    }

    static String linesJson(SellQuote quote) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (SellQuote.Line line : quote.lines()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("item", line.price().itemKey());
            m.put("amount", line.amount());
            m.put("base", line.price().base());
            m.put("multiplier", Math.round(line.price().multiplier() * 10_000) / 10_000.0);
            m.put("hot", line.price().hot());
            m.put("value", Math.round(line.value() * 100) / 100.0);
            out.add(m);
        }
        return GSON.toJson(out);
    }

    /** A sale record by id (tests, staff tools later). */
    public CompletableFuture<Long> saleTotal(UUID id) {
        return db.async(c -> Sql.queryOne(c, "SELECT total FROM sell_sales WHERE id = ?", rs -> rs.getLong(1), id).orElse(-1L));
    }

    // ------------------------------------------------------------------ refunds

    public record Refund(long id, UUID profile, byte[] item) {
    }

    public CompletableFuture<Void> addRefund(UUID owner, UUID profile, byte[] item, String material, int amount, long now) {
        return db.run(c -> Sql.update(c, "INSERT INTO sell_refunds (owner, profile_id, item, material, amount, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                owner, profile, item, material, amount, now));
    }

    public CompletableFuture<List<Refund>> refunds(UUID owner) {
        return db.async(c -> Sql.query(c, "SELECT id, profile_id, item FROM sell_refunds WHERE owner = ? ORDER BY id",
                rs -> new Refund(rs.getLong("id"), Sql.uuid(rs, "profile_id"), rs.getBytes("item")), owner));
    }

    /** Claims a refund: true only for the one caller that deleted the row (hand the item out only then). */
    public CompletableFuture<Boolean> takeRefund(long id) {
        return db.async(c -> Sql.update(c, "DELETE FROM sell_refunds WHERE id = ?", id) == 1);
    }
}
