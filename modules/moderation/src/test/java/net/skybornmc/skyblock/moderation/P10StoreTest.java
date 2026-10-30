package net.skybornmc.skyblock.moderation;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.DatabaseConfig;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.db.Sql;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P10 against a real (H2, MySQL mode) database: V011 runs (twice is fine), every reversal path writes its kind, the report
 * reads one month (reversal kinds, optional reports table, false-report suggestions) and is stored once and announced
 * once; the clean streak keeps daily playtime samples, ignores reversed punishments and changes state for one caller only.
 */
class P10StoreTest {

    private static final UUID STEVE = TestData.STEVE;
    private static final UUID ALEX = TestData.ALEX;
    private static final ZoneId OSLO = ZoneId.of("Europe/Oslo");
    private static final YearMonth OCT = YearMonth.of(2026, 10);
    private static final long HOUR = TestData.HOUR;

    @TempDir
    Path dir;

    private Database db;
    private PunishmentStore store;
    private ReportStore reports;
    private CleanStreakStore streaks;

    @BeforeEach
    void start() throws Exception {
        DatabaseConfig config = new DatabaseConfig();
        config.type = DatabaseConfig.Type.H2;
        config.h2File = "moderation-p10-test";
        config.poolSize = 4;
        config.slowQueryMs = 60_000;
        db = new Database(config, dir.toFile(), Logger.getLogger("moderation-p10-test"));
        db.start();
        new Migrations(db, Logger.getLogger("moderation-p10-test")).migrate("core", getClass().getClassLoader());
        new Migrations(db, Logger.getLogger("moderation-p10-test")).migrate("moderation", getClass().getClassLoader());
        store = new PunishmentStore(db);
        reports = new ReportStore(db);
        streaks = new CleanStreakStore(db);
    }

    @AfterEach
    void stop() {
        db.close();
    }

    private static long oct(int day) {
        return ZonedDateTime.of(2026, 10, day, 12, 0, 0, 0, OSLO).toInstant().toEpochMilli();
    }

    private long insert(Punishment.Type type, UUID target, String template, Integer step, UUID issuer, String issuerName,
                        long created, Long expires) {
        return db.async(c -> Sql.insertReturningId(c, "INSERT INTO punishments (type, target_uuid, target_name, reason, template, "
                        + "ladder_step, issuer_uuid, issuer_name, created, expires) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                type.name(), Sql.toBytes(target), "Steve", "secret reason text", template, step,
                issuer == null ? null : Sql.toBytes(issuer), issuerName, created, expires)).join();
    }

    private String kind(long id) {
        return db.async(c -> Sql.queryOne(c, "SELECT removed_kind FROM punishments WHERE id = ?", rs -> String.valueOf(rs.getString(1)), id)
                .orElse(null)).join();
    }

    @Test
    void migrationIsRerunnable() throws Exception {
        db.async(c -> Sql.update(c, "DELETE FROM schema_version WHERE module_id = 'moderation' AND version_no = 11")).join();
        new Migrations(db, Logger.getLogger("moderation-p10-test")).migrate("moderation", getClass().getClassLoader());
        assertTrue(reports.months().join().isEmpty());
    }

    @Test
    void pardonAndUndoWriteTheirKind() {
        long now = System.currentTimeMillis();
        long mute = insert(Punishment.Type.MUTE, STEVE, "spam", 0, ALEX, "Alex", now - HOUR, null);
        store.pardon(Punishment.Type.MUTE, STEVE, null, ALEX, "Alex", "mistake", false).join();
        assertEquals("PARDON", kind(mute));
        long warn = insert(Punishment.Type.WARN, STEVE, "spam", 0, ALEX, "Alex", now - 1000, null);
        PunishmentStore.UndoResult undo = store.undo(warn, ALEX, "Alex", true, java.time.Duration.ofMinutes(5), "Undone by Alex", now).join();
        assertEquals(Undo.Result.OK, undo.result());
        assertEquals("UNDO", kind(warn));
    }

    @Test
    void reportReadsOneMonthWithReversalKinds() {
        long a = insert(Punishment.Type.WARN, STEVE, "spam", 0, ALEX, "Alex", oct(3), null);
        long b = insert(Punishment.Type.MUTE, STEVE, "spam", 1, ALEX, "Alex", oct(4), oct(4) + HOUR);
        long c = insert(Punishment.Type.BAN, STEVE, "hacking", 0, ALEX, "Alex", oct(5), null);
        insert(Punishment.Type.KICK, STEVE, null, null, null, "Console", oct(6), null);
        insert(Punishment.Type.WARN, STEVE, "spam", 0, ALEX, "Alex", oct(1) - 40L * 24 * HOUR, null); // August: not read
        db.run(conn -> {
            Sql.update(conn, "UPDATE punishments SET removed_at = ?, removed_kind = 'UNDO' WHERE id = ?", oct(3) + 1, a);
            Sql.update(conn, "UPDATE punishments SET removed_at = ?, rollback_id = 9 WHERE id = ?", oct(4) + 1, b); // old row, no kind
            Sql.update(conn, "INSERT INTO punishment_suggestions (target_uuid, target_name, kind, action, reason, source, created, "
                    + "status, reason_id) VALUES (?, 'Steve', 'api', 'warn', 'x', 'auto:api:report-abuse', ?, 'PENDING', 'report-abuse')",
                    Sql.toBytes(STEVE), oct(7));
            Sql.update(conn, "INSERT INTO punish_dry_runs (staff_uuid, staff_name, trainee, target_name, verdict, created) "
                    + "VALUES (?, 'Alex', TRUE, 'Steve', 'APPLY', ?)", Sql.toBytes(ALEX), oct(8));
            Sql.update(conn, "INSERT INTO appeals (punishment_id, punishment_type, player_uuid, player_name, appeal_text, status, "
                    + "handler_uuid, handler_name, created, handled) VALUES (?, 'BAN', ?, 'Steve', 'please unban me now', 'ACCEPTED', ?, "
                    + "'Admin', ?, ?)", c, Sql.toBytes(STEVE), Sql.toBytes(ALEX), oct(5) + HOUR, oct(5) + 3 * HOUR);
        }).join();
        long now = oct(31) + 48 * HOUR;
        MonthlyReport.Input in = reports.input(OCT, OSLO, MonthlyReport.Settings.DEFAULT, now, "report-abuse").join();
        assertNull(in.reports(), "no reports table without the staff module");
        assertEquals(1, in.falseReportSuggestions());
        assertEquals(1, in.dryRuns().size());
        assertEquals(1, in.appeals().size());
        MonthlyReport.Report r = MonthlyReport.compute(in, MonthlyReport.Settings.DEFAULT);
        assertEquals(new MonthlyReport.Totals(1, 1, 1, 1, 0, 0, 0), r.totals());
        assertEquals(1, r.reversed().undo());
        assertEquals(1, r.reversed().rollback(), "rows without a kind but with a rollback id");
        assertEquals(1, r.appeals().accepted());
        assertEquals(1, r.traineeDryRuns());
        assertEquals(List.of("Alex"), r.staff().stream().map(MonthlyReport.StaffLine::name).toList(), "console never counts");
        assertFalse(MonthlyReport.toJson(r).contains("secret reason text"), "reason texts are never read");

        // with the reports module's table, reports are counted
        db.run(conn -> {
            Sql.update(conn, "CREATE TABLE reports (id BIGINT AUTO_INCREMENT PRIMARY KEY, created_at BIGINT NOT NULL, "
                    + "closed_at BIGINT, outcome VARCHAR(32))");
            Sql.update(conn, "INSERT INTO reports (created_at, closed_at, outcome) VALUES (?, ?, 'INVALID')", oct(9), oct(9) + HOUR);
        }).join();
        MonthlyReport.Input with = reports.input(OCT, OSLO, MonthlyReport.Settings.DEFAULT, now, "report-abuse").join();
        assertEquals(new MonthlyReport.Reports(1, 1, 1, HOUR), MonthlyReport.compute(with, MonthlyReport.Settings.DEFAULT).reports());
    }

    @Test
    void reportIsStoredOnceAndAnnouncedOnce() {
        MonthlyReport.Report r = MonthlyReport.compute(new MonthlyReport.Input(OCT, OSLO, oct(31) + 48 * HOUR, List.of(), List.of(),
                List.of(), List.of(), null, List.of(), List.of(), 0), MonthlyReport.Settings.DEFAULT);
        assertTrue(reports.save(r, "System").join());
        assertFalse(reports.save(r, "Alex").join(), "the first one stays");
        Optional<ReportStore.Stored> stored = reports.load("2026-10").join();
        assertTrue(stored.isPresent());
        assertEquals("System", stored.get().generatedBy());
        assertEquals(Optional.of(r), MonthlyReport.fromJson(stored.get().json()));
        assertEquals(List.of("2026-10"), reports.months().join());
        assertTrue(reports.announced().join().isEmpty());
        assertTrue(reports.claimAnnouncement("2026-10").join());
        assertFalse(reports.claimAnnouncement("2026-10").join(), "announced exactly once");
        assertEquals(List.of("2026-10"), reports.announced().join());
    }

    @Test
    void cleanStreakSamplesAndState() {
        long now = System.currentTimeMillis();
        int today = CleanStreak.day(now, OSLO);
        int start = today - 60;
        // first check: nothing played in the period yet (counting starts now)
        CleanStreakStore.Snapshot first = streaks.snapshot(STEVE, today - 70, start, 100 * HOUR).join();
        assertEquals(0, first.played());
        assertEquals(CleanStreak.State.NONE, first.state());
        assertNull(first.lastBreaking());
        streaks.snapshot(STEVE, start + 1, start, 103 * HOUR).join();
        CleanStreakStore.Snapshot later = streaks.snapshot(STEVE, today, start, 115 * HOUR).join();
        assertEquals(15 * HOUR, later.played(), "since the newest sample at or before the period start");
        // the same day again overwrites today's sample; samples older than the baseline are gone
        streaks.snapshot(STEVE, today, start, 116 * HOUR).join();
        assertEquals(List.of(today - 70, start + 1, today), db.async(c -> Sql.query(c, "SELECT play_day FROM punish_playtime_days "
                + "WHERE player_uuid = ? ORDER BY play_day", rs -> rs.getInt(1), Sql.toBytes(STEVE))).join());

        // reversed punishments don't count; the newest one on record does
        long kept = insert(Punishment.Type.KICK, STEVE, null, null, ALEX, "Alex", now - 90L * 24 * HOUR, null);
        long reversed = insert(Punishment.Type.WARN, STEVE, "spam", 0, ALEX, "Alex", now - 5L * 24 * HOUR, null);
        insert(Punishment.Type.ISLAND_BAN, STEVE, "griefing", 0, ALEX, "Alex", now - HOUR, null); // never breaks
        db.run(c -> Sql.update(c, "UPDATE punishments SET removed_at = ?, removed_kind = 'UNDO' WHERE id = ?", now, reversed)).join();
        Long last = streaks.snapshot(STEVE, today, start, 116 * HOUR).join().lastBreaking();
        assertNotNull(last);
        assertEquals(now - 90L * 24 * HOUR, last.longValue(), "the kick #" + kept);

        assertTrue(streaks.markEarned(STEVE, now).join());
        assertFalse(streaks.markEarned(STEVE, now).join(), "only one check announces it");
        CleanStreak.State earned = streaks.state(STEVE).join();
        assertTrue(earned.earned());
        assertEquals(1, earned.timesEarned());
        assertTrue(streaks.markLost(STEVE, now + 1).join());
        assertFalse(streaks.markLost(STEVE, now + 2).join());
        assertTrue(streaks.markEarned(STEVE, now + 3).join());
        assertEquals(2, streaks.state(STEVE).join().timesEarned(), "history kept");
        assertFalse(streaks.markLost(ALEX, now).join(), "never earned");
    }
}
