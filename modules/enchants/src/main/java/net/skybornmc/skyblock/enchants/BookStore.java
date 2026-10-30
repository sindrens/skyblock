package net.skybornmc.skyblock.enchants;

import net.skybornmc.skyblock.common.db.Sql;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * All enchant SQL (tables in {@code db/enchants}). Methods take the caller's connection so they can run inside one
 * transaction with the crystal payment and the tool's perks. No Bukkit here (unit-tested on H2).
 */
final class BookStore {

    enum State {
        ACTIVE, CONSUMED, VOID;

        String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        static State byKey(String key) {
            for (State s : values()) {
                if (s.key().equalsIgnoreCase(key)) {
                    return s;
                }
            }
            return VOID;
        }
    }

    /** One row of {@code enchant_books}. */
    record Row(UUID id, boolean scroll, String enchant, int level, State state, String origin, UUID createdFor, long createdAt,
               String reason) {
    }

    static final String KIND_BOOK = "book";
    static final String KIND_SCROLL = "scroll";

    private static final String COLUMNS = "id, kind, enchant_id, level, state, origin, created_for, created_at, reason";

    /**
     * @param createdFor the player (account) it was made for, or null
     * @param profile    that player's profile key at the time (books are profile items), or null
     */
    void insert(Connection c, UUID id, boolean scroll, String enchant, int level, int season, String origin, UUID createdFor,
                UUID profile, long now, String reason) throws SQLException {
        Sql.update(c, "INSERT INTO enchant_books (id, kind, enchant_id, level, state, season_id, origin, created_for, profile_id, "
                        + "created_at, changed_at, changed_by, reason) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, NULL, ?)",
                id, scroll ? KIND_SCROLL : KIND_BOOK, scroll ? null : enchant, scroll ? 0 : level, State.ACTIVE.key(), season,
                clip(origin, 64), createdFor, profile, now, clip(reason, 64));
    }

    Optional<Row> find(Connection c, UUID id) throws SQLException {
        return Sql.queryOne(c, "SELECT " + COLUMNS + " FROM enchant_books WHERE id = ?", BookStore::row, id);
    }

    /** Locks the row for the rest of the transaction. */
    Optional<Row> lock(Connection c, UUID id) throws SQLException {
        return Sql.queryOne(c, "SELECT " + COLUMNS + " FROM enchant_books WHERE id = ? FOR UPDATE", BookStore::row, id);
    }

    /** {@code from → to} if the row is still in {@code from}; true when it changed. */
    boolean move(Connection c, UUID id, State from, State to, UUID by, long now, String reason) throws SQLException {
        return Sql.update(c, "UPDATE enchant_books SET state = ?, changed_at = ?, changed_by = ?, reason = ? WHERE id = ? AND state = ?",
                to.key(), now, by, clip(reason, 64), id, from.key()) == 1;
    }

    /** Undo of a consume: back to active only if the same operation consumed it. */
    boolean reactivate(Connection c, UUID id, String reason, long now) throws SQLException {
        return Sql.update(c, "UPDATE enchant_books SET state = ?, changed_at = ?, reason = ? WHERE id = ? AND state = ? AND reason = ?",
                State.ACTIVE.key(), now, clip("undo:" + reason, 64), id, State.CONSUMED.key(), clip(reason, 64)) == 1;
    }

    // ------------------------------------------------------------------ collection

    /** Records that a player has seen an enchant this season (no-op if already). */
    void markSeen(Connection c, UUID player, int season, String enchant, long now) throws SQLException {
        if (Sql.queryOne(c, "SELECT 1 FROM enchant_seen WHERE player = ? AND season_id = ? AND enchant_id = ?", rs -> 1,
                player, season, enchant).isPresent()) {
            return;
        }
        try {
            Sql.update(c, "INSERT INTO enchant_seen (player, season_id, enchant_id, first_at) VALUES (?, ?, ?, ?)", player, season, enchant, now);
        } catch (SQLException e) {
            if (!duplicate(e)) {
                throw e;
            }
        }
    }

    Set<String> seen(Connection c, UUID player, int season) throws SQLException {
        return new LinkedHashSet<>(Sql.query(c, "SELECT enchant_id FROM enchant_seen WHERE player = ? AND season_id = ?",
                rs -> rs.getString(1), player, season));
    }

    // ------------------------------------------------------------------ daily rolls

    /** Uses one roll of today; false when the limit is reached. Inside a transaction. */
    boolean useDaily(Connection c, UUID player, String day, String roll, int limit) throws SQLException {
        if (limit <= 0) {
            return false;
        }
        if (Sql.queryOne(c, "SELECT used FROM enchant_daily WHERE player = ? AND roll_day = ? AND roll = ? FOR UPDATE", rs -> rs.getInt(1),
                player, day, roll).isEmpty()) {
            try {
                Sql.update(c, "INSERT INTO enchant_daily (player, roll_day, roll, used) VALUES (?, ?, ?, 0)", player, day, roll);
            } catch (SQLException e) {
                if (!duplicate(e)) {
                    throw e;
                }
            }
        }
        return Sql.update(c, "UPDATE enchant_daily SET used = used + 1 WHERE player = ? AND roll_day = ? AND roll = ? AND used < ?",
                player, day, roll, limit) == 1;
    }

    /** Undo of {@link #useDaily}. */
    void returnDaily(Connection c, UUID player, String day, String roll) throws SQLException {
        Sql.update(c, "UPDATE enchant_daily SET used = used - 1 WHERE player = ? AND roll_day = ? AND roll = ? AND used > 0", player, day, roll);
    }

    /** Rolls used today: roll id → count. */
    Map<String, Integer> daily(Connection c, UUID player, String day) throws SQLException {
        Map<String, Integer> out = new HashMap<>();
        for (Object[] r : Sql.query(c, "SELECT roll, used FROM enchant_daily WHERE player = ? AND roll_day = ?",
                rs -> new Object[]{rs.getString(1), rs.getInt(2)}, player, day)) {
            out.put((String) r[0], (Integer) r[1]);
        }
        return out;
    }

    // ------------------------------------------------------------------ helpers

    private static Row row(ResultSet rs) throws SQLException {
        return new Row(Sql.uuid(rs, "id"), KIND_SCROLL.equals(rs.getString("kind")), rs.getString("enchant_id"), rs.getInt("level"),
                State.byKey(rs.getString("state")), rs.getString("origin"), Sql.uuid(rs, "created_for"), rs.getLong("created_at"),
                rs.getString("reason"));
    }

    /** Unique key violation (SQLState class 23; MariaDB 1062, H2 23505). */
    static boolean duplicate(SQLException e) {
        String state = e.getSQLState();
        return (state != null && state.startsWith("23")) || e.getErrorCode() == 1062 || e.getErrorCode() == 23505;
    }

    static String clip(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() > max ? text.substring(0, max) : text;
    }
}
