package net.skybornmc.skyblock.progression;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.DatabaseConfig;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.db.Sql;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code island_masteries} on H2 in MySQL mode with a stand-in for the economy's guarded payment (the rank row and the
 * island bank change in one transaction): a rank exists exactly when it was paid, never twice, and Ascension levels go up
 * one at a time, endlessly.
 */
class MasteryStoreTest {

    @TempDir
    Path dir;

    private Database db;
    private final MasteryStore store = new MasteryStore();
    private final UUID island = UUID.randomUUID();
    private final UUID buyer = UUID.randomUUID();

    private Database open() throws Exception {
        DatabaseConfig config = new DatabaseConfig();
        config.type = DatabaseConfig.Type.H2;
        config.h2File = "masteries-test";
        config.poolSize = 4;
        Database d = new Database(config, dir.toFile(), Logger.getLogger("masteries-test"));
        d.start();
        new Migrations(d, Logger.getLogger("masteries-test")).migrate("progression", getClass().getClassLoader());
        d.blocking(c -> Sql.update(c, "CREATE TABLE IF NOT EXISTS test_bank (owner_id BINARY(16) NOT NULL PRIMARY KEY, balance BIGINT NOT NULL)"));
        return d;
    }

    @BeforeEach
    void start() throws Exception {
        db = open();
        db.blocking(c -> Sql.update(c, "INSERT INTO test_bank (owner_id, balance) VALUES (?, ?)", Sql.toBytes(island), 100_000L));
    }

    @AfterEach
    void stop() {
        db.close();
    }

    private boolean pay(String mastery, int rank, long cost) {
        try {
            return db.blockingTransaction(c -> {
                if (!store.buy(c, island, 0, mastery, rank, buyer, 1L)) {
                    return false;
                }
                if (!withdraw(c, cost)) {
                    throw new SQLException("insufficient funds");
                }
                return true;
            });
        } catch (RuntimeException e) {
            return false;
        }
    }

    private boolean withdraw(Connection c, long cost) throws SQLException {
        return Sql.update(c, "UPDATE test_bank SET balance = balance - ? WHERE owner_id = ? AND balance >= ?", cost, Sql.toBytes(island), cost) == 1;
    }

    private long balance() {
        return db.<Long>blocking(c -> Sql.queryOne(c, "SELECT balance FROM test_bank WHERE owner_id = ?", rs -> rs.getLong(1),
                Sql.toBytes(island)).orElse(-1L));
    }

    private int rank(String mastery) {
        return db.<Integer>blocking(c -> store.level(c, island, 0, mastery));
    }

    @Test
    void ranksArePaidOnceAndInOrder() {
        assertTrue(pay("wings", 1, 60_000));
        assertEquals(40_000, balance());
        assertFalse(pay("wings", 1, 60_000), "a second click pays nothing");
        assertEquals(40_000, balance());
        assertFalse(pay("treasury", 2, 1), "ranks can't be skipped");
        assertEquals(0, rank("treasury"));
    }

    @Test
    void notEnoughMoneyLeavesNoRank() {
        assertFalse(pay("treasury", 1, 100_001));
        assertEquals(0, rank("treasury"), "the rank row rolled back with the failed payment");
        assertEquals(100_000, balance());
    }

    @Test
    void ascensionLevelsGoOnAndOn() {
        for (int level = 1; level <= 40; level++) {
            assertTrue(pay(MasteryRules.ASCENSION_ID, level, 10));
        }
        assertEquals(40, rank(MasteryRules.ASCENSION_ID));
        assertFalse(pay(MasteryRules.ASCENSION_ID, 40, 10));
        assertEquals(100_000 - 400, balance());
        Map<String, Integer> loaded = db.blocking(c -> store.load(c, 0)).get(island);
        assertEquals(40, loaded.get(MasteryRules.ASCENSION_ID));
    }

    @Test
    void twoMembersBuyingAtOnceOnlyPayOnce() throws Exception {
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger bought = new AtomicInteger();
        List<CompletableFuture<Void>> runs = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            runs.add(CompletableFuture.runAsync(() -> {
                try {
                    go.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                if (pay("homestead", 1, 30_000)) {
                    bought.incrementAndGet();
                }
            }));
        }
        go.countDown();
        CompletableFuture.allOf(runs.toArray(CompletableFuture[]::new)).join();
        assertEquals(1, bought.get());
        assertEquals(1, rank("homestead"));
        assertEquals(70_000, balance());
    }

    @Test
    void ranksComeBackAfterARestart() throws Exception {
        assertTrue(pay("treasury", 1, 1_000));
        assertTrue(pay("homestead", 1, 1_000));
        db.close();
        db = open();
        Map<String, Integer> loaded = db.blocking(c -> store.load(c, 0)).get(island);
        MasteryCatalog catalog = MasteryCatalogTest.bundled();
        assertEquals(1, MasteryRules.whole(catalog, loaded, MasteryEffect.TREASURY), "fee 1.5% again after a restart");
        assertEquals(1, MasteryRules.extraHomes(catalog, loaded));
    }
}
