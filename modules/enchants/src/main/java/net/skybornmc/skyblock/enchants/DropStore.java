package net.skybornmc.skyblock.enchants;

import net.skybornmc.skyblock.common.db.Sql;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * SQL of the E2 book drops ({@code db/enchants/V002}): pity counters per profile, first-find progress per account and
 * season, and the atomic "drop a book" transaction. Methods take the caller's connection. No Bukkit (unit-tested on H2).
 */
final class DropStore {

    /** First-find progress of one activity. */
    record FirstFind(double actions, int granted) {
    }

    private final BookStore books;

    DropStore(BookStore books) {
        this.books = books;
    }

    // ------------------------------------------------------------------ pity (per profile)

    Map<String, Double> loadPity(Connection c, UUID profile) throws SQLException {
        Map<String, Double> out = new HashMap<>();
        for (Object[] r : Sql.query(c, "SELECT activity, since_book FROM enchant_pity WHERE profile_id = ?",
                rs -> new Object[]{rs.getString(1), rs.getDouble(2)}, profile)) {
            out.put((String) r[0], (Double) r[1]);
        }
        return out;
    }

    void savePity(Connection c, UUID profile, String activity, double since, long now) throws SQLException {
        double value = clean(since);
        if (Sql.update(c, "UPDATE enchant_pity SET since_book = ?, updated_at = ? WHERE profile_id = ? AND activity = ?",
                value, now, profile, activity) > 0) {
            return;
        }
        try {
            Sql.update(c, "INSERT INTO enchant_pity (profile_id, activity, since_book, updated_at) VALUES (?, ?, ?, ?)",
                    profile, activity, value, now);
        } catch (SQLException e) {
            if (!BookStore.duplicate(e)) {
                throw e;
            }
            Sql.update(c, "UPDATE enchant_pity SET since_book = ?, updated_at = ? WHERE profile_id = ? AND activity = ?",
                    value, now, profile, activity);
        }
    }

    // ------------------------------------------------------------------ first finds (per account and season)

    Map<String, FirstFind> loadFirstFinds(Connection c, UUID account, int season) throws SQLException {
        Map<String, FirstFind> out = new HashMap<>();
        for (Object[] r : Sql.query(c, "SELECT activity, actions, granted FROM enchant_first_finds WHERE account = ? AND season_id = ?",
                rs -> new Object[]{rs.getString(1), rs.getDouble(2), rs.getInt(3)}, account, season)) {
            out.put((String) r[0], new FirstFind((Double) r[1], (Integer) r[2]));
        }
        return out;
    }

    /** Writes the action count (never lowers it; never touches {@code granted}). */
    void saveFirstActions(Connection c, UUID account, int season, String activity, double actions, long now) throws SQLException {
        ensureFirst(c, account, season, activity, now);
        Sql.update(c, "UPDATE enchant_first_finds SET actions = ?, updated_at = ? WHERE account = ? AND season_id = ? AND activity = ? "
                + "AND actions < ?", clean(actions), now, account, season, activity, clean(actions));
    }

    /**
     * Gives out first-find milestone {@code from} (0-based): moves {@code granted} from {@code from} to {@code from + 1}.
     * False when another grant already moved it (so the same milestone can never pay twice).
     */
    boolean grantFirst(Connection c, UUID account, int season, String activity, int from, double actions, long now) throws SQLException {
        ensureFirst(c, account, season, activity, now);
        return Sql.update(c, "UPDATE enchant_first_finds SET granted = ?, actions = CASE WHEN actions < ? THEN ? ELSE actions END, "
                + "updated_at = ? WHERE account = ? AND season_id = ? AND activity = ? AND granted = ?",
                from + 1, clean(actions), clean(actions), now, account, season, activity, from) == 1;
    }

    private void ensureFirst(Connection c, UUID account, int season, String activity, long now) throws SQLException {
        if (Sql.queryOne(c, "SELECT 1 FROM enchant_first_finds WHERE account = ? AND season_id = ? AND activity = ?", rs -> 1,
                account, season, activity).isPresent()) {
            return;
        }
        try {
            Sql.update(c, "INSERT INTO enchant_first_finds (account, season_id, activity, actions, granted, updated_at) VALUES (?, ?, ?, 0, 0, ?)",
                    account, season, activity, now);
        } catch (SQLException e) {
            if (!BookStore.duplicate(e)) {
                throw e;
            }
        }
    }

    // ------------------------------------------------------------------ the drop

    /**
     * One dropped book, in one transaction: for a first find the milestone is claimed first (nothing is written when it was
     * already given out), then the book row and the collection entry are written and the profile's pity counter of that
     * activity starts over.
     *
     * @param firstFrom the first-find milestone index to claim, or -1 for a chance/pity/mini-boss book
     * @param resetPity whether this book resets the activity's pity counter (§3.4: any book from that activity)
     * @return false when the milestone was already given out (the caller must never hand out the item)
     */
    boolean mintDrop(Connection c, UUID bookId, String enchant, int level, int season, String origin, UUID owner, UUID profile,
                     String activity, int firstFrom, double accountActions, boolean resetPity, long now) throws SQLException {
        if (firstFrom >= 0 && !grantFirst(c, owner, season, activity, firstFrom, accountActions, now)) {
            return false;
        }
        books.insert(c, bookId, false, enchant, level, season, origin, owner, profile, now, "drop");
        books.markSeen(c, owner, season, enchant, now);
        if (resetPity) {
            savePity(c, profile, activity, 0, now);
        }
        return true;
    }

    private static double clean(double v) {
        return Double.isFinite(v) ? Math.max(0, v) : 0;
    }
}
