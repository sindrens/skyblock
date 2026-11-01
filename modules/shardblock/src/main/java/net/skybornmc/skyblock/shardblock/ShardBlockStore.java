package net.skybornmc.skyblock.shardblock;

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
 * SQL of the shard blocks ({@code db/shardblock}). Bukkit-free. Every state change is a conditional UPDATE that must hit
 * exactly one row; the payout guard ({@link #collectGuard}) runs inside the economy's transaction, so the shard, the block
 * state and the daily counter commit or roll back together.
 */
final class ShardBlockStore {

    static final String READY = "ready";
    static final String REGROWING = "regrowing";

    private static final String COLUMNS = "block_id, world, x, y, z, island_id, placed_by, respawn_level, double_level, state, "
            + "ready_at, breaks, shards_paid";

    /** One placed shard block as stored. */
    record Row(long blockId, String world, int x, int y, int z, UUID island, UUID placedBy, int respawnLevel, int doubleLevel,
               String state, long readyAt, long breaks, long shardsPaid) {
    }

    private final Database db;

    ShardBlockStore(Database db) {
        this.db = db;
    }

    /** Every placed block (loaded once at enable; a few per island). */
    CompletableFuture<List<Row>> loadAll() {
        return db.async(c -> Sql.query(c, "SELECT " + COLUMNS + " FROM shard_blocks", ShardBlockStore::row));
    }

    CompletableFuture<Optional<Row>> get(long blockId) {
        return db.async(c -> Sql.queryOne(c, "SELECT " + COLUMNS + " FROM shard_blocks WHERE block_id = ?", ShardBlockStore::row, blockId));
    }

    /** The row at a position (used after an unclear insert result). */
    CompletableFuture<Optional<Row>> at(String world, int x, int y, int z) {
        return db.async(c -> Sql.queryOne(c, "SELECT " + COLUMNS + " FROM shard_blocks WHERE world = ? AND x = ? AND y = ? AND z = ?",
                ShardBlockStore::row, world, x, y, z));
    }

    /** Inserts a placed block (ready) and returns its id. A taken position fails (unique key). */
    CompletableFuture<Long> insert(String world, int x, int y, int z, UUID island, UUID placedBy, int respawnLevel, int doubleLevel, long now) {
        return db.async(c -> Sql.insertReturningId(c, "INSERT INTO shard_blocks (world, x, y, z, island_id, placed_by, respawn_level, "
                        + "double_level, state, ready_at, breaks, shards_paid, placed_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0, 0, ?, ?)",
                world, x, y, z, island, placedBy, respawnLevel, doubleLevel, READY, now, now));
    }

    /**
     * Moves a block into the archive and deletes it, in one transaction. Returns the archived row, or empty when it was
     * already gone (a second pick-up, a race with staff): only the caller that got the row may hand out the item.
     */
    CompletableFuture<Optional<Row>> archive(long blockId, String reason, String by, long now) {
        return db.transaction(c -> archiveIn(c, blockId, reason, by, now));
    }

    private static Optional<Row> archiveIn(Connection c, long blockId, String reason, String by, long now) throws SQLException {
        Optional<Row> row = Sql.queryOne(c, "SELECT " + COLUMNS + " FROM shard_blocks WHERE block_id = ?", ShardBlockStore::row, blockId);
        if (row.isEmpty()) {
            return Optional.empty();
        }
        Row r = row.get();
        if (Sql.update(c, "DELETE FROM shard_blocks WHERE block_id = ?", blockId) != 1) {
            return Optional.empty();
        }
        Sql.update(c, "INSERT INTO shard_blocks_archive (block_id, world, x, y, z, island_id, placed_by, respawn_level, double_level, "
                        + "breaks, shards_paid, reason, archived_by, archived_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                r.blockId(), r.world(), r.x(), r.y(), r.z(), r.island(), r.placedBy(), r.respawnLevel(), r.doubleLevel(), r.breaks(),
                r.shardsPaid(), reason, by, now);
        return row;
    }

    /** Archives every block of an island (island removed). Returns the archived rows. */
    CompletableFuture<List<Row>> archiveIsland(UUID island, String reason, String by, long now) {
        return db.transaction(c -> {
            List<Row> rows = Sql.query(c, "SELECT " + COLUMNS + " FROM shard_blocks WHERE island_id = ?", ShardBlockStore::row, island);
            List<Row> done = new java.util.ArrayList<>();
            for (Row r : rows) {
                archiveIn(c, r.blockId(), reason, by, now).ifPresent(done::add);
            }
            return done;
        });
    }

    /**
     * The payout guard (runs inside the economy transaction, may run twice): the block must still be ready (state ready, or
     * regrowing with its time passed) with exactly {@code breaks} breaks; it becomes regrowing until {@code readyAt}. Then
     * the daily counter of the profile grows by {@code amount} only while it stays within {@code cap} (0 = no cap).
     *
     * @return false = nothing may be paid (block changed or cap reached); the caller's transaction rolls back
     */
    static boolean collectGuard(Connection c, long blockId, long breaks, long now, long readyAt, int amount, UUID profile, int day, int cap)
            throws SQLException {
        if (Sql.update(c, "UPDATE shard_blocks SET state = ?, ready_at = ?, breaks = breaks + 1, shards_paid = shards_paid + ?, "
                        + "updated_at = ? WHERE block_id = ? AND breaks = ? AND (state = ? OR ready_at <= ?)",
                REGROWING, readyAt, amount, now, blockId, breaks, READY, now) != 1) {
            return false;
        }
        Sql.update(c, "INSERT INTO shard_block_daily (profile_key, day, shards) VALUES (?, ?, 0) ON DUPLICATE KEY UPDATE shards = shards",
                profile, day);
        if (cap <= 0) {
            return Sql.update(c, "UPDATE shard_block_daily SET shards = shards + ? WHERE profile_key = ? AND day = ?", amount, profile, day) == 1;
        }
        return Sql.update(c, "UPDATE shard_block_daily SET shards = shards + ? WHERE profile_key = ? AND day = ? AND shards + ? <= ?",
                amount, profile, day, amount, cap) == 1;
    }

    /** The upgrade guard: the level must still be {@code from}; it becomes {@code from + 1} (two buyers pay once). */
    static boolean upgradeGuard(Connection c, long blockId, ShardRules.Kind kind, int from, long now) throws SQLException {
        String col = kind.column();
        return Sql.update(c, "UPDATE shard_blocks SET " + col + " = " + col + " + 1, updated_at = ? WHERE block_id = ? AND " + col + " = ?",
                now, blockId, from) == 1;
    }

    /** Shards a profile got from shard blocks on {@code day}. */
    CompletableFuture<Long> dailyUsed(UUID profile, int day) {
        return db.async(c -> Sql.queryOne(c, "SELECT shards FROM shard_block_daily WHERE profile_key = ? AND day = ?",
                rs -> rs.getLong(1), profile, day).orElse(0L));
    }

    /** Deletes daily-cap rows older than {@code beforeDay}. */
    CompletableFuture<Integer> purgeDays(int beforeDay) {
        return db.async(c -> Sql.update(c, "DELETE FROM shard_block_daily WHERE day < ?", beforeDay));
    }

    /** Staff stats: blocks, shards today, shards ever (live + archived blocks), top islands by blocks. */
    record Stats(long blocks, long today, long total, List<java.util.Map.Entry<UUID, Long>> top) {
    }

    CompletableFuture<Stats> stats(int day, int top) {
        return db.async(c -> {
            long blocks = Sql.queryOne(c, "SELECT COUNT(*) FROM shard_blocks", rs -> rs.getLong(1)).orElse(0L);
            long today = Sql.queryOne(c, "SELECT COALESCE(SUM(shards), 0) FROM shard_block_daily WHERE day = ?", rs -> rs.getLong(1), day).orElse(0L);
            long live = Sql.queryOne(c, "SELECT COALESCE(SUM(shards_paid), 0) FROM shard_blocks", rs -> rs.getLong(1)).orElse(0L);
            long archived = Sql.queryOne(c, "SELECT COALESCE(SUM(shards_paid), 0) FROM shard_blocks_archive", rs -> rs.getLong(1)).orElse(0L);
            List<java.util.Map.Entry<UUID, Long>> list = Sql.query(c, "SELECT island_id, COUNT(*) AS n FROM shard_blocks GROUP BY island_id "
                            + "ORDER BY n DESC LIMIT " + Math.max(1, top),
                    rs -> java.util.Map.entry(Sql.uuid(rs, "island_id"), rs.getLong("n")));
            return new Stats(blocks, today, live + archived, list);
        });
    }

    static Row row(ResultSet rs) throws SQLException {
        return new Row(rs.getLong("block_id"), rs.getString("world"), rs.getInt("x"), rs.getInt("y"), rs.getInt("z"),
                Sql.uuid(rs, "island_id"), Sql.uuid(rs, "placed_by"), rs.getInt("respawn_level"), rs.getInt("double_level"),
                rs.getString("state"), rs.getLong("ready_at"), rs.getLong("breaks"), rs.getLong("shards_paid"));
    }
}
