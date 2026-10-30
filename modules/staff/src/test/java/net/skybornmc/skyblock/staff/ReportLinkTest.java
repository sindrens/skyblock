package net.skybornmc.skyblock.staff;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.DatabaseConfig;
import net.skybornmc.skyblock.common.db.Migrations;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Phase P spec 12.14 item 10: a punishment closes only the punishing staff member's claimed reports; others stay. */
class ReportLinkTest {

    private static final UUID STEVE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-00000000000c");
    private static final UUID REPORTER = UUID.fromString("00000000-0000-0000-0000-00000000000d");

    @TempDir
    Path dir;

    private Database db;
    private StaffStore store;

    @BeforeEach
    void start() throws Exception {
        DatabaseConfig config = new DatabaseConfig();
        config.type = DatabaseConfig.Type.H2;
        config.h2File = "staff-report-link-test";
        config.poolSize = 4;
        config.slowQueryMs = 60_000;
        db = new Database(config, dir.toFile(), Logger.getLogger("staff-test"));
        db.start();
        new Migrations(db, Logger.getLogger("staff-test")).migrate("core", getClass().getClassLoader());
        new Migrations(db, Logger.getLogger("staff-test")).migrate("staff", getClass().getClassLoader());
        store = new StaffStore(db);
    }

    @AfterEach
    void stop() {
        db.close();
    }

    private long report(UUID target) {
        return store.createReport(REPORTER, "Rep", target, "Steve", "Hacking", "", null, null, null, null).join();
    }

    private static Report row(long id, Report.Status status, UUID claimedBy) {
        return new Report(id, 0, REPORTER, "Rep", STEVE, "Steve", "Hacking", "", null, null, null, null, status, claimedBy,
                claimedBy == null ? null : "x", null, null, null, null, null);
    }

    @Test
    void pureRulePicksOnlyReportsClaimedByThatStaffMember() {
        List<Report> about = List.of(row(1, Report.Status.OPEN, null), row(2, Report.Status.CLAIMED, ALEX),
                row(3, Report.Status.CLAIMED, BOB), row(4, Report.Status.CLAIMED, ALEX));
        assertEquals(List.of(2L, 4L), ReportRules.toClose(about, ALEX).stream().map(Report::id).toList());
        assertEquals(1, ReportRules.openUnclaimed(about));
        assertEquals(1, ReportRules.claimedByOthers(about, ALEX));
        assertTrue(ReportRules.toClose(about, null).isEmpty(), "console/system closes nothing");
    }

    @Test
    void closesOnlyTheClaimingStaffsReportsAsPunished() {
        long mine = report(STEVE);
        long theirs = report(STEVE);
        long open = report(STEVE);
        long otherPlayer = report(BOB);
        assertTrue(store.claim(mine, ALEX, "Alex").join());
        assertTrue(store.claim(theirs, BOB, "Bob").join());
        assertTrue(store.claim(otherPlayer, ALEX, "Alex").join());

        StaffStore.LinkResult r = store.closeClaimedBy(STEVE, ALEX, "Alex", Report.Outcome.PUNISHED).join();
        assertEquals(List.of(mine), r.closed().stream().map(Report::id).toList());
        assertEquals(1, r.openUnclaimed(), "the unclaimed one is only counted");
        assertEquals(1, r.claimedByOthers());
        Report closed = store.report(mine).join().orElseThrow();
        assertEquals(Report.Status.CLOSED, closed.status());
        assertEquals("PUNISHED", closed.outcome());
        assertEquals(ALEX, closed.closedBy());
        assertEquals(Report.Status.CLAIMED, store.report(theirs).join().orElseThrow().status(), "Bob's claim stays");
        assertEquals(Report.Status.OPEN, store.report(open).join().orElseThrow().status(), "unclaimed stays open");
        assertEquals(Report.Status.CLAIMED, store.report(otherPlayer).join().orElseThrow().status(), "other players untouched");

        StaffStore.LinkResult again = store.closeClaimedBy(STEVE, ALEX, "Alex", Report.Outcome.PUNISHED).join();
        assertTrue(again.closed().isEmpty(), "nothing left to close");
        StaffStore.LinkResult counts = store.linkCounts(STEVE, BOB).join();
        assertEquals(1, counts.openUnclaimed());
        assertEquals(0, counts.claimedByOthers(), "Bob's own claim is not 'others' for Bob");
    }
}
