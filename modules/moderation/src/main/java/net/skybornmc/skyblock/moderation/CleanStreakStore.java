package net.skybornmc.skyblock.moderation;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.Sql;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Database side of the clean streak (spec 14.11): state per player, daily playtime samples, the newest breaking punishment. */
final class CleanStreakStore {

    private final Database db;

    CleanStreakStore(Database db) {
        this.db = db;
    }

    /** What one check needs, read in one go (today's sample is written first, old samples are pruned). */
    record Snapshot(CleanStreak.State state, Long lastBreaking, long played) {
    }

    /**
     * Writes today's playtime sample, reads the samples, the state and the newest breaking punishment still on record,
     * prunes samples older than the baseline. One transaction.
     */
    CompletableFuture<Snapshot> snapshot(UUID player, int today, int startDay, long totalPlaytime) {
        return db.transaction(c -> {
            byte[] id = Sql.toBytes(player);
            if (Sql.update(c, "UPDATE punish_playtime_days SET played_total_ms = ? WHERE player_uuid = ? AND play_day = ?",
                    totalPlaytime, id, today) == 0) {
                Sql.update(c, "INSERT INTO punish_playtime_days (player_uuid, play_day, played_total_ms) VALUES (?, ?, ?)",
                        id, today, totalPlaytime);
            }
            NavigableMap<Integer, Long> samples = new TreeMap<>();
            Sql.query(c, "SELECT play_day, played_total_ms FROM punish_playtime_days WHERE player_uuid = ?", rs -> {
                samples.put(rs.getInt("play_day"), rs.getLong("played_total_ms"));
                return 0;
            }, id);
            long played = CleanStreak.played(totalPlaytime, samples, startDay);
            Integer prune = CleanStreak.pruneBefore(samples, startDay);
            if (prune != null) {
                Sql.update(c, "DELETE FROM punish_playtime_days WHERE player_uuid = ? AND play_day < ?", id, prune);
            }
            return new Snapshot(state(c, player), lastBreaking(c, player), played);
        });
    }

    static CleanStreak.State state(Connection c, UUID player) throws SQLException {
        return Sql.queryOne(c, "SELECT streak_state, earned_at, lost_at, times_earned FROM punish_clean_streak WHERE player_uuid = ?",
                rs -> new CleanStreak.State("EARNED".equals(rs.getString("streak_state")), Sql.nullableLong(rs, "earned_at"),
                        Sql.nullableLong(rs, "lost_at"), rs.getInt("times_earned")), Sql.toBytes(player)).orElse(CleanStreak.State.NONE);
    }

    CompletableFuture<CleanStreak.State> state(UUID player) {
        return db.async(c -> state(c, player));
    }

    /** Newest warn, mute, ban or kick of the account that was not reversed ({@code null} = none). */
    static Long lastBreaking(Connection c, UUID player) throws SQLException {
        return Sql.queryOne(c, "SELECT MAX(created) AS last FROM punishments WHERE target_uuid = ? "
                        + "AND type IN ('WARN', 'MUTE', 'BAN', 'KICK') AND removed_at IS NULL",
                rs -> Sql.nullableLong(rs, "last"), Sql.toBytes(player)).orElse(null);
    }

    /** NONE/LOST → EARNED; {@code true} for exactly one caller (two checks at once never both announce). */
    CompletableFuture<Boolean> markEarned(UUID player, long now) {
        return db.transaction(c -> {
            byte[] id = Sql.toBytes(player);
            int n = Sql.update(c, "UPDATE punish_clean_streak SET streak_state = 'EARNED', earned_at = ?, times_earned = times_earned + 1, "
                    + "updated = ? WHERE player_uuid = ? AND streak_state <> 'EARNED'", now, now, id);
            if (n == 1) {
                return true;
            }
            if (Sql.queryOne(c, "SELECT 1 FROM punish_clean_streak WHERE player_uuid = ?", rs -> 1, id).isPresent()) {
                return false; // already EARNED
            }
            try {
                Sql.update(c, "INSERT INTO punish_clean_streak (player_uuid, streak_state, earned_at, times_earned, updated) "
                        + "VALUES (?, 'EARNED', ?, 1, ?)", id, now, now);
                return true;
            } catch (SQLException e) {
                if (e.getErrorCode() == 1062 || "23505".equals(e.getSQLState())) {
                    return false;
                }
                throw e;
            }
        });
    }

    /** EARNED → LOST; {@code true} for exactly one caller. */
    CompletableFuture<Boolean> markLost(UUID player, long now) {
        return db.async(c -> Sql.update(c, "UPDATE punish_clean_streak SET streak_state = 'LOST', lost_at = ?, updated = ? "
                + "WHERE player_uuid = ? AND streak_state = 'EARNED'", now, now, Sql.toBytes(player)) == 1);
    }
}
