package net.skybornmc.skyblock.moderation;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.Sql;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Guard state per staff member (spec 14.1, table {@code punish_guard}, V010). Account-wide (player uuid). */
final class GuardStore {

    private final Database db;

    GuardStore(Database db) {
        this.db = db;
    }

    private static Guard.Entry map(java.sql.ResultSet rs) throws java.sql.SQLException {
        Guard.State state;
        try {
            state = Guard.State.valueOf(rs.getString("guard_state"));
        } catch (IllegalArgumentException e) {
            state = Guard.State.BLOCKED; // unknown value: the safe reading
        }
        return new Guard.Entry(Sql.uuid(rs, "staff_uuid"), rs.getString("staff_name"), state,
                Sql.nullableLong(rs, "allowed_until"), rs.getString("hit_text"), rs.getString("set_by_name"), rs.getLong("updated"));
    }

    /** Every row (loaded once at start; one row per staff member at most). */
    CompletableFuture<List<Guard.Entry>> all() {
        return db.async(c -> Sql.query(c, "SELECT staff_uuid, staff_name, guard_state, allowed_until, hit_text, set_by_name, "
                + "updated FROM punish_guard", GuardStore::map));
    }

    /** Stores the row of {@code e.staff()} (replaces it, one transaction). */
    CompletableFuture<Void> save(Guard.Entry e) {
        return db.transaction(c -> {
            Sql.update(c, "DELETE FROM punish_guard WHERE staff_uuid = ?", Sql.toBytes(e.staff()));
            Sql.update(c, "INSERT INTO punish_guard (staff_uuid, staff_name, guard_state, allowed_until, hit_text, set_by_name, "
                            + "updated) VALUES (?, ?, ?, ?, ?, ?, ?)", Sql.toBytes(e.staff()), clip(e.staffName()), e.state().name(),
                    e.until(), e.hit() == null ? null : e.hit().length() > 32 ? e.hit().substring(0, 32) : e.hit(),
                    clip(e.by()), e.updated());
            return null;
        });
    }

    /** Removes the row (back to normal). Completes with whether one existed. */
    CompletableFuture<Boolean> delete(UUID staff) {
        return db.async(c -> Sql.update(c, "DELETE FROM punish_guard WHERE staff_uuid = ?", Sql.toBytes(staff)) > 0);
    }

    private static String clip(String s) {
        return s == null ? "?" : s.length() <= 32 ? s : s.substring(0, 32);
    }
}
