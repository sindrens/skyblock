package net.skybornmc.skyblock.box;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.DatabaseConfig;
import net.skybornmc.skyblock.common.db.Migrations;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The box tables on H2 in MySQL mode (same SQL as MariaDB): atomic deliveries, exactly-once takes, give-back, spool. */
class BoxEngineTest {

    private static final Logger LOG = Logger.getLogger("box-test");
    @TempDir
    Path dir;
    private Database db;
    private BoxStore store;
    private BoxSpool spool;
    private BoxEngine engine;

    @BeforeEach
    void start() throws Exception {
        db = open();
        store = new BoxStore();
        spool = new BoxSpool(dir.resolve("spool").toFile());
        engine = new BoxEngine(db, store, spool, LOG, () -> 0, () -> 0);
    }

    private Database open() throws Exception {
        DatabaseConfig config = new DatabaseConfig();
        config.type = DatabaseConfig.Type.H2;
        config.h2File = "box-test";
        config.poolSize = 4;
        Database d = new Database(config, dir.toFile(), LOG);
        d.start();
        new Migrations(d, LOG).migrate("box", getClass().getClassLoader());
        return d;
    }

    @AfterEach
    void stop() {
        db.close();
    }

    private static BoxItem item(String material, int amount, String source, long at) {
        return new BoxItem(("data:" + material + ":" + amount).getBytes(StandardCharsets.UTF_8), material, amount, material.toLowerCase(),
                source, at, null);
    }

    private int count(UUID profile) {
        return db.blocking(c -> store.count(c, profile));
    }

    private List<BoxEntry> list(UUID profile) {
        return db.blocking(c -> store.list(c, profile, 1000));
    }

    @Test
    void aDeliveryStoresEveryStackWithItsCount() {
        UUID p = UUID.randomUUID();
        BoxEngine.Stored s = engine.deliver(p, List.of(item("DIAMOND", 64, "sell", 1), item("STONE", 32, "sell", 1))).join();
        assertEquals(2, s.ids().size());
        assertEquals(2, s.count());
        assertFalse(s.spooled());
        List<BoxEntry> rows = list(p);
        assertEquals("DIAMOND", rows.getFirst().material(), "oldest first");
        assertEquals("sell", rows.getFirst().source());
        assertArrayEquals("data:DIAMOND:64".getBytes(StandardCharsets.UTF_8), rows.getFirst().bytes());
        assertEquals(0, count(UUID.randomUUID()), "per profile");
    }

    @Test
    void aFailedDeliveryStoresNothing() {
        UUID p = UUID.randomUUID();
        assertThrows(RuntimeException.class, () -> db.blockingTransaction(c -> {
            store.insert(c, p, List.of(item("DIAMOND", 1, "shop", 1), item("GOLD_INGOT", 1, "shop", 1)));
            throw new SQLException("crash in the middle");
        }));
        assertEquals(0, count(p), "one transaction: all or nothing");
    }

    @Test
    void aStackIsTakenExactlyOnce() {
        UUID p = UUID.randomUUID();
        List<Long> ids = engine.deliver(p, List.of(item("A", 1, "trade", 1), item("B", 1, "trade", 2), item("C", 1, "trade", 3))).join().ids();
        BoxEngine.Taken first = engine.take(p, List.of(ids.get(0), ids.get(1))).join();
        assertEquals(2, first.entries().size());
        assertEquals(1, first.count());
        BoxEngine.Taken again = engine.take(p, List.of(ids.get(0))).join();
        assertTrue(again.entries().isEmpty(), "a second click gets nothing");
        assertTrue(engine.take(UUID.randomUUID(), List.of(ids.get(2))).join().entries().isEmpty(), "another profile can't take it");
        assertEquals(1, count(p));
    }

    @Test
    void parallelTakesOfTheSameStackGiveItOnce() {
        UUID p = UUID.randomUUID();
        long id = engine.deliver(p, List.of(item("NETHERITE_INGOT", 1, "auction", 1))).join().ids().getFirst();
        List<CompletableFuture<BoxEngine.Taken>> runs = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            runs.add(engine.take(p, List.of(id)));
        }
        int taken = 0;
        for (CompletableFuture<BoxEngine.Taken> r : runs) {
            try {
                taken += r.join().entries().size();
            } catch (RuntimeException lockConflict) {
                // a losing transaction may fail instead of finding nothing: it gave nothing either
            }
        }
        assertEquals(1, taken);
        assertEquals(0, count(p));
    }

    @Test
    void giveBackKeepsSourceAndArrivalTime() {
        UUID p = UUID.randomUUID();
        List<Long> ids = engine.deliver(p, List.of(item("EMERALD", 16, "forge", 1234))).join().ids();
        BoxEngine.Taken taken = engine.take(p, ids).join();
        assertEquals(0, taken.count());
        // The player left before the item could be given: it goes back.
        BoxEngine.Stored back = engine.giveBack(p, BoxEngine.items(taken.entries())).join();
        assertEquals(1, back.count());
        BoxEntry row = list(p).getFirst();
        assertEquals("forge", row.source());
        assertEquals(1234, row.createdAt());
        assertEquals(16, row.amount());
    }

    @Test
    void aStoppedDatabaseSpoolsAndTheImportStoresItOnce() throws Exception {
        UUID p = UUID.randomUUID();
        db.close();
        BoxEngine.Stored s = engine.deliver(p, List.of(item("DIAMOND_BLOCK", 9, "shop", 5), item("IRON_INGOT", 3, "shop", 5))).join();
        assertTrue(s.spooled(), "never lost: written to disk");
        File spoolDir = dir.resolve("spool").toFile();
        File current = new File(spoolDir, "spool.txt");
        assertTrue(current.length() > 0);
        byte[] copy = Files.readAllBytes(current.toPath());

        db = open();
        engine = new BoxEngine(db, store, spool, LOG, () -> 0, () -> 0);
        assertEquals(2, engine.importSpool().join());
        assertEquals(2, count(p));
        assertFalse(spool.hasWork());
        // A crash after the commit but before the file was deleted: the same lines are imported again, without doubling.
        Files.write(new File(spoolDir, "spool.txt").toPath(), copy);
        assertEquals(0, engine.importSpool().join());
        assertEquals(2, count(p));
        assertEquals("shop", list(p).getFirst().source());
    }

    @Test
    void adminClearMovesRowsToTheArchive() {
        UUID p = UUID.randomUUID();
        List<Long> ids = engine.deliver(p, List.of(item("A", 1, "staff", 1), item("B", 2, "staff", 1), item("C", 3, "staff", 1))).join().ids();
        assertEquals(1, engine.archive(p, List.of(ids.get(1)), UUID.randomUUID(), "Admin", 10).join().size());
        assertEquals(2, count(p));
        assertEquals(2, engine.archive(p, null, null, "CONSOLE", 11).join().size());
        assertEquals(0, count(p));
        assertEquals(3, (int) db.blocking(c -> store.clearedCount(c, p)), "never deleted: kept in box_cleared");
        assertTrue(engine.take(p, ids).join().entries().isEmpty(), "cleared rows can't be taken any more");
    }

    @Test
    void theCapNeverRefusesAReturn() {
        UUID p = UUID.randomUUID();
        List<BoxItem> many = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            many.add(item("DIRT", 64, "sell", i));
        }
        BoxEngine.Stored s = engine.deliver(p, many).join();
        assertEquals(12, s.count(), "over a cap of 10 everything is still stored");
        assertEquals(BoxCap.Level.FULL, BoxCap.of(s.count(), 10, 90));
        assertTrue(BoxCap.isFull(s.count(), 10));
    }
}
