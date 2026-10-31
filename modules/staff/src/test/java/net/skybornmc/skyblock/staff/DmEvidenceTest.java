package net.skybornmc.skyblock.staff;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.audit.AuditConfig;
import net.skybornmc.skyblock.common.audit.AuditEntry;
import net.skybornmc.skyblock.common.audit.AuditLog;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.DatabaseConfig;
import net.skybornmc.skyblock.common.db.Migrations;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** STAFF-R3 (R3-9a): the last DMs between reporter and reported player are found in the audit log, stored and purged. */
class DmEvidenceTest {

    private static final UUID REPORTER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-0000000000c3");

    @TempDir
    Path dir;

    private Database db;
    private AuditLog audit;
    private StaffStore store;

    @BeforeEach
    void start() throws Exception {
        DatabaseConfig config = new DatabaseConfig();
        config.type = DatabaseConfig.Type.H2;
        config.h2File = "staff-dm-evidence-test";
        config.poolSize = 4;
        config.slowQueryMs = 60_000;
        Logger log = Logger.getLogger("staff-test");
        db = new Database(config, dir.toFile(), log);
        db.start();
        new Migrations(db, log).migrate("core", getClass().getClassLoader());
        new Migrations(db, log).migrate("staff", getClass().getClassLoader());
        audit = new AuditLog(db, new AuditConfig(), dir.toFile(), log);
        store = new StaffStore(db);
    }

    @AfterEach
    void stop() {
        db.close();
    }

    private void dm(UUID from, String fromName, UUID to, String toName, String text, boolean ignored) {
        Map<String, Object> details = ignored ? Map.of("message", text, "blocked", "ignored") : Map.of("message", text);
        audit.log(AuditCategory.DM, "dm", Actor.player(from, fromName), to.toString(), toName, null, null, details);
    }

    @Test
    void queryFindsTheLastTenDmsBetweenTheTwoInBothDirections() {
        for (int i = 1; i <= 12; i++) {
            if (i % 2 == 0) {
                dm(REPORTER, "Rep", TARGET, "Bad", "r" + i, false);
            } else {
                dm(TARGET, "Bad", REPORTER, "Rep", "t" + i, i == 11);
            }
        }
        dm(REPORTER, "Rep", OTHER, "Other", "not about them", false);
        dm(OTHER, "Other", TARGET, "Bad", "not about them either", false);
        audit.log(AuditCategory.CHAT, "chat", Actor.player(REPORTER, "Rep"), TARGET.toString(), "Bad", null, null,
                Map.of("message", "public chat is not a DM"));
        audit.log(AuditCategory.DM, "dm_blocked", Actor.player(TARGET, "Bad"), REPORTER.toString(), "Rep", null, null,
                Map.of("message", "www.scam.com", "reason", "link"));
        audit.flush();

        List<AuditEntry> rows = audit.query(DmEvidence.query(REPORTER, TARGET, 10)).join();
        List<DmEvidence.Line> lines = DmEvidence.lines(rows, REPORTER, TARGET, 10);

        assertEquals(10, lines.size());
        assertEquals("t3", lines.getFirst().body(), "oldest of the last ten first");
        assertEquals("r12", lines.getLast().body(), "the newest last, like a chat");
        assertTrue(lines.stream().noneMatch(l -> l.body().contains("not about") || l.body().contains("public")
                || l.body().contains("scam")), "only DMs between the two");
        DmEvidence.Line ignored = lines.stream().filter(l -> l.body().equals("t11")).findFirst().orElseThrow();
        assertTrue(ignored.ignored(), "a DM the reporter ignored is marked");
        assertEquals("Bad", ignored.senderName());
        assertEquals("Rep", ignored.recipientName());
        assertEquals(TARGET, ignored.sender());
    }

    @Test
    void noDmsMeansNoEvidence() {
        dm(REPORTER, "Rep", OTHER, "Other", "hi", false);
        audit.flush();
        List<DmEvidence.Line> lines = DmEvidence.lines(audit.query(DmEvidence.query(REPORTER, TARGET, 10)).join(), REPORTER, TARGET, 10);
        assertTrue(lines.isEmpty());
        assertEquals(0, store.saveEvidence(1, lines, 0).join());
        assertTrue(DmEvidence.lines(List.of(), REPORTER, TARGET, 0).isEmpty(), "count 0 = off");
        assertTrue(DmEvidence.lines(List.of(), REPORTER, REPORTER, 10).isEmpty());
    }

    @Test
    void evidenceIsStoredPerReportAndPurgedAfterTheKeepTime() {
        long now = 1_800_000_000_000L;
        long old = now - 181L * 86_400_000L;
        List<DmEvidence.Line> lines = List.of(
                new DmEvidence.Line(1000, TARGET, "Bad", "Rep", "first", false),
                new DmEvidence.Line(2000, REPORTER, "Rep", "Bad", "x".repeat(2000), true));
        assertEquals(2, store.saveEvidence(7, lines, old).join());
        assertEquals(1, store.saveEvidence(8, List.of(new DmEvidence.Line(3000, TARGET, "Bad", "Rep", "new", false)), now).join());

        List<DmEvidence.Line> seven = store.evidence(7).join();
        assertEquals(2, seven.size());
        assertEquals("first", seven.getFirst().body());
        assertEquals(DmEvidence.MAX_BODY, seven.getLast().body().length(), "cut to the column size");
        assertTrue(seven.getLast().ignored());
        assertEquals(TARGET, seven.getFirst().sender());
        assertEquals(2, store.evidenceCount(7).join());

        long cutoff = DmEvidence.cutoff(now, 180);
        assertEquals(now - 180L * 86_400_000L, cutoff);
        assertEquals(2, store.purgeEvidence(cutoff).join(), "181 days old: gone");
        assertEquals(0, store.evidenceCount(7).join());
        assertEquals(1, store.evidenceCount(8).join(), "a fresh report keeps its DMs");
        assertEquals(0, store.purgeEvidence(cutoff).join());
    }

    @Test
    void cutoffNeverKeepsLessThanOneDay() {
        assertEquals(1000L * 86_400_000L - 86_400_000L, DmEvidence.cutoff(1000L * 86_400_000L, 0));
    }

    @Test
    void linesSkipRowsWithoutAPlayerSender() {
        AuditEntry console = new AuditEntry(1, 5, 0, "s", AuditCategory.DM, "dm", Actor.CONSOLE, TARGET.toString(), "Bad", null,
                null, null, null, null, Map.of("message", "from console"));
        AuditEntry bad = new AuditEntry(2, 6, 0, "s", AuditCategory.DM, "dm", Actor.player(REPORTER, "Rep"), "CONSOLE", "CONSOLE",
                null, null, null, null, null, Map.of("message", "to console"));
        assertFalse(DmEvidence.lines(List.of(console, bad), REPORTER, TARGET, 10).iterator().hasNext());
    }
}
