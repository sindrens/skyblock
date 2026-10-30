package net.skybornmc.skyblock.staff;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.Sql;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Database access for staff mode snapshots, frozen players and reports. All methods are async. */
final class StaffStore {

    /** A saved staff-mode snapshot. */
    record Snapshot(UUID uuid, byte[] contents, String gameMode, boolean allowFlight, boolean flying, boolean wasVanished) {
    }

    /** A frozen player (row in {@code staff_frozen}); flight fields are their own state before the freeze. */
    record Frozen(UUID uuid, String name, String frozenBy, UUID frozenByUuid, long frozenAt, boolean allowFlight, boolean flying) {
    }

    private static final String REPORT_COLUMNS = "id, created_at, reporter, reporter_name, target, target_name, reason, details, "
            + "world, x, y, z, status, claimed_by, claimed_by_name, claimed_at, closed_by, closed_by_name, closed_at, outcome";

    private final Database db;

    StaffStore(Database db) {
        this.db = db;
    }

    // ------------------------------------------------------------------ staff mode

    CompletableFuture<Void> saveSnapshot(Snapshot s) {
        return db.run(c -> {
            Sql.update(c, "DELETE FROM staff_mode WHERE uuid = ?", Sql.toBytes(s.uuid()));
            Sql.update(c, "INSERT INTO staff_mode (uuid, contents, game_mode, allow_flight, flying, was_vanished, saved_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?)", Sql.toBytes(s.uuid()), s.contents(), s.gameMode(), s.allowFlight(),
                    s.flying(), s.wasVanished(), System.currentTimeMillis());
        });
    }

    CompletableFuture<Optional<Snapshot>> snapshot(UUID uuid) {
        return db.async(c -> Sql.queryOne(c, "SELECT uuid, contents, game_mode, allow_flight, flying, was_vanished FROM staff_mode WHERE uuid = ?",
                rs -> new Snapshot(Sql.uuid(rs, "uuid"), rs.getBytes("contents"), rs.getString("game_mode"),
                        rs.getBoolean("allow_flight"), rs.getBoolean("flying"), rs.getBoolean("was_vanished")),
                Sql.toBytes(uuid)));
    }

    CompletableFuture<Void> deleteSnapshot(UUID uuid) {
        return db.run(c -> Sql.update(c, "DELETE FROM staff_mode WHERE uuid = ?", Sql.toBytes(uuid)));
    }

    // ------------------------------------------------------------------ freeze

    CompletableFuture<List<Frozen>> frozenPlayers() {
        return db.async(c -> Sql.query(c, "SELECT uuid, name, frozen_by, frozen_by_uuid, frozen_at, allow_flight, flying FROM staff_frozen",
                rs -> new Frozen(Sql.uuid(rs, "uuid"), rs.getString("name"), rs.getString("frozen_by"), Sql.uuid(rs, "frozen_by_uuid"),
                        rs.getLong("frozen_at"), rs.getBoolean("allow_flight"), rs.getBoolean("flying"))));
    }

    /** Inserts or replaces the row (one transaction). */
    CompletableFuture<Void> saveFrozen(Frozen f) {
        return db.transaction(c -> {
            Sql.update(c, "DELETE FROM staff_frozen WHERE uuid = ?", Sql.toBytes(f.uuid()));
            Sql.update(c, "INSERT INTO staff_frozen (uuid, name, frozen_by, frozen_by_uuid, frozen_at, allow_flight, flying) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?)", Sql.toBytes(f.uuid()), f.name(), f.frozenBy(),
                    f.frozenByUuid() == null ? null : Sql.toBytes(f.frozenByUuid()), f.frozenAt(), f.allowFlight(), f.flying());
            return null;
        });
    }

    CompletableFuture<Void> deleteFrozen(UUID uuid) {
        return db.run(c -> Sql.update(c, "DELETE FROM staff_frozen WHERE uuid = ?", Sql.toBytes(uuid)));
    }

    // ------------------------------------------------------------------ reports

    CompletableFuture<Long> createReport(UUID reporter, String reporterName, UUID target, String targetName, String reason,
                                         String details, String world, Integer x, Integer y, Integer z) {
        return db.async(c -> Sql.insertReturningId(c, "INSERT INTO reports (created_at, reporter, reporter_name, target, target_name, "
                        + "reason, details, world, x, y, z, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'open')",
                System.currentTimeMillis(), Sql.toBytes(reporter), reporterName, Sql.toBytes(target), targetName, reason, details,
                world, x, y, z));
    }

    /** Open + claimed reports (oldest first), then the most recent {@code closedLimit} closed ones. */
    CompletableFuture<List<Report>> reports(Report.Status status, int closedLimit) {
        String sql = status == Report.Status.CLOSED
                ? "SELECT " + REPORT_COLUMNS + " FROM reports WHERE status = 'closed' ORDER BY closed_at DESC LIMIT " + Math.max(1, closedLimit)
                : "SELECT " + REPORT_COLUMNS + " FROM reports WHERE status = ? ORDER BY created_at ASC";
        return status == Report.Status.CLOSED
                ? db.async(c -> Sql.query(c, sql, StaffStore::map))
                : db.async(c -> Sql.query(c, sql, StaffStore::map, status.db()));
    }

    CompletableFuture<List<Report>> reportsAbout(UUID target, int limit) {
        return db.async(c -> Sql.query(c, "SELECT " + REPORT_COLUMNS + " FROM reports WHERE target = ? ORDER BY created_at DESC LIMIT "
                + Math.max(1, limit), StaffStore::map, Sql.toBytes(target)));
    }

    CompletableFuture<Optional<Report>> report(long id) {
        return db.async(c -> Sql.queryOne(c, "SELECT " + REPORT_COLUMNS + " FROM reports WHERE id = ?", StaffStore::map, id));
    }

    /** Count of open/claimed reports filed by {@code reporter}, and whether one about {@code target} is still open. */
    CompletableFuture<int[]> reporterState(UUID reporter, UUID target) {
        return db.async(c -> {
            int open = Sql.queryOne(c, "SELECT COUNT(*) FROM reports WHERE reporter = ? AND status <> 'closed'",
                    rs -> rs.getInt(1), Sql.toBytes(reporter)).orElse(0);
            int sameTarget = Sql.queryOne(c, "SELECT COUNT(*) FROM reports WHERE reporter = ? AND target = ? AND status <> 'closed'",
                    rs -> rs.getInt(1), Sql.toBytes(reporter), Sql.toBytes(target)).orElse(0);
            return new int[]{open, sameTarget};
        });
    }

    /** {open, total} reports about {@code target}. */
    CompletableFuture<int[]> targetCounts(UUID target) {
        return db.async(c -> {
            int open = Sql.queryOne(c, "SELECT COUNT(*) FROM reports WHERE target = ? AND status <> 'closed'",
                    rs -> rs.getInt(1), Sql.toBytes(target)).orElse(0);
            int total = Sql.queryOne(c, "SELECT COUNT(*) FROM reports WHERE target = ?", rs -> rs.getInt(1), Sql.toBytes(target)).orElse(0);
            return new int[]{open, total};
        });
    }

    /** Claims (or with {@code staff == null} unclaims) an open/claimed report. Returns whether it changed. */
    CompletableFuture<Boolean> claim(long id, UUID staff, String staffName) {
        return db.async(c -> staff == null
                ? Sql.update(c, "UPDATE reports SET status = 'open', claimed_by = NULL, claimed_by_name = NULL, claimed_at = NULL "
                + "WHERE id = ? AND status = 'claimed'", id) == 1
                : Sql.update(c, "UPDATE reports SET status = 'claimed', claimed_by = ?, claimed_by_name = ?, claimed_at = ? "
                + "WHERE id = ? AND status <> 'closed'", Sql.toBytes(staff), staffName, System.currentTimeMillis(), id) == 1);
    }

    CompletableFuture<Boolean> close(long id, UUID staff, String staffName, Report.Outcome outcome) {
        return db.async(c -> Sql.update(c, "UPDATE reports SET status = 'closed', closed_by = ?, closed_by_name = ?, closed_at = ?, "
                        + "outcome = ? WHERE id = ? AND status <> 'closed'", Sql.toBytes(staff), staffName, System.currentTimeMillis(),
                outcome.name(), id) == 1);
    }

    /** Report link result: reports closed now, open unclaimed ones, ones claimed by other staff. */
    record LinkResult(List<Report> closed, int openUnclaimed, int claimedByOthers) {
    }

    /**
     * Report link (Phase P 12.9): closes the reports about {@code target} claimed by {@code staff} with {@code outcome}
     * in one transaction (a report claimed meanwhile by someone else is not touched), and counts the rest.
     */
    CompletableFuture<LinkResult> closeClaimedBy(UUID target, UUID staff, String staffName, Report.Outcome outcome) {
        return db.transaction(c -> {
            List<Report> about = Sql.query(c, "SELECT " + REPORT_COLUMNS + " FROM reports WHERE target = ? AND status <> 'closed'",
                    StaffStore::map, Sql.toBytes(target));
            List<Report> closed = new java.util.ArrayList<>();
            long now = System.currentTimeMillis();
            for (Report r : ReportRules.toClose(about, staff)) {
                int n = Sql.update(c, "UPDATE reports SET status = 'closed', closed_by = ?, closed_by_name = ?, closed_at = ?, "
                                + "outcome = ? WHERE id = ? AND status = 'claimed' AND claimed_by = ?", Sql.toBytes(staff), staffName,
                        now, outcome.name(), r.id(), Sql.toBytes(staff));
                if (n == 1) {
                    closed.add(Sql.queryOne(c, "SELECT " + REPORT_COLUMNS + " FROM reports WHERE id = ?", StaffStore::map, r.id())
                            .orElse(r));
                }
            }
            return new LinkResult(List.copyOf(closed), ReportRules.openUnclaimed(about), ReportRules.claimedByOthers(about, staff));
        });
    }

    /** Counts only (no change). */
    CompletableFuture<LinkResult> linkCounts(UUID target, UUID staff) {
        return db.async(c -> {
            List<Report> about = Sql.query(c, "SELECT " + REPORT_COLUMNS + " FROM reports WHERE target = ? AND status <> 'closed'",
                    StaffStore::map, Sql.toBytes(target));
            return new LinkResult(List.of(), ReportRules.openUnclaimed(about), ReportRules.claimedByOthers(about, staff));
        });
    }

    /** Closed reports of {@code reporter} they haven't been told about yet; marks them as told. */
    CompletableFuture<List<Report>> takeUnnotified(UUID reporter) {
        return db.transaction(c -> {
            List<Report> list = Sql.query(c, "SELECT " + REPORT_COLUMNS + " FROM reports WHERE reporter = ? AND status = 'closed' "
                    + "AND reporter_notified = FALSE ORDER BY closed_at ASC", StaffStore::map, Sql.toBytes(reporter));
            if (!list.isEmpty()) {
                Sql.update(c, "UPDATE reports SET reporter_notified = TRUE WHERE reporter = ? AND status = 'closed'", Sql.toBytes(reporter));
            }
            return list;
        });
    }

    CompletableFuture<Void> markNotified(long id) {
        return db.run(c -> Sql.update(c, "UPDATE reports SET reporter_notified = TRUE WHERE id = ?", id));
    }

    private static Report map(ResultSet rs) throws SQLException {
        return new Report(rs.getLong("id"), rs.getLong("created_at"), Sql.uuid(rs, "reporter"), rs.getString("reporter_name"),
                Sql.uuid(rs, "target"), rs.getString("target_name"), rs.getString("reason"), rs.getString("details"),
                rs.getString("world"), intOrNull(rs, "x"), intOrNull(rs, "y"), intOrNull(rs, "z"),
                Report.Status.of(rs.getString("status")), Sql.uuid(rs, "claimed_by"), rs.getString("claimed_by_name"),
                Sql.nullableLong(rs, "claimed_at"), Sql.uuid(rs, "closed_by"), rs.getString("closed_by_name"),
                Sql.nullableLong(rs, "closed_at"), rs.getString("outcome"));
    }

    private static Integer intOrNull(ResultSet rs, String column) throws SQLException {
        int v = rs.getInt(column);
        return rs.wasNull() ? null : v;
    }
}
