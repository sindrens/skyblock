package net.skybornmc.skyblock.blackmarket;

import net.skybornmc.skyblock.common.db.Sql;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** SQL of the Black Market (tables in {@code db/blackmarket/V001__blackmarket.sql}). Every method runs inside a database task. */
final class BlackMarketStore {

    static final String PENDING = "pending";
    static final String SENDING = "sending";
    static final String DELIVERED = "delivered";
    static final String REFUNDED = "refunded";
    static final String FAILED = "failed";
    static final String REVIEW = "review";

    /** Currency key of a staff give (nothing paid). */
    static final String STAFF = "staff";

    /** One stored offer of a rotation; {@code left} = server-wide stock left (-1 = no shared stock). */
    record RotationRow(int index, String offerId, long price, int total, int left) {
    }

    /** One buy. {@code island} is set for an Island Charter. */
    record BuyRow(UUID id, UUID account, UUID profile, long rotationId, String offerId, String kind, String ref, int amount,
                  String currency, long price, UUID island, String state, long boughtAt) {
    }

    /** What the buy guard decided. */
    enum Outcome { OK, SOLD_OUT, LIMIT, CHARTER_TAKEN, STALE }

    /**
     * A limit counter to raise in the buy transaction.
     *
     * @param limit 0 = only counted, never refused
     */
    record Count(String scope, String offerId, String periodKey, int limit) {
    }

    private BlackMarketStore() {
    }

    // ================================================================== rotations

    static List<RotationRow> rotation(Connection c, long rotationId) throws SQLException {
        return Sql.query(c, "SELECT slot_index, offer_id, price_shards, stock_total, stock_left FROM black_market_rotation "
                        + "WHERE rotation_id = ? ORDER BY slot_index",
                rs -> new RotationRow(rs.getInt("slot_index"), rs.getString("offer_id"), rs.getLong("price_shards"), rs.getInt("stock_total"),
                        rs.getInt("stock_left")), rotationId);
    }

    /**
     * The stored offers of a rotation; when none are stored yet, {@code picks} are written and returned. A second server
     * or thread writing at the same moment loses on the primary key and reads the winner's rows.
     */
    static List<RotationRow> ensureRotation(Connection c, long rotationId, List<Rotation.Pick> picks, long startsAt) throws SQLException {
        List<RotationRow> stored = rotation(c, rotationId);
        if (!stored.isEmpty() || picks.isEmpty()) {
            return stored;
        }
        try {
            insertPicks(c, rotationId, picks, startsAt);
        } catch (SQLException e) {
            if (!duplicate(e)) {
                throw e;
            }
        }
        return rotation(c, rotationId);
    }

    /** Staff reroll: the rotation's offers are replaced (limits per rotation stay counted by offer id). */
    static List<RotationRow> replaceRotation(Connection c, long rotationId, List<Rotation.Pick> picks, long startsAt) throws SQLException {
        Sql.update(c, "DELETE FROM black_market_rotation WHERE rotation_id = ?", rotationId);
        insertPicks(c, rotationId, picks, startsAt);
        return rotation(c, rotationId);
    }

    private static void insertPicks(Connection c, long rotationId, List<Rotation.Pick> picks, long startsAt) throws SQLException {
        List<Object[]> rows = new ArrayList<>();
        for (Rotation.Pick p : picks) {
            rows.add(new Object[]{rotationId, p.index(), p.offerId(), p.price(), p.stock(), p.stock(), startsAt});
        }
        if (!rows.isEmpty()) {
            Sql.batch(c, "INSERT INTO black_market_rotation (rotation_id, slot_index, offer_id, price_shards, stock_total, stock_left, "
                    + "starts_at) VALUES (?, ?, ?, ?, ?, ?, ?)", rows);
        }
    }

    static boolean duplicate(SQLException e) {
        return e.getErrorCode() == 1062 || "23505".equals(e.getSQLState()) || (e.getSQLState() != null && e.getSQLState().startsWith("23"));
    }

    // ================================================================== counts

    /**
     * Raises a counter by {@code add} only while it stays within {@code limit} (0 = no limit). Locks the counter row, so
     * concurrent buys of the same scope wait for each other.
     */
    static boolean bump(Connection c, Count count, int add, long now) throws SQLException {
        Sql.update(c, "INSERT INTO black_market_counts (scope_id, offer_id, period_key, bought, updated_at) VALUES (?, ?, ?, 0, ?) "
                + "ON DUPLICATE KEY UPDATE bought = bought", count.scope(), count.offerId(), count.periodKey(), now);
        if (count.limit() <= 0) {
            return Sql.update(c, "UPDATE black_market_counts SET bought = bought + ?, updated_at = ? WHERE scope_id = ? AND offer_id = ? "
                    + "AND period_key = ?", add, now, count.scope(), count.offerId(), count.periodKey()) == 1;
        }
        return Sql.update(c, "UPDATE black_market_counts SET bought = bought + ?, updated_at = ? WHERE scope_id = ? AND offer_id = ? "
                + "AND period_key = ? AND bought + ? <= ?", add, now, count.scope(), count.offerId(), count.periodKey(), add, count.limit()) == 1;
    }

    /** Counters of one scope for the given period keys: {@code "<offer>|<period>"} → bought. */
    static Map<String, Integer> counts(Connection c, String scope, Collection<String> periodKeys) throws SQLException {
        Map<String, Integer> out = new HashMap<>();
        if (periodKeys.isEmpty()) {
            return out;
        }
        String marks = String.join(", ", java.util.Collections.nCopies(periodKeys.size(), "?"));
        List<Object> params = new ArrayList<>();
        params.add(scope);
        params.addAll(periodKeys);
        for (Object[] row : Sql.query(c, "SELECT offer_id, period_key, bought FROM black_market_counts WHERE scope_id = ? AND period_key IN ("
                + marks + ")", rs -> new Object[]{rs.getString("offer_id"), rs.getString("period_key"), rs.getInt("bought")}, params.toArray())) {
            out.put(row[0] + "|" + row[1], (Integer) row[2]);
        }
        return out;
    }

    static String countKey(String offerId, String periodKey) {
        return offerId + "|" + periodKey;
    }

    // ================================================================== buying

    /**
     * The buy guard, inside the payment's transaction: checks the offer is still in the rotation, lowers the shared
     * stock, raises every counter within its limit and writes the {@code pending} buy row. Anything refused rolls back
     * the whole transaction (payment included).
     *
     * @param slotIndex the rotation slot of a rotating offer, or -1 for the fixed row and the charter
     */
    static Outcome buy(Connection c, BuyRow row, int slotIndex, List<Count> limits, Count charter, long now) throws SQLException {
        if (slotIndex >= 0) {
            java.util.Optional<Integer> left = Sql.queryOne(c, "SELECT stock_left FROM black_market_rotation WHERE rotation_id = ? "
                    + "AND slot_index = ? AND offer_id = ?", rs -> rs.getInt("stock_left"), row.rotationId(), slotIndex, row.offerId());
            if (left.isEmpty()) {
                return Outcome.STALE;
            }
            if (left.get() >= 0 && Sql.update(c, "UPDATE black_market_rotation SET stock_left = stock_left - 1 WHERE rotation_id = ? "
                    + "AND slot_index = ? AND stock_left >= 1", row.rotationId(), slotIndex) != 1) {
                return Outcome.SOLD_OUT;
            }
        }
        if (charter != null && !bump(c, charter, 1, now)) {
            return Outcome.CHARTER_TAKEN;
        }
        for (Count count : limits) {
            if (!bump(c, count, 1, now)) {
                return Outcome.LIMIT;
            }
        }
        insertBuy(c, row);
        return Outcome.OK;
    }

    static void insertBuy(Connection c, BuyRow r) throws SQLException {
        Sql.update(c, "INSERT INTO black_market_buys (buy_id, account_id, profile_key, rotation_id, offer_id, reward_kind, reward_ref, amount, "
                        + "pay_currency, price, island_id, buy_state, bought_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                r.id(), r.account(), r.profile(), r.rotationId(), r.offerId(), r.kind(), r.ref(), r.amount(), r.currency(), r.price(),
                r.island(), r.state(), r.boughtAt(), r.boughtAt());
    }

    // ================================================================== hand-outs

    /** Moves a buy from one state to another; false if it was not in {@code from} (someone else has it). */
    static boolean move(Connection c, UUID buyId, String from, String to, long now) throws SQLException {
        return Sql.update(c, "UPDATE black_market_buys SET buy_state = ?, updated_at = ? WHERE buy_id = ? AND buy_state = ?",
                to, now, buyId, from) == 1;
    }

    static List<BuyRow> buys(Connection c, String state, int limit) throws SQLException {
        return Sql.query(c, "SELECT * FROM black_market_buys WHERE buy_state = ? ORDER BY bought_at LIMIT " + Math.max(1, limit),
                BlackMarketStore::map, state);
    }

    /** The newest buys of an account (staff view). */
    static List<BuyRow> history(Connection c, UUID account, int limit) throws SQLException {
        return Sql.query(c, "SELECT * FROM black_market_buys WHERE account_id = ? ORDER BY bought_at DESC LIMIT " + Math.max(1, limit),
                BlackMarketStore::map, account);
    }

    private static BuyRow map(java.sql.ResultSet rs) throws SQLException {
        byte[] island = rs.getBytes("island_id");
        return new BuyRow(Sql.uuid(rs, "buy_id"), Sql.uuid(rs, "account_id"), Sql.uuid(rs, "profile_key"), rs.getLong("rotation_id"),
                rs.getString("offer_id"), rs.getString("reward_kind"), rs.getString("reward_ref"), rs.getInt("amount"),
                rs.getString("pay_currency"), rs.getLong("price"), island == null ? null : Sql.toUuid(island), rs.getString("buy_state"),
                rs.getLong("bought_at"));
    }
}
