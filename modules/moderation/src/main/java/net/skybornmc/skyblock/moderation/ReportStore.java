package net.skybornmc.skyblock.moderation;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.Sql;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Database side of the monthly report (spec 14.8). Every read covers one month on an index (V011) and returns only the
 * columns the report needs: never player names, IPs, reason texts, evidence or notes. Runs on the database thread.
 */
final class ReportStore {

    private final Database db;

    ReportStore(Database db) {
        this.db = db;
    }

    /** A stored report row. */
    record Stored(String month, long generatedAt, String generatedBy, String json, boolean notified) {
    }

    /**
     * Reads everything one report needs (guard events come from the audit log and are added by the caller).
     *
     * @param falseReason reason id of false-report suggestions ({@code extras.false-reports.reason})
     */
    CompletableFuture<MonthlyReport.Input> input(YearMonth month, ZoneId zone, MonthlyReport.Settings settings, long now,
                                                 String falseReason) {
        return db.async(c -> read(c, month, zone, settings, now, falseReason));
    }

    static MonthlyReport.Input read(Connection c, YearMonth month, ZoneId zone, MonthlyReport.Settings settings, long now,
                                    String falseReason) throws SQLException {
        long start = MonthlyReport.start(month, zone);
        long end = MonthlyReport.end(month, zone);
        long[] range = MonthlyReport.punishmentRange(month, zone, settings);
        List<MonthlyReport.PRow> punishments = Sql.query(c, "SELECT type, target_uuid, template, ladder_step, issuer_uuid, "
                        + "issuer_name, created, removed_at, removed_kind, rollback_id FROM punishments WHERE created >= ? AND created < ?",
                rs -> {
                    int step = rs.getInt("ladder_step");
                    Integer ladder = rs.wasNull() ? null : step;
                    return new MonthlyReport.PRow(Punishment.Type.valueOf(rs.getString("type")), Sql.uuid(rs, "target_uuid"),
                            rs.getString("template"), ladder, Sql.uuid(rs, "issuer_uuid"), rs.getString("issuer_name"),
                            rs.getLong("created"), MonthlyReport.Reversal.of(Sql.nullableLong(rs, "removed_at"),
                            rs.getString("removed_kind"), Sql.nullableLong(rs, "rollback_id")));
                }, range[0], range[1]);
        List<MonthlyReport.SanctionRow> sanctions = Sql.query(c, "SELECT issuer_uuid, issuer_name, created FROM sanctions "
                        + "WHERE created >= ? AND created < ?",
                rs -> new MonthlyReport.SanctionRow(Sql.uuid(rs, "issuer_uuid"), rs.getString("issuer_name"), rs.getLong("created")),
                start, end);
        List<MonthlyReport.AppealRow> appeals = Sql.query(c, "SELECT handler_uuid, handler_name, status, created, handled "
                        + "FROM appeals WHERE (created >= ? AND created < ?) OR (handled >= ? AND handled < ?)",
                rs -> new MonthlyReport.AppealRow(Sql.uuid(rs, "handler_uuid"), rs.getString("handler_name"), rs.getString("status"),
                        rs.getLong("created"), Sql.nullableLong(rs, "handled")), start, end, start, end);
        List<MonthlyReport.RequestRow> requests = Sql.query(c, "SELECT requester_uuid, requester_name, requester_trainee, "
                        + "handler_uuid, handler_name, status, created, expires FROM punishment_requests WHERE created >= ? AND created < ?",
                rs -> new MonthlyReport.RequestRow(Sql.uuid(rs, "requester_uuid"), rs.getString("requester_name"),
                        rs.getBoolean("requester_trainee"), Sql.uuid(rs, "handler_uuid"), rs.getString("handler_name"),
                        rs.getString("status"), rs.getLong("created"), rs.getLong("expires")), start, end);
        List<MonthlyReport.DryRow> dry = Sql.query(c, "SELECT staff_uuid, staff_name, trainee, created FROM punish_dry_runs "
                        + "WHERE created >= ? AND created < ?",
                rs -> new MonthlyReport.DryRow(Sql.uuid(rs, "staff_uuid"), rs.getString("staff_name"), rs.getBoolean("trainee"),
                        rs.getLong("created")), start, end);
        long falseSuggestions = falseReason == null ? 0 : Sql.queryOne(c, "SELECT COUNT(*) AS n FROM punishment_suggestions "
                + "WHERE kind = 'api' AND reason_id = ? AND created >= ? AND created < ?", rs -> rs.getLong("n"),
                falseReason, start, end).orElse(0L);
        List<MonthlyReport.ReportRow> reports;
        try {
            // the staff module's table (optional: null = reports not installed)
            reports = Sql.query(c, "SELECT created_at, closed_at, outcome FROM reports WHERE (created_at >= ? AND created_at < ?) "
                            + "OR (closed_at >= ? AND closed_at < ?)",
                    rs -> new MonthlyReport.ReportRow(rs.getLong("created_at"), Sql.nullableLong(rs, "closed_at"), rs.getString("outcome")),
                    start, end, start, end);
        } catch (SQLException e) {
            reports = null;
        }
        return new MonthlyReport.Input(month, zone, now, punishments, sanctions, appeals, requests, reports, dry, List.of(),
                falseSuggestions);
    }

    /** Stores a report; {@code false} when that month is already stored (the first one stays). */
    CompletableFuture<Boolean> save(MonthlyReport.Report report, String by) {
        return db.async(c -> {
            if (Sql.queryOne(c, "SELECT 1 FROM punish_reports WHERE report_month = ?", rs -> 1, report.month()).isPresent()) {
                return false;
            }
            try {
                Sql.update(c, "INSERT INTO punish_reports (report_month, generated_at, generated_by, report_json, notified) "
                        + "VALUES (?, ?, ?, ?, FALSE)", report.month(), report.generatedAt(), clip(by, 32), MonthlyReport.toJson(report));
                return true;
            } catch (SQLException e) {
                if (e.getErrorCode() == 1062 || "23505".equals(e.getSQLState())) {
                    return false; // stored at the same moment elsewhere
                }
                throw e;
            }
        });
    }

    CompletableFuture<Optional<Stored>> load(String month) {
        return db.async(c -> Sql.queryOne(c, "SELECT report_month, generated_at, generated_by, report_json, notified "
                + "FROM punish_reports WHERE report_month = ?", rs -> new Stored(rs.getString("report_month"),
                rs.getLong("generated_at"), rs.getString("generated_by"), rs.getString("report_json"), rs.getBoolean("notified")), month));
    }

    /** Stored month keys, newest first (max 120). */
    CompletableFuture<List<String>> months() {
        return db.async(c -> Sql.query(c, "SELECT report_month FROM punish_reports ORDER BY report_month DESC LIMIT 120",
                rs -> rs.getString("report_month")));
    }

    /** Announced month keys (the scheduled run skips them). */
    CompletableFuture<List<String>> announced() {
        return db.async(c -> Sql.query(c, "SELECT report_month FROM punish_reports WHERE notified = TRUE "
                + "ORDER BY report_month DESC LIMIT 120", rs -> rs.getString("report_month")));
    }

    /** Claims the announcement of a stored report: {@code true} for exactly one caller. */
    CompletableFuture<Boolean> claimAnnouncement(String month) {
        return db.async(c -> Sql.update(c, "UPDATE punish_reports SET notified = TRUE WHERE report_month = ? AND notified = FALSE",
                month) == 1);
    }

    private static String clip(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
