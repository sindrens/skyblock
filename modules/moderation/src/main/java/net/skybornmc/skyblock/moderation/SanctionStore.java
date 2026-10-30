package net.skybornmc.skyblock.moderation;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.Sql;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Database side of sanctions (P7, spec 13.1; tables from {@code db/moderation/V008}). Every state change is one
 * conditional {@code UPDATE ... WHERE state = ?}, so two staff clicks can never both lift or restore the same sanction.
 * Money is removed and restored through the economy's guarded transactions ({@link #insertMoney},
 * {@link #claimMoneyRestore}): the sanction row and the balance change commit or roll back together.
 */
final class SanctionStore {

    static final String COLUMNS = "id, kind, target_uuid, target_name, profile_key, issuer_uuid, issuer_name, reason, created, "
            + "expires, amount, currency, item_text, state, handled_by_uuid, handled_by_name, handled_at, handled_reason";

    private final Database db;

    SanctionStore(Database db) {
        this.db = db;
    }

    static Sanction map(ResultSet rs) throws SQLException {
        return new Sanction(rs.getLong("id"), Sanction.Kind.valueOf(rs.getString("kind")), Sql.uuid(rs, "target_uuid"),
                rs.getString("target_name"), Sql.uuid(rs, "profile_key"), Sql.uuid(rs, "issuer_uuid"), rs.getString("issuer_name"),
                rs.getString("reason"), rs.getLong("created"), Sql.nullableLong(rs, "expires"), Sql.nullableLong(rs, "amount"),
                rs.getString("currency"), rs.getString("item_text"), Sanction.State.valueOf(rs.getString("state")),
                Sql.uuid(rs, "handled_by_uuid"), rs.getString("handled_by_name"), Sql.nullableLong(rs, "handled_at"),
                rs.getString("handled_reason"));
    }

    private static String clip(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static long insert(Connection c, Sanction s) throws SQLException {
        return Sql.insertReturningId(c, "INSERT INTO sanctions (kind, target_uuid, target_name, profile_key, issuer_uuid, "
                        + "issuer_name, reason, created, expires, amount, currency, item_text, state) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                s.kind(), s.target(), clip(s.targetName(), 32), s.profileKey(), s.issuer(), clip(s.issuerName(), 32),
                clip(s.reason(), 255), s.created(), s.expires(), s.amount(), s.currency(), clip(s.itemText(), 255), s.state());
    }

    static Optional<Sanction> byId(Connection c, long id) throws SQLException {
        return Sql.queryOne(c, "SELECT " + COLUMNS + " FROM sanctions WHERE id = ?", SanctionStore::map, id);
    }

    // ------------------------------------------------------------------ reads

    /** Every freeze/trade block still marked active (the in-memory copy is built from this). */
    CompletableFuture<List<Sanction>> activeBlocks() {
        return db.async(c -> Sql.query(c, "SELECT " + COLUMNS + " FROM sanctions WHERE state = 'ACTIVE' AND kind IN ('FREEZE', 'TRADE')",
                SanctionStore::map));
    }

    /** All sanctions of a player, newest first (history, the Sanctions menu). */
    CompletableFuture<List<Sanction>> history(UUID target) {
        return db.async(c -> Sql.query(c, "SELECT " + COLUMNS + " FROM sanctions WHERE target_uuid = ? ORDER BY created DESC, id DESC",
                SanctionStore::map, target));
    }

    CompletableFuture<Optional<Sanction>> get(long id) {
        return db.async(c -> byId(c, id));
    }

    /** Ids of money/items that can be restored (tab completion of {@code /psanction restore}), newest first. */
    CompletableFuture<List<Long>> restorableIds(int limit) {
        return db.async(c -> Sql.query(c, "SELECT id FROM sanctions WHERE state = 'ACTIVE' AND kind IN ('MONEY', 'ITEM') "
                + "ORDER BY id DESC LIMIT " + Math.max(1, limit), rs -> rs.getLong(1)));
    }

    /** How many sanctions {@code issuer} gave since {@code from} (/pstats "sanctions"). */
    CompletableFuture<Long> givenBy(UUID issuer, long from, long to) {
        return db.async(c -> Sql.queryOne(c, "SELECT COUNT(*) FROM sanctions WHERE issuer_uuid = ? AND created >= ? AND created < ? "
                + "AND state <> 'FAILED' AND state <> 'PENDING'", rs -> rs.getLong(1), issuer, from, to).orElse(0L));
    }

    // ------------------------------------------------------------------ freeze / block trading

    /** Outcome of starting a freeze/trade block. */
    record BlockResult(Sanction created, Sanction existing) {
        boolean ok() {
            return created != null;
        }
    }

    /**
     * Starts a freeze or trade block, unless one of the same kind is already in force for the player (then nothing is
     * written and {@code existing} is that one: staff lift it first to change the length). One transaction.
     */
    CompletableFuture<BlockResult> block(Sanction draft, long now) {
        return db.transaction(c -> {
            List<Sanction> active = Sql.query(c, "SELECT " + COLUMNS + " FROM sanctions WHERE target_uuid = ? AND kind = ? "
                    + "AND state = 'ACTIVE' FOR UPDATE", SanctionStore::map, draft.target(), draft.kind());
            for (Sanction s : active) {
                if (s.blocksAt(now)) {
                    return new BlockResult(null, s);
                }
            }
            long id = insert(c, draft);
            return new BlockResult(draft.withId(id), null);
        });
    }

    /** Lifts an active block (first click wins). Empty if it was not active anymore. */
    CompletableFuture<Optional<Sanction>> lift(long id, UUID by, String byName, String why, long now) {
        return db.transaction(c -> {
            int rows = Sql.update(c, "UPDATE sanctions SET state = 'LIFTED', handled_by_uuid = ?, handled_by_name = ?, handled_at = ?, "
                            + "handled_reason = ? WHERE id = ? AND state = 'ACTIVE' AND kind IN ('FREEZE', 'TRADE') "
                            + "AND (expires IS NULL OR expires > ?)",
                    by, clip(byName, 32), now, clip(why, 255), id, now);
            return rows == 1 ? byId(c, id) : Optional.<Sanction>empty();
        });
    }

    /** Marks blocks that ran out as EXPIRED; returns the ones this call marked (each audited once). */
    CompletableFuture<List<Sanction>> expire(List<Long> ids, long now) {
        return db.transaction(c -> {
            List<Sanction> done = new java.util.ArrayList<>();
            for (long id : ids) {
                if (Sql.update(c, "UPDATE sanctions SET state = 'EXPIRED', handled_at = ? WHERE id = ? AND state = 'ACTIVE' "
                        + "AND expires IS NOT NULL AND expires <= ?", now, id, now) == 1) {
                    byId(c, id).ifPresent(done::add);
                }
            }
            return done;
        });
    }

    // ------------------------------------------------------------------ money (inside the economy's transaction)

    /** Writes the MONEY sanction row on the economy's connection (guard of the withdraw); returns its id. */
    static long insertMoney(Connection c, Sanction draft) throws SQLException {
        return insert(c, draft);
    }

    /** Claims a MONEY sanction for restoring on the economy's connection (guard of the deposit); false = already done. */
    static boolean claimMoneyRestore(Connection c, long id, UUID by, String byName, String why, long now) throws SQLException {
        return Sql.update(c, "UPDATE sanctions SET state = 'RESTORED', handled_by_uuid = ?, handled_by_name = ?, handled_at = ?, "
                + "handled_reason = ? WHERE id = ? AND state = 'ACTIVE' AND kind = 'MONEY'", by, clip(byName, 32), now,
                clip(why, 255), id) == 1;
    }

    // ------------------------------------------------------------------ items

    /**
     * Step 1 of a confiscation: the sanction (PENDING, never restorable) and the serialized item in one transaction,
     * before the item leaves the player. Returns the sanction id.
     */
    CompletableFuture<Long> insertPendingItem(Sanction draft, byte[] item, String source, int slot) {
        return db.transaction(c -> {
            long id = insert(c, draft);
            Sql.update(c, "INSERT INTO confiscated_items (sanction_id, item, source, slot) VALUES (?, ?, ?, ?)", id, item,
                    source, slot);
            return id;
        });
    }

    /** Step 3: the item was taken (ACTIVE = held) or could not be taken (FAILED). Only from PENDING. */
    CompletableFuture<Boolean> settleItem(long id, boolean taken, long now) {
        return db.async(c -> Sql.update(c, "UPDATE sanctions SET state = ?, handled_at = ? WHERE id = ? AND state = 'PENDING'",
                taken ? Sanction.State.ACTIVE : Sanction.State.FAILED, taken ? null : now, id) == 1);
    }

    /** The held item of an ITEM sanction: claimed as RESTORED (first click wins) and returned. Empty = not held. */
    CompletableFuture<Optional<byte[]>> claimItemRestore(long id, UUID by, String byName, String why, long now) {
        return db.transaction(c -> {
            int rows = Sql.update(c, "UPDATE sanctions SET state = 'RESTORED', handled_by_uuid = ?, handled_by_name = ?, "
                    + "handled_at = ?, handled_reason = ? WHERE id = ? AND state = 'ACTIVE' AND kind = 'ITEM'", by,
                    clip(byName, 32), now, clip(why, 255), id);
            if (rows != 1) {
                return Optional.<byte[]>empty();
            }
            return Sql.queryOne(c, "SELECT item FROM confiscated_items WHERE sanction_id = ?", rs -> rs.getBytes(1), id);
        });
    }

    /** Undoes a restore claim when the item could not be handed over (the player left): held again. */
    CompletableFuture<Boolean> unclaimItemRestore(long id) {
        return db.async(c -> Sql.update(c, "UPDATE sanctions SET state = 'ACTIVE', handled_by_uuid = NULL, handled_by_name = NULL, "
                + "handled_at = NULL, handled_reason = NULL WHERE id = ? AND state = 'RESTORED' AND kind = 'ITEM'", id) == 1);
    }
}
