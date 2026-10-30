package net.skybornmc.skyblock.staff;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.DatabaseConfig;
import net.skybornmc.skyblock.common.db.Migrations;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase P 14.5 on the reports side: only invalid counts, the count inside the window, the ordinal in the staff message,
 * and report blocks (longest wins, permanent, lifted by reference, ended ones ignored).
 */
class FalseReportsTest {

    private static final UUID STEVE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID REPORTER = UUID.fromString("00000000-0000-0000-0000-00000000000d");

    @TempDir
    Path dir;

    private Database db;
    private StaffStore store;

    @BeforeEach
    void start() throws Exception {
        DatabaseConfig config = new DatabaseConfig();
        config.type = DatabaseConfig.Type.H2;
        config.h2File = "staff-false-reports-test";
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

    @Test
    void onlyInvalidIsAFalseReport() {
        for (Report.Outcome o : Report.Outcome.values()) {
            assertEquals(o == Report.Outcome.INVALID, ReportRules.falseReport(o), o.name());
        }
    }

    @Test
    void ordinals() {
        assertEquals(List.of("st", "nd", "rd", "th", "th", "th", "th", "th", "th", "th", "th", "th", "th", "th"),
                Arrays.stream(new int[]{1, 2, 3, 4, 5, 9, 10, 11, 12, 13, 14, 20, 100, 111})
                        .mapToObj(ReportRules::ordinalSuffix).toList());
        assertEquals("st", ReportRules.ordinalSuffix(21));
        assertEquals("nd", ReportRules.ordinalSuffix(22));
        assertEquals("rd", ReportRules.ordinalSuffix(103));
    }

    @Test
    void countsInvalidReportsInsideTheWindow() {
        long now = System.currentTimeMillis();
        long a = store.createReport(REPORTER, "Rep", STEVE, "Steve", "Hacking", "", null, null, null, null).join();
        long b = store.createReport(REPORTER, "Rep", ALEX, "Alex", "Hacking", "", null, null, null, null).join();
        long c = store.createReport(REPORTER, "Rep", STEVE, "Steve", "Spam", "", null, null, null, null).join();
        long d = store.createReport(REPORTER, "Rep", ALEX, "Alex", "Spam", "", null, null, null, null).join();
        assertTrue(store.close(a, ALEX, "Alex", Report.Outcome.INVALID).join());
        assertTrue(store.close(b, ALEX, "Alex", Report.Outcome.NO_EVIDENCE).join());
        assertTrue(store.close(c, ALEX, "Alex", Report.Outcome.DUPLICATE).join());
        assertTrue(store.close(d, ALEX, "Alex", Report.Outcome.INVALID).join());
        assertEquals(2, store.closedCount(REPORTER, Report.Outcome.INVALID, now - 60_000).join());
        assertEquals(0, store.closedCount(REPORTER, Report.Outcome.INVALID, now + 3_600_000).join(), "outside the window");
        assertEquals(0, store.closedCount(ALEX, Report.Outcome.INVALID, 0).join(), "per reporter");
    }

    @Test
    void reportBlocks() {
        long now = System.currentTimeMillis();
        assertEquals(Optional.empty(), ReportRules.blockedUntil(List.of()));
        assertEquals(Optional.of(5L), ReportRules.blockedUntil(List.of(3L, 5L)));
        assertEquals(Optional.of(Long.MAX_VALUE), ReportRules.blockedUntil(Arrays.asList(3L, null)), "a permanent block wins");

        store.addReportBlock(STEVE, now + 3_600_000, "punishment:1", now).join();
        store.addReportBlock(STEVE, now + 7_200_000, "punishment:2", now).join();
        store.addReportBlock(STEVE, now - 1, "punishment:0", now - 10_000).join(); // already ended
        store.addReportBlock(ALEX, null, "punishment:3", now).join();
        assertEquals(Optional.of(now + 7_200_000), ReportRules.blockedUntil(store.activeReportBlocks(STEVE, now).join()));
        assertEquals(Optional.of(Long.MAX_VALUE), ReportRules.blockedUntil(store.activeReportBlocks(ALEX, now).join()));
        assertTrue(store.liftReportBlock("punishment:2", now).join());
        assertFalse(store.liftReportBlock("punishment:2", now).join(), "lifted once");
        assertFalse(store.liftReportBlock("punishment:0", now).join(), "an ended block is not lifted again");
        assertEquals(Optional.of(now + 3_600_000), ReportRules.blockedUntil(store.activeReportBlocks(STEVE, now).join()),
                "the other block of the same player stays");
        assertTrue(store.liftReportBlock("punishment:1", now).join());
        assertEquals(Optional.empty(), ReportRules.blockedUntil(store.activeReportBlocks(STEVE, now).join()));
    }
}
