package net.skybornmc.skyblock.shardblock;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.DatabaseConfig;
import net.skybornmc.skyblock.common.db.Migrations;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ECON-SHARDS against a real (H2) database: a ready block pays once per break, a regrowing block pays nothing, the daily
 * cap holds even with many breaks at the same moment, and a block is archived (picked up) exactly once.
 */
class ShardBlockStoreTest {

    @TempDir
    Path dir;

    private Database db;
    private ShardBlockStore store;
    private final UUID island = UUID.randomUUID();
    private final UUID profile = UUID.randomUUID();

    @BeforeEach
    void start() throws Exception {
        DatabaseConfig dbc = new DatabaseConfig();
        dbc.type = DatabaseConfig.Type.H2;
        dbc.h2File = "shard-test";
        dbc.poolSize = 8;
        dbc.slowQueryMs = 60_000;
        Logger log = Logger.getLogger("shard-test");
        db = new Database(dbc, dir.toFile(), log);
        db.start();
        new Migrations(db, log).migrate("core", getClass().getClassLoader());
        new Migrations(db, log).migrate("shardblock", getClass().getClassLoader());
        store = new ShardBlockStore(db);
    }

    @AfterEach
    void stop() {
        db.close();
    }

    private long place(int x) {
        return store.insert("islands", x, 64, 0, island, profile, 0, 0, 1_000).join();
    }

    /** Like the economy transaction: a guard that says no rolls everything back. */
    private boolean collect(long id, long breaks, long now, int amount, int cap) {
        try {
            return db.blockingTransaction(c -> {
                if (!ShardBlockStore.collectGuard(c, id, breaks, now, now + 1_800_000, amount, profile, 20_000, cap)) {
                    throw new IllegalStateException("guard refused");
                }
                return true;
            });
        } catch (RuntimeException e) {
            return false;
        }
    }

    @Test
    void aReadyBlockPaysOncePerBreak() {
        long id = place(0);
        assertTrue(collect(id, 0, 2_000, 1, 100));
        assertFalse(collect(id, 0, 2_000, 1, 100), "the same break twice pays once");
        assertFalse(collect(id, 1, 3_000, 1, 100), "still regrowing");
        assertTrue(collect(id, 1, 2_000 + 1_800_000, 2, 100), "regrown: the next break pays (double)");
        ShardBlockStore.Row row = store.get(id).join().orElseThrow();
        assertEquals(2, row.breaks());
        assertEquals(3, row.shardsPaid());
        assertEquals(3L, store.dailyUsed(profile, 20_000).join());
    }

    @Test
    void theDailyCapHoldsUnderParallelBreaks() throws Exception {
        int blocks = 50;
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < blocks; i++) {
            ids.add(place(i));
        }
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (long id : ids) {
                // A lock timeout or deadlock victim rolls back too: nothing paid.
                results.add(pool.submit(() -> collect(id, 0, 2_000, 1, 10)));
            }
            int paid = 0;
            for (Future<Boolean> f : results) {
                paid += f.get() ? 1 : 0;
            }
            assertTrue(paid <= 10, "paid " + paid);
        assertTrue(paid >= 1, "at least one break paid");
            assertEquals((long) paid, store.dailyUsed(profile, 20_000).join(), "counter = shards paid");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void noCapMeansNoCap() {
        for (int i = 0; i < 5; i++) {
            assertTrue(collect(place(i), 0, 2_000, 30, 0));
        }
        assertEquals(150L, store.dailyUsed(profile, 20_000).join());
    }

    @Test
    void aBlockIsPickedUpOnce() {
        long id = place(0);
        assertTrue(store.archive(id, "pickup", "player", 5_000).join().isPresent());
        assertTrue(store.archive(id, "pickup", "player", 5_001).join().isEmpty(), "a second pick-up gets nothing");
        assertTrue(store.get(id).join().isEmpty());
        assertEquals(0L, store.stats(20_000, 5).join().total(), "nothing was paid by the archived block");
        assertEquals(0L, store.stats(20_000, 5).join().blocks());
    }

    @Test
    void upgradesMoveOneLevelOnce() {
        long id = place(0);
        assertTrue(db.blockingTransaction(c -> ShardBlockStore.upgradeGuard(c, id, ShardRules.Kind.DOUBLE, 0, 2_000)));
        assertFalse(db.blockingTransaction(c -> ShardBlockStore.upgradeGuard(c, id, ShardRules.Kind.DOUBLE, 0, 2_000)),
                "two buyers of the same level pay once");
        assertEquals(1, store.get(id).join().orElseThrow().doubleLevel());
    }
}
