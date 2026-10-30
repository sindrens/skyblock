package net.skybornmc.skyblock.box;

import net.skybornmc.skyblock.common.db.Sql;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** SQL of the box tables (same statements on MariaDB and H2 in MySQL mode). Callers run it off the main thread. */
final class BoxStore {

    private static final String COLUMNS = "entry_id, profile_key, item_data, material, amount, item_name, source_key, created_at";

    /**
     * Inserts every item for {@code profile} (call inside one transaction: all or nothing). An item whose
     * {@link BoxItem#ref} is already stored is skipped (a spooled delivery imported twice).
     *
     * @return the new entry ids (skipped items have none)
     */
    List<Long> insert(Connection c, UUID profile, Collection<BoxItem> items) throws SQLException {
        List<Long> ids = new ArrayList<>();
        for (BoxItem item : items) {
            if (item.ref() != null && Sql.queryOne(c, "SELECT entry_id FROM box_items WHERE delivery_ref = ?",
                    rs -> rs.getLong(1), item.ref()).isPresent()) {
                continue;
            }
            ids.add(Sql.insertReturningId(c, "INSERT INTO box_items (profile_key, item_data, material, amount, item_name, source_key, "
                            + "created_at, delivery_ref) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    profile, item.bytes(), item.material(), item.amount(), item.name(), item.source(), item.createdAt(), item.ref()));
        }
        return ids;
    }

    int count(Connection c, UUID profile) throws SQLException {
        return Sql.queryOne(c, "SELECT COUNT(*) FROM box_items WHERE profile_key = ?", rs -> rs.getInt(1), profile).orElse(0);
    }

    /** Counts of several profiles (missing = 0). */
    Map<UUID, Integer> counts(Connection c, Collection<UUID> profiles) throws SQLException {
        Map<UUID, Integer> out = new LinkedHashMap<>();
        for (UUID p : profiles) {
            out.put(p, count(c, p));
        }
        return out;
    }

    /** Oldest first, at most {@code limit} rows. */
    List<BoxEntry> list(Connection c, UUID profile, int limit) throws SQLException {
        return Sql.query(c, "SELECT " + COLUMNS + " FROM box_items WHERE profile_key = ? ORDER BY entry_id LIMIT " + Math.max(1, limit),
                BoxStore::entry, profile);
    }

    /**
     * Removes the given rows of {@code profile} (call inside one transaction). A row that is gone already (taken in
     * another click, cleared by staff) or belongs to another profile is skipped.
     *
     * @return exactly the rows this call removed: the caller must give these and only these
     */
    List<BoxEntry> take(Connection c, UUID profile, List<Long> ids) throws SQLException {
        List<BoxEntry> taken = new ArrayList<>();
        for (long id : ids) {
            var row = Sql.queryOne(c, "SELECT " + COLUMNS + " FROM box_items WHERE entry_id = ? AND profile_key = ?", BoxStore::entry, id, profile);
            if (row.isEmpty()) {
                continue;
            }
            if (Sql.update(c, "DELETE FROM box_items WHERE entry_id = ? AND profile_key = ?", id, profile) == 1) {
                taken.add(row.get());
            }
        }
        return taken;
    }

    /**
     * Moves rows into {@code box_cleared} (admin clear/remove). {@code ids == null} = every row of the profile.
     *
     * @return the rows moved
     */
    List<BoxEntry> archive(Connection c, UUID profile, List<Long> ids, UUID by, String byName, long now) throws SQLException {
        List<BoxEntry> rows = ids == null ? list(c, profile, Integer.MAX_VALUE) : new ArrayList<>();
        if (ids != null) {
            for (long id : ids) {
                Sql.queryOne(c, "SELECT " + COLUMNS + " FROM box_items WHERE entry_id = ? AND profile_key = ?", BoxStore::entry, id, profile)
                        .ifPresent(rows::add);
            }
        }
        List<BoxEntry> moved = new ArrayList<>();
        for (BoxEntry e : rows) {
            if (Sql.update(c, "DELETE FROM box_items WHERE entry_id = ? AND profile_key = ?", e.id(), profile) != 1) {
                continue;
            }
            Sql.update(c, "INSERT INTO box_cleared (entry_id, profile_key, item_data, material, amount, item_name, source_key, created_at, "
                            + "cleared_at, cleared_by, cleared_by_name) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    e.id(), e.profileKey(), e.bytes(), e.material(), e.amount(), e.name(), e.source(), e.createdAt(), now,
                    by == null ? null : by.toString(), byName == null ? "?" : byName.length() > 32 ? byName.substring(0, 32) : byName);
            moved.add(e);
        }
        return moved;
    }

    /** Rows admins cleared (staff history). */
    int clearedCount(Connection c, UUID profile) throws SQLException {
        return Sql.queryOne(c, "SELECT COUNT(*) FROM box_cleared WHERE profile_key = ?", rs -> rs.getInt(1), profile).orElse(0);
    }

    private static BoxEntry entry(ResultSet rs) throws SQLException {
        return new BoxEntry(rs.getLong("entry_id"), Sql.uuid(rs, "profile_key"), rs.getBytes("item_data"), rs.getString("material"),
                rs.getInt("amount"), rs.getString("item_name"), rs.getString("source_key"), rs.getLong("created_at"));
    }
}
