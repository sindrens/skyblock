package net.skybornmc.skyblock.depths.satchel;

import net.skybornmc.skyblock.common.db.Sql;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * SQL of the satchel ({@code depths_satchel_items}, {@code depths_satchel_upgrades}). Every method uses only the
 * connection it gets, so callers combine them in one transaction (e.g. the sale: batched adds + removal + payment).
 * Amounts can never go below 0: removals are conditional ({@code WHERE amount >= ?}).
 */
public final class SatchelStore {

    private static final String SELECT = "SELECT item, amount FROM depths_satchel_items WHERE profile = ? AND amount > 0";
    private static final String ADD = "UPDATE depths_satchel_items SET amount = amount + ?, updated_at = ? WHERE profile = ? AND item = ?";
    private static final String INSERT = "INSERT INTO depths_satchel_items (profile, item, amount, updated_at) VALUES (?, ?, ?, ?)";
    private static final String TAKE = "UPDATE depths_satchel_items SET amount = amount - ?, updated_at = ? WHERE profile = ? AND item = ? AND amount >= ?";
    private static final String CLEAN = "DELETE FROM depths_satchel_items WHERE profile = ? AND amount <= 0";
    private static final String UPGRADE_GET = "SELECT upgrade_level FROM depths_satchel_upgrades WHERE profile = ?";
    private static final String UPGRADE_SET = "UPDATE depths_satchel_upgrades SET upgrade_level = ?, updated_at = ? WHERE profile = ? AND upgrade_level = ?";
    private static final String UPGRADE_INSERT = "INSERT INTO depths_satchel_upgrades (profile, upgrade_level, updated_at) VALUES (?, ?, ?)";

    private SatchelStore() {
    }

    /** Every item a profile holds (amount above 0). */
    public static Map<String, Long> read(Connection c, UUID profile) throws SQLException {
        Map<String, Long> out = new LinkedHashMap<>();
        for (Object[] row : Sql.query(c, SELECT, rs -> new Object[]{rs.getString("item"), rs.getLong("amount")}, profile)) {
            out.put((String) row[0], (Long) row[1]);
        }
        return out;
    }

    /**
     * Applies batched changes: additions first, then removals (conditional). Returns false if a removal found too few
     * (the caller's transaction must then roll back; it can't happen while the cache is right).
     */
    public static boolean apply(Connection c, UUID profile, Map<String, Long> deltas, long now) throws SQLException {
        if (deltas == null || deltas.isEmpty()) {
            return true;
        }
        for (Map.Entry<String, Long> e : deltas.entrySet()) {
            long delta = e.getValue();
            if (delta > 0 && Sql.update(c, ADD, delta, now, profile, e.getKey()) == 0) {
                Sql.update(c, INSERT, profile, e.getKey(), delta, now);
            }
        }
        boolean removed = false;
        for (Map.Entry<String, Long> e : deltas.entrySet()) {
            long delta = e.getValue();
            if (delta < 0) {
                if (Sql.update(c, TAKE, -delta, now, profile, e.getKey(), -delta) == 0) {
                    return false;
                }
                removed = true;
            }
        }
        if (removed) {
            Sql.update(c, CLEAN, profile);
        }
        return true;
    }

    /** Removes items (all or nothing within the caller's transaction): false as soon as one row holds too few. */
    public static boolean take(Connection c, UUID profile, Map<String, Long> amounts, long now) throws SQLException {
        for (Map.Entry<String, Long> e : amounts.entrySet()) {
            long n = e.getValue();
            if (n <= 0) {
                continue;
            }
            if (Sql.update(c, TAKE, n, now, profile, e.getKey(), n) == 0) {
                return false;
            }
        }
        Sql.update(c, CLEAN, profile);
        return true;
    }

    /** Bought upgrade level (0 = none). */
    public static int upgradeLevel(Connection c, UUID profile) throws SQLException {
        return Sql.queryOne(c, UPGRADE_GET, rs -> rs.getInt(1), profile).orElse(0);
    }

    /** Raises the upgrade level from {@code from} to {@code to}; false if it is not {@code from} any more (bought twice). */
    public static boolean setUpgrade(Connection c, UUID profile, int from, int to, long now) throws SQLException {
        if (from == 0 && Sql.queryOne(c, UPGRADE_GET, rs -> rs.getInt(1), profile).isEmpty()) {
            Sql.update(c, UPGRADE_INSERT, profile, to, now);
            return true;
        }
        return Sql.update(c, UPGRADE_SET, to, now, profile, from) == 1;
    }

    /** Removes everything of a deleted profile. */
    public static void deleteProfile(Connection c, UUID profile) throws SQLException {
        Sql.update(c, "DELETE FROM depths_satchel_items WHERE profile = ?", profile);
        Sql.update(c, "DELETE FROM depths_satchel_upgrades WHERE profile = ?", profile);
    }
}
