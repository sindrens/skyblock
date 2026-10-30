package net.skybornmc.skyblock.moderation;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.Sql;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Per staff member notification switches (spec 14.9, table {@code staff_notify_settings}, V009) and the dry-run log
 * (spec 14.6, table {@code punish_dry_runs}). Account-wide (player uuid). A missing row means "on" ({@link NotifyRules}).
 */
final class NotifyStore {

    private final Database db;

    NotifyStore(Database db) {
        this.db = db;
    }

    /** Every stored switch, staff uuid → type → on (loaded once at start; the table is tiny: staff × types). */
    CompletableFuture<Map<UUID, Map<NotifyRules.Type, Boolean>>> loadAll() {
        return db.async(c -> {
            Map<UUID, Map<NotifyRules.Type, Boolean>> out = new HashMap<>();
            for (Row r : Sql.query(c, "SELECT staff_uuid, notify_type, notify_on FROM staff_notify_settings", NotifyStore::row)) {
                NotifyRules.Type.parse(r.type()).ifPresent(t ->
                        out.computeIfAbsent(r.staff(), k -> new EnumMap<>(NotifyRules.Type.class)).put(t, r.on()));
            }
            return out;
        });
    }

    /** One staff member's stored switches. */
    CompletableFuture<Map<NotifyRules.Type, Boolean>> load(UUID staff) {
        return db.async(c -> {
            Map<NotifyRules.Type, Boolean> out = new EnumMap<>(NotifyRules.Type.class);
            for (Row r : Sql.query(c, "SELECT staff_uuid, notify_type, notify_on FROM staff_notify_settings WHERE staff_uuid = ?",
                    NotifyStore::row, Sql.toBytes(staff))) {
                NotifyRules.Type.parse(r.type()).ifPresent(t -> out.put(t, r.on()));
            }
            return out;
        });
    }

    private record Row(UUID staff, String type, boolean on) {
    }

    private static Row row(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Row(Sql.uuid(rs, "staff_uuid"), rs.getString("notify_type"), rs.getBoolean("notify_on"));
    }

    /**
     * Stores one switch (update, else insert, in one transaction).
     *
     * @return the previous value ({@code null} = nothing stored, i.e. it was on by default)
     */
    CompletableFuture<Boolean> set(UUID staff, NotifyRules.Type type, boolean on, long now) {
        return db.transaction(c -> {
            Boolean before = Sql.queryOne(c, "SELECT notify_on FROM staff_notify_settings WHERE staff_uuid = ? AND notify_type = ?",
                    rs -> rs.getBoolean("notify_on"), Sql.toBytes(staff), type.id()).orElse(null);
            if (Sql.update(c, "UPDATE staff_notify_settings SET notify_on = ?, updated = ? WHERE staff_uuid = ? AND notify_type = ?",
                    on, now, Sql.toBytes(staff), type.id()) == 0) {
                Sql.update(c, "INSERT INTO staff_notify_settings (staff_uuid, notify_type, notify_on, updated) VALUES (?, ?, ?, ?)",
                        Sql.toBytes(staff), type.id(), on, now);
            }
            return before;
        });
    }

    // ------------------------------------------------------------------ dry runs (spec 14.6)

    /** One dry run as logged: what would have happened, never applied. */
    record DryRunRow(UUID staff, String staffName, boolean trainee, UUID target, String targetName, String reasonKey,
                     String verdict, String action, long created) {
    }

    CompletableFuture<Long> logDryRun(DryRunRow r) {
        return db.async(c -> Sql.insertReturningId(c, "INSERT INTO punish_dry_runs (staff_uuid, staff_name, trainee, target_uuid, "
                        + "target_name, reason_key, verdict, action_text, created) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                r.staff() == null ? null : Sql.toBytes(r.staff()), clip(r.staffName(), 32), r.trainee(),
                r.target() == null ? null : Sql.toBytes(r.target()), clip(r.targetName(), 32), clip(r.reasonKey(), 48),
                clip(r.verdict(), 12), clip(r.action(), 64), r.created()));
    }

    /** Dry runs of one staff member since {@code since} (/pstats for trainees). */
    CompletableFuture<Long> dryRunCount(UUID staff, long since) {
        return db.async(c -> Sql.queryOne(c, "SELECT COUNT(*) AS n FROM punish_dry_runs WHERE staff_uuid = ? AND created >= ?",
                rs -> rs.getLong("n"), Sql.toBytes(staff), since).orElse(0L));
    }

    /** The newest dry runs of one staff member, newest first (tests and a later trainee view). */
    CompletableFuture<List<DryRunRow>> dryRuns(UUID staff, int limit) {
        return db.async(c -> Sql.query(c, "SELECT staff_uuid, staff_name, trainee, target_uuid, target_name, reason_key, verdict, "
                        + "action_text, created FROM punish_dry_runs WHERE staff_uuid = ? ORDER BY created DESC, id DESC LIMIT "
                        + Math.max(1, limit),
                rs -> new DryRunRow(Sql.uuid(rs, "staff_uuid"), rs.getString("staff_name"), rs.getBoolean("trainee"),
                        Sql.uuid(rs, "target_uuid"), rs.getString("target_name"), rs.getString("reason_key"),
                        rs.getString("verdict"), rs.getString("action_text"), rs.getLong("created")), Sql.toBytes(staff)));
    }

    private static String clip(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
