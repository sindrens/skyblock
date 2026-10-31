package net.skybornmc.skyblock.progression;

import net.skybornmc.skyblock.common.db.Sql;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

/**
 * SQL of the M5.7 leaderboard tables ({@code leaderboard_week_base}, {@code leaderboard_weekly},
 * {@code leaderboard_hidden}; MariaDB, H2 in MySQL mode for tests). Every method takes the caller's connection;
 * {@link #rollOver} must run in one transaction.
 */
final class WeeklyStore {

    /** Island id of the row that marks a week started on a server without islands. */
    static final UUID MARKER = new UUID(0, 0);

    /** An island's values at the start of a week. */
    record Base(double earned, int pillarLevels) {
    }

    /** One final place of a closed week. */
    record Place(String board, int place, UUID island, double score) {
    }

    Map<UUID, Base> loadBase(Connection c, int season, int week) throws SQLException {
        Map<UUID, Base> out = new HashMap<>();
        for (Object[] r : Sql.query(c, "SELECT island_id, earned_points, pillar_levels FROM leaderboard_week_base "
                        + "WHERE season_id = ? AND week_start = ?",
                rs -> new Object[]{Sql.uuid(rs, "island_id"), rs.getDouble("earned_points"), rs.getInt("pillar_levels")}, season, week)) {
            if (!MARKER.equals(r[0])) {
                out.put((UUID) r[0], new Base((Double) r[1], (Integer) r[2]));
            }
        }
        return out;
    }

    /** True once the week has a base (an empty server writes a marker row, see {@link #MARKER}). */
    boolean hasBase(Connection c, int season, int week) throws SQLException {
        return Sql.queryOne(c, "SELECT 1 FROM leaderboard_week_base WHERE season_id = ? AND week_start = ? LIMIT 1",
                rs -> rs.getInt(1), season, week).isPresent();
    }

    /** The latest week before {@code week} that has a base (the week to close when the server was down over Monday). */
    OptionalInt previousWeek(Connection c, int season, int week) throws SQLException {
        return Sql.queryOne(c, "SELECT MAX(week_start) FROM leaderboard_week_base WHERE season_id = ? AND week_start < ?",
                        rs -> {
                            int v = rs.getInt(1);
                            return rs.wasNull() ? null : v;
                        }, season, week)
                .map(OptionalInt::of).orElse(OptionalInt.empty());
    }

    /**
     * Closes {@code oldWeek} (its final places; skipped when {@code oldWeek} is null) and writes the base of
     * {@code newWeek}. Returns false and writes nothing when {@code newWeek} already has a base (closed before).
     * Run inside one transaction so the places and the next base exist together or not at all.
     */
    boolean rollOver(Connection c, int season, Integer oldWeek, List<Place> places, int newWeek, Map<UUID, Base> base)
            throws SQLException {
        if (hasBase(c, season, newWeek)) {
            return false;
        }
        if (oldWeek != null && !places.isEmpty()) {
            List<Object[]> rows = new ArrayList<>(places.size());
            for (Place p : places) {
                rows.add(new Object[]{season, oldWeek, p.board(), p.place(), Sql.toBytes(p.island()), p.score()});
            }
            Sql.batch(c, "INSERT INTO leaderboard_weekly (season_id, week_start, board, place, island_id, score, rewarded) "
                    + "VALUES (?, ?, ?, ?, ?, ?, 0)", rows);
        }
        List<Object[]> rows = new ArrayList<>(base.size() + 1);
        base.forEach((island, b) -> rows.add(new Object[]{season, newWeek, Sql.toBytes(island), b.earned(), b.pillarLevels()}));
        if (rows.isEmpty()) {
            // An empty server still marks the week as started, so the next week knows this one was closed.
            rows.add(new Object[]{season, newWeek, Sql.toBytes(MARKER), 0.0, 0});
        }
        Sql.batch(c, "INSERT INTO leaderboard_week_base (season_id, week_start, island_id, earned_points, pillar_levels) "
                + "VALUES (?, ?, ?, ?, ?)", rows);
        return true;
    }

    /** The final places of a closed week, by board then place. */
    List<Place> loadPlaces(Connection c, int season, int week) throws SQLException {
        return Sql.query(c, "SELECT board, place, island_id, score FROM leaderboard_weekly WHERE season_id = ? AND week_start = ? "
                        + "ORDER BY board, place",
                rs -> new Place(rs.getString("board"), rs.getInt("place"), Sql.uuid(rs, "island_id"), rs.getDouble("score")), season, week);
    }

    Set<UUID> loadHidden(Connection c) throws SQLException {
        return new HashSet<>(Sql.query(c, "SELECT account_id FROM leaderboard_hidden", rs -> Sql.uuid(rs, "account_id")));
    }

    /** Hides or shows a player's islands. Returns true if something changed. */
    boolean setHidden(Connection c, UUID account, boolean hidden, long now) throws SQLException {
        if (!hidden) {
            return Sql.update(c, "DELETE FROM leaderboard_hidden WHERE account_id = ?", Sql.toBytes(account)) > 0;
        }
        if (Sql.queryOne(c, "SELECT 1 FROM leaderboard_hidden WHERE account_id = ?", rs -> rs.getInt(1), Sql.toBytes(account)).isPresent()) {
            return false;
        }
        Sql.update(c, "INSERT INTO leaderboard_hidden (account_id, hidden_at) VALUES (?, ?)", Sql.toBytes(account), now);
        return true;
    }
}
