package net.skybornmc.skyblock.common.audit;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.H2;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.db.Sql;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditLogTest {

    private static final Logger LOG = Logger.getLogger("audit-test");

    @TempDir
    Path dir;
    private Database db;
    private AuditConfig config;
    private AuditLog audit;

    @BeforeEach
    void setUp() throws Exception {
        db = H2.start(dir);
        new Migrations(db, LOG).migrate("core", getClass().getClassLoader());
        config = new AuditConfig();
        config.archiveFolder = dir.resolve("archive").toString();
        audit = new AuditLog(db, config, dir.toFile(), LOG);
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    @Test
    void writesAndQueriesByActorCategoryAndTarget() throws Exception {
        UUID steve = UUID.randomUUID();
        UUID alex = UUID.randomUUID();
        audit.logPlayer(AuditCategory.ECONOMY, "pay", Actor.player(steve, "Steve"), alex, "Alex", 1_500L, null, Map.of("currency", "money"));
        audit.log(AuditCategory.COMMAND, "command", Actor.player(steve, "Steve"), Map.of("line", "/is home"));
        audit.log(AuditCategory.PUNISHMENT, "unban", Actor.CONSOLE, "Griefer", null, null, null, null);
        assertEquals(3, audit.flush());

        List<AuditEntry> steveRows = audit.query(AuditQuery.create().actor(steve)).get();
        assertEquals(2, steveRows.size());
        AuditEntry pay = audit.query(AuditQuery.create().categories(AuditCategory.ECONOMY)).get().getFirst();
        assertEquals("pay", pay.action());
        assertEquals(alex.toString(), pay.target());
        assertEquals(1_500L, pay.amount());
        assertEquals("money", pay.details().get("currency"));
        assertEquals(1, audit.query(AuditQuery.create().involving(alex)).get().size());
        assertEquals(1, audit.query(AuditQuery.create().actorName("CONSOLE")).get().size());
        assertEquals(3L, audit.count(AuditQuery.create()).get());
    }

    @Test
    void listenersSeeEveryNewRowUntilRemoved() {
        List<AuditEntry> seen = new java.util.ArrayList<>();
        Runnable stop = audit.listen(seen::add);
        audit.listen(e -> {
            throw new IllegalStateException("a broken listener must not stop logging");
        });
        UUID steve = UUID.randomUUID();
        audit.logPlayer(AuditCategory.TRADE, "trade_complete", Actor.player(steve, "Steve"), UUID.randomUUID(), "Alex", 5L, null, Map.of());
        assertEquals(1, seen.size());
        assertEquals("trade_complete", seen.getFirst().action());
        assertEquals(steve, seen.getFirst().actor().uuid());
        stop.run();
        audit.log(AuditCategory.STAFF, "other", Actor.SYSTEM, Map.of());
        assertEquals(1, seen.size());
        assertEquals(2, audit.flush());
    }

    @Test
    void betweenFindsBothDirectionsOnly() throws Exception {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        audit.logPlayer(AuditCategory.ECONOMY, "pay", Actor.player(a, "A"), b, "B", 10L, null, null);
        audit.logPlayer(AuditCategory.ECONOMY, "pay", Actor.player(b, "B"), a, "A", 20L, null, null);
        audit.logPlayer(AuditCategory.ECONOMY, "pay", Actor.player(a, "A"), c, "C", 30L, null, null);
        audit.flush();
        assertEquals(2, audit.query(AuditQuery.create().between(a, b)).get().size());
        assertEquals(2, audit.query(AuditQuery.create().between(a, b).copyPage(0, 1).page(0, 5)).get().size());
    }

    @Test
    void pagingAndNewestFirst() throws Exception {
        for (int i = 0; i < 12; i++) {
            audit.log(AuditCategory.STAFF, "step_" + i, Actor.SYSTEM, Map.of());
            Thread.sleep(2);
        }
        audit.flush();
        List<AuditEntry> page0 = audit.query(AuditQuery.create().page(0, 5)).get();
        List<AuditEntry> page2 = audit.query(AuditQuery.create().page(2, 5)).get();
        assertEquals("step_11", page0.getFirst().action());
        assertEquals(2, page2.size());
    }

    @Test
    void retentionArchivesBeforeRemovingShortCategoriesOnly() throws Exception {
        long old = System.currentTimeMillis() - Duration.ofDays(40).toMillis();
        db.blocking(c -> {
            AuditLog.insert(c, List.of(
                    entry(old, AuditCategory.CHAT, "chat"),
                    entry(old, AuditCategory.ECONOMY, "pay"),
                    entry(System.currentTimeMillis(), AuditCategory.CHAT, "chat")));
            return null;
        });
        AuditRetention.Result result = new AuditRetention(db, config, dir.toFile(), LOG).run(System.currentTimeMillis());
        assertEquals(1, result.removed());
        assertEquals(2L, audit.count(AuditQuery.create()).get());
        File[] archives = new File(config.archiveFolder).listFiles((d, n) -> n.endsWith(".jsonl.gz"));
        assertNotNull(archives);
        assertEquals(1, archives.length);
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new GZIPInputStream(Files.newInputStream(archives[0].toPath())),
                StandardCharsets.UTF_8))) {
            assertTrue(r.readLine().contains("\"category\":\"chat\""));
        }
    }

    @Test
    void closeWithoutDatabaseSpoolsAndStartReplays() throws Exception {
        audit.log(AuditCategory.ISLAND, "island_create", Actor.SYSTEM, Map.of("size", 3));
        db.close();
        audit.close();
        assertTrue(new File(dir.toFile(), "audit-spool.jsonl").isFile());

        Database again = H2.start(dir);
        try {
            AuditLog fresh = new AuditLog(again, config, dir.toFile(), LOG);
            fresh.start();
            fresh.close();
            long rows = again.<Long>blocking(c -> Sql.queryOne(c, "SELECT COUNT(*) FROM audit_log", rs -> rs.getLong(1)).orElse(0L));
            assertEquals(1L, rows);
        } finally {
            again.close();
        }
    }

    @Test
    void jsonLineRoundTrip() {
        AuditEntry e = new AuditEntry(0, 123L, 2, "skyblock-1", AuditCategory.TRADE, "trade", Actor.player(UUID.randomUUID(), "A"),
                "t", "T", 5L, "islands", 1, 2, 3, Map.of("k", "v"));
        AuditEntry back = AuditJson.parse(AuditJson.line(e));
        assertNotNull(back);
        assertEquals(e.actor(), back.actor());
        assertEquals("v", back.details().get("k"));
        assertEquals(3, back.z());
    }

    @Test
    void partitionDdlOnlyAfterHighestBound() {
        List<AuditPartitions.Partition> existing = List.of(new AuditPartitions.Partition("p_future", null));
        List<String> ddl = AuditPartitions.missing(existing, YearMonth.of(2026, 10), 2);
        assertEquals(3, ddl.size());
        assertTrue(ddl.getFirst().contains("PARTITION p202610 VALUES LESS THAN (1793491200000)"));
        long novBound = YearMonth.of(2026, 12).atDay(1).atStartOfDay().toInstant(java.time.ZoneOffset.UTC).toEpochMilli();
        List<String> next = AuditPartitions.missing(List.of(new AuditPartitions.Partition("p202611", novBound),
                new AuditPartitions.Partition("p_future", null)), YearMonth.of(2026, 10), 2);
        assertEquals(1, next.size());
        assertTrue(next.getFirst().contains("p202612"));
    }

    private static AuditEntry entry(long time, AuditCategory category, String action) {
        return new AuditEntry(0, time, 0, "skyblock-1", category, action, Actor.SYSTEM, null, null, null, null, null, null, null, Map.of());
    }
}
