package net.skybornmc.skyblock.trade;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.Sql;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * All trade SQL. The trade session row is always locked first ({@code SELECT … FOR UPDATE}), so adding, taking back,
 * completing and cancelling are serialized per trade; every state change is a conditional {@code UPDATE … WHERE state = …}.
 */
final class TradeStore {

    static final String OPEN = "open";
    static final String COMPLETED = "completed";
    static final String CANCELLED = "cancelled";

    static final String HELD = "held";
    static final String PENDING = "pending";
    static final String DELIVERED = "delivered";

    private static final String ESCROW_COLUMNS = "id, trade_id, origin_player, origin_profile, owner_player, owner_profile, state, "
            + "item, item_key, item_uuid, material, amount, item_name, created_at";

    /** One escrow row. */
    record EscrowRow(long id, long tradeId, UUID originPlayer, UUID originProfile, UUID ownerPlayer, UUID ownerProfile,
                     String state, Offer offer, long createdAt) {
    }

    private final Database db;

    TradeStore(Database db) {
        this.db = db;
    }

    Database db() {
        return db;
    }

    // ------------------------------------------------------------------ sessions

    /** Creates an open session; completes with its id. */
    CompletableFuture<Long> create(TradeSession.Side a, TradeSession.Side b, long now) {
        return db.async(c -> Sql.insertReturningId(c, "INSERT INTO trade_sessions (player_a, profile_a, name_a, player_b, profile_b, "
                        + "name_b, status, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                a.player, a.profile, clip(a.name, 32), b.player, b.profile, clip(b.name, 32), OPEN, now));
    }

    /** The status of a trade, empty if unknown. */
    CompletableFuture<Optional<String>> status(long tradeId) {
        return db.async(c -> Sql.queryOne(c, "SELECT status FROM trade_sessions WHERE id = ?", rs -> rs.getString(1), tradeId));
    }

    /** Locks the session row; returns its status (empty if it doesn't exist). */
    static Optional<String> lockStatus(Connection c, long tradeId) throws SQLException {
        return Sql.queryOne(c, "SELECT status FROM trade_sessions WHERE id = ? FOR UPDATE", rs -> rs.getString(1), tradeId);
    }

    // ------------------------------------------------------------------ escrow in and out

    /**
     * Stores an offered item in the escrow, only while the trade is open (same transaction as the status check).
     *
     * @return the new row id, or empty if the trade is not open any more (the caller gives the item back)
     */
    CompletableFuture<Optional<Long>> escrow(long tradeId, UUID player, UUID profile, Offer offer, String token, long now) {
        return db.transaction(c -> {
            if (!OPEN.equals(lockStatus(c, tradeId).orElse(null))) {
                return Optional.<Long>empty();
            }
            long id = Sql.insertReturningId(c, "INSERT INTO trade_escrow (trade_id, origin_player, origin_profile, owner_player, "
                            + "owner_profile, state, item, item_key, item_uuid, material, amount, item_name, write_token, created_at, "
                            + "updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    tradeId, player, profile, player, profile, HELD, offer.bytes(), offer.itemKey(), offer.itemUuid(),
                    clip(offer.material(), 64), offer.amount(), offer.name(), token, now, now);
            return Optional.of(id);
        });
    }

    /** The escrow row written with {@code token} (after a database error: was the item stored or not?). */
    CompletableFuture<Optional<EscrowRow>> byToken(String token) {
        return db.async(c -> Sql.queryOne(c, "SELECT " + ESCROW_COLUMNS + " FROM trade_escrow WHERE write_token = ?", TradeStore::row,
                token));
    }

    /**
     * Takes an offered item back out of an open trade: the held row becomes pending for its owner (delivered next).
     *
     * @return true if it was released; false if the trade is not open or the row isn't held (completion/cancel owns it)
     */
    CompletableFuture<Boolean> release(long tradeId, long rowId, UUID profile, long now) {
        return db.transaction(c -> {
            if (!OPEN.equals(lockStatus(c, tradeId).orElse(null))) {
                return false;
            }
            return Sql.update(c, "UPDATE trade_escrow SET state = ?, updated_at = ? WHERE id = ? AND trade_id = ? AND state = ? "
                    + "AND origin_profile = ?", PENDING, now, rowId, tradeId, HELD, profile) == 1;
        });
    }

    // ------------------------------------------------------------------ the swap

    /**
     * The item half of the swap, run inside the economy batch transaction ({@code TxGuard}): the trade must still be open
     * and hold exactly the expected rows; then every held row changes owner to the other side and becomes pending, and
     * the session is completed. Returns false (= roll back everything) on any mismatch.
     */
    static boolean completeGuard(Connection c, TradeSession.Snapshot s, long now) throws SQLException {
        if (!OPEN.equals(lockStatus(c, s.id()).orElse(null))) {
            return false;
        }
        Set<Long> heldA = new HashSet<>();
        Set<Long> heldB = new HashSet<>();
        for (Object[] row : Sql.query(c, "SELECT id, origin_profile FROM trade_escrow WHERE trade_id = ? AND state = ? FOR UPDATE",
                rs -> new Object[]{rs.getLong(1), Sql.uuid(rs, "origin_profile")}, s.id(), HELD)) {
            UUID origin = (UUID) row[1];
            if (origin.equals(s.profileA())) {
                heldA.add((Long) row[0]);
            } else if (origin.equals(s.profileB())) {
                heldB.add((Long) row[0]);
            } else {
                return false;
            }
        }
        if (!heldA.equals(new HashSet<>(s.idsA())) || !heldB.equals(new HashSet<>(s.idsB()))) {
            return false;
        }
        String move = "UPDATE trade_escrow SET owner_player = ?, owner_profile = ?, state = ?, updated_at = ? "
                + "WHERE trade_id = ? AND state = ? AND origin_profile = ?";
        if (Sql.update(c, move, s.playerB(), s.profileB(), PENDING, now, s.id(), HELD, s.profileA()) != heldA.size()) {
            return false;
        }
        if (Sql.update(c, move, s.playerA(), s.profileA(), PENDING, now, s.id(), HELD, s.profileB()) != heldB.size()) {
            return false;
        }
        return Sql.update(c, "UPDATE trade_sessions SET status = ?, ended_at = ?, end_reason = ? WHERE id = ? AND status = ?",
                COMPLETED, now, "completed", s.id(), OPEN) == 1;
    }

    // ------------------------------------------------------------------ cancel and recovery

    /** Outcome of {@link #cancel}: the status before, and the rows that went back to their owners. */
    record CancelOutcome(String previous, List<EscrowRow> returned) {
    }

    /**
     * Cancels an open trade: status cancelled, every held row pending for its owner (who put it in). A trade that is
     * already completed or cancelled is left as it is ({@code previous} tells which).
     */
    CompletableFuture<CancelOutcome> cancel(long tradeId, String reason, long now) {
        return db.transaction(c -> cancelNow(c, tradeId, reason, now));
    }

    static CancelOutcome cancelNow(Connection c, long tradeId, String reason, long now) throws SQLException {
        String previous = lockStatus(c, tradeId).orElse(null);
        if (!OPEN.equals(previous)) {
            return new CancelOutcome(previous, List.of());
        }
        List<EscrowRow> held = Sql.query(c, "SELECT " + ESCROW_COLUMNS + " FROM trade_escrow WHERE trade_id = ? AND state = ? FOR UPDATE",
                TradeStore::row, tradeId, HELD);
        Sql.update(c, "UPDATE trade_escrow SET state = ?, updated_at = ? WHERE trade_id = ? AND state = ?", PENDING, now, tradeId, HELD);
        Sql.update(c, "UPDATE trade_sessions SET status = ?, ended_at = ?, end_reason = ? WHERE id = ? AND status = ?",
                CANCELLED, now, clip(reason, 32), tradeId, OPEN);
        return new CancelOutcome(OPEN, held);
    }

    /** Trades open in the database that started before {@code cutoff} (crash recovery, orphan check while running). */
    CompletableFuture<List<Long>> openTradesBefore(long cutoff) {
        return db.async(c -> Sql.query(c, "SELECT id FROM trade_sessions WHERE status = ? AND created_at < ?", rs -> rs.getLong(1),
                OPEN, cutoff));
    }

    /** Blocking cancel for shutdown (the database is about to close). */
    CancelOutcome cancelBlocking(long tradeId, String reason, long now) {
        return db.blockingTransaction(c -> cancelNow(c, tradeId, reason, now));
    }

    // ------------------------------------------------------------------ delivery

    /** Items waiting for a profile (returned or received), oldest first. */
    CompletableFuture<List<EscrowRow>> pending(UUID ownerProfile) {
        return db.async(c -> Sql.query(c, "SELECT " + ESCROW_COLUMNS + " FROM trade_escrow WHERE owner_profile = ? AND state = ? "
                + "ORDER BY id", TradeStore::row, ownerProfile, PENDING));
    }

    /** Every profile with items waiting. */
    CompletableFuture<Set<UUID>> pendingOwners() {
        return db.async(c -> new HashSet<>(Sql.query(c, "SELECT DISTINCT owner_profile FROM trade_escrow WHERE state = ?",
                rs -> Sql.uuid(rs, "owner_profile"), PENDING)));
    }

    /** Takes one waiting item for delivery (exactly once). */
    CompletableFuture<Boolean> claim(long rowId, UUID ownerProfile, long now) {
        return db.async(c -> Sql.update(c, "UPDATE trade_escrow SET state = ?, updated_at = ? WHERE id = ? AND owner_profile = ? "
                + "AND state = ?", DELIVERED, now, rowId, ownerProfile, PENDING) == 1);
    }

    /** Puts an item that did not fit back into the escrow as waiting (a new pending row of the same trade). */
    CompletableFuture<Long> putBack(long tradeId, UUID ownerPlayer, UUID ownerProfile, Offer offer, long now) {
        return db.async(c -> Sql.insertReturningId(c, "INSERT INTO trade_escrow (trade_id, origin_player, origin_profile, owner_player, "
                        + "owner_profile, state, item, item_key, item_uuid, material, amount, item_name, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                tradeId, ownerPlayer, ownerProfile, ownerPlayer, ownerProfile, PENDING, offer.bytes(), offer.itemKey(),
                offer.itemUuid(), clip(offer.material(), 64), offer.amount(), offer.name(), now, now));
    }

    /** Rows of one trade (tests and staff checks). */
    CompletableFuture<List<EscrowRow>> rows(long tradeId) {
        return db.async(c -> Sql.query(c, "SELECT " + ESCROW_COLUMNS + " FROM trade_escrow WHERE trade_id = ? ORDER BY id",
                TradeStore::row, tradeId));
    }

    private static EscrowRow row(ResultSet rs) throws SQLException {
        Offer offer = new Offer(rs.getLong("id"), rs.getBytes("item"), rs.getString("item_key"), rs.getString("item_uuid"),
                rs.getString("material"), rs.getInt("amount"), rs.getString("item_name"));
        return new EscrowRow(rs.getLong("id"), rs.getLong("trade_id"), Sql.uuid(rs, "origin_player"), Sql.uuid(rs, "origin_profile"),
                Sql.uuid(rs, "owner_player"), Sql.uuid(rs, "owner_profile"), rs.getString("state"), offer, rs.getLong("created_at"));
    }

    static String clip(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    /** Ids of a list of rows (tests). */
    static List<Long> ids(List<EscrowRow> rows) {
        List<Long> out = new ArrayList<>(rows.size());
        rows.forEach(r -> out.add(r.id()));
        return out;
    }
}
