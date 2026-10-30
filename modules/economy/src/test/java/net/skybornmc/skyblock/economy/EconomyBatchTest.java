package net.skybornmc.skyblock.economy;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.DatabaseConfig;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.db.Sql;
import net.skybornmc.skyblock.common.economy.AccountRef;
import net.skybornmc.skyblock.common.economy.BatchResult;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.economy.Transaction;
import net.skybornmc.skyblock.common.economy.TxReason;
import net.skybornmc.skyblock.common.economy.TxResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link EconomyStore#executeBatch}: all legs (and the guard) commit together or not at all (secure trade M3.7). */
class EconomyBatchTest {

    @TempDir
    Path dir;

    private Database db;
    private EconomyStore store;
    private final AtomicLong clock = new AtomicLong(1_000_000L);
    private final AccountRef alice = AccountRef.player(UUID.fromString("00000000-0000-0000-0000-00000000000a"));
    private final AccountRef bob = AccountRef.player(UUID.fromString("ffffffff-0000-0000-0000-00000000000b"));

    @BeforeEach
    void start() throws Exception {
        DatabaseConfig config = new DatabaseConfig();
        config.type = DatabaseConfig.Type.H2;
        config.h2File = "economy-batch-test";
        config.poolSize = 4;
        config.slowQueryMs = 60_000;
        db = new Database(config, dir.toFile(), Logger.getLogger("economy-batch-test"));
        db.start();
        new Migrations(db, Logger.getLogger("economy-batch-test")).migrate("core", getClass().getClassLoader());
        new Migrations(db, Logger.getLogger("economy-batch-test")).migrate("economy", getClass().getClassLoader());
        store = new EconomyStore(db, () -> 1, clock::get);
        store.execute(Transaction.deposit(alice, Currency.MONEY, 1_000, TxReason.ADMIN_GIVE)).join();
        store.execute(Transaction.deposit(bob, Currency.MONEY, 500, TxReason.ADMIN_GIVE)).join();
        store.execute(Transaction.deposit(bob, Currency.SHARDS, 20, TxReason.ADMIN_GIVE)).join();
    }

    @AfterEach
    void stop() {
        db.close();
    }

    private long balance(AccountRef account, Currency currency) {
        return store.balance(account, currency).join();
    }

    private Transaction leg(AccountRef from, AccountRef to, Currency currency, long amount, String key) {
        return Transaction.transfer(from, to, currency, amount, TxReason.TRADE).idempotencyKey(key);
    }

    @Test
    void allLegsCommitTogether() {
        AtomicInteger guardRuns = new AtomicInteger();
        BatchResult r = store.executeBatch(List.of(
                leg(alice, bob, Currency.MONEY, 300, "t:1:a:money"),
                leg(bob, alice, Currency.MONEY, 100, "t:1:b:money"),
                leg(bob, alice, Currency.SHARDS, 5, "t:1:b:shards")), c -> {
            guardRuns.incrementAndGet();
            return true;
        }).join();
        assertTrue(r.ok());
        assertEquals(3, r.results().size());
        assertEquals(800, balance(alice, Currency.MONEY));
        assertEquals(700, balance(bob, Currency.MONEY));
        assertEquals(5, balance(alice, Currency.SHARDS));
        assertEquals(15, balance(bob, Currency.SHARDS));
        assertEquals(1, guardRuns.get());
    }

    @Test
    void oneShortLegRollsBackEverything() {
        BatchResult r = store.executeBatch(List.of(
                leg(alice, bob, Currency.MONEY, 300, "t:2:a:money"),
                leg(bob, alice, Currency.SHARDS, 50, "t:2:b:shards")), null).join();
        assertEquals(TxResult.Status.INSUFFICIENT_FUNDS, r.status());
        assertEquals(1, r.failedIndex());
        assertEquals(1_000, balance(alice, Currency.MONEY));
        assertEquals(500, balance(bob, Currency.MONEY));
        assertEquals(20, balance(bob, Currency.SHARDS));
    }

    @Test
    void failingGuardRollsBackAndReplayIsRefused() {
        BatchResult refused = store.executeBatch(List.of(leg(alice, bob, Currency.MONEY, 10, "t:3:a:money")), c -> false).join();
        assertEquals(TxResult.Status.DUPLICATE, refused.status());
        assertEquals(1_000, balance(alice, Currency.MONEY));

        assertTrue(store.executeBatch(List.of(leg(alice, bob, Currency.MONEY, 10, "t:3:a:money")), null).join().ok());
        BatchResult replay = store.executeBatch(List.of(leg(alice, bob, Currency.MONEY, 10, "t:3:a:money")), null).join();
        assertEquals(TxResult.Status.DUPLICATE, replay.status());
        assertEquals(990, balance(alice, Currency.MONEY));
    }

    @Test
    void guardWorkCommitsWithTheMoney() {
        db.blocking(c -> Sql.update(c, "CREATE TABLE batch_probe (id INT PRIMARY KEY)"));
        store.executeBatch(List.of(leg(alice, bob, Currency.MONEY, 5_000, "t:4:a:money")),
                c -> Sql.update(c, "INSERT INTO batch_probe (id) VALUES (1)") == 1).join();
        assertEquals(0L, probeCount());
        store.executeBatch(List.of(), c -> Sql.update(c, "INSERT INTO batch_probe (id) VALUES (2)") == 1).join();
        assertEquals(1L, probeCount());
    }

    private long probeCount() {
        Long n = db.blocking(c -> Sql.queryOne(c, "SELECT COUNT(*) FROM batch_probe", rs -> rs.getLong(1)).orElse(0L));
        return n;
    }

    @Test
    void duplicateKeysInsideOneBatchAreInvalid() {
        BatchResult r = store.executeBatch(List.of(
                leg(alice, bob, Currency.MONEY, 1, "same"),
                leg(bob, alice, Currency.MONEY, 1, "same")), null).join();
        assertEquals(TxResult.Status.DUPLICATE, r.status());
        assertEquals(1_000, balance(alice, Currency.MONEY));
    }
}
