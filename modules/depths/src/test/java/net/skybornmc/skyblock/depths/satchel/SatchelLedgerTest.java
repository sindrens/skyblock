package net.skybornmc.skyblock.depths.satchel;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.DatabaseConfig;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.economy.TxGuard;
import net.skybornmc.skyblock.common.economy.TxResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Dupe safety of the satchel against a real (H2, MySQL mode) database: batched saves, atomic take-out, a sale whose
 * payment and satchel removal commit together or not at all, Forge use, profiles kept apart.
 */
class SatchelLedgerTest {

    @TempDir
    Path dir;

    private Database db;
    private SatchelLedger ledger;
    private final UUID profile = UUID.randomUUID();

    @BeforeEach
    void start() throws Exception {
        DatabaseConfig dbc = new DatabaseConfig();
        dbc.type = DatabaseConfig.Type.H2;
        dbc.h2File = "satchel";
        dbc.poolSize = 2;
        dbc.slowQueryMs = 60_000;
        db = new Database(dbc, dir.toFile(), Logger.getLogger("t"));
        db.start();
        new Migrations(db, Logger.getLogger("t")).migrate("depths", getClass().getClassLoader());
        ledger = new SatchelLedger(db, Runnable::run, System::currentTimeMillis, Logger.getLogger("t"));
    }

    @AfterEach
    void stop() {
        db.close();
    }

    private static <T> T await(CompletableFuture<T> f) throws Exception {
        return f.get(10, TimeUnit.SECONDS);
    }

    private Map<String, Long> rows(UUID key) {
        return db.blocking(c -> SatchelStore.read(c, key));
    }

    private SatchelState loadedState(UUID key) throws Exception {
        SatchelState s = new SatchelState(key);
        assertTrue(await(ledger.load(s)));
        return s;
    }

    /** A payment that runs the guard in its own transaction and "pays" into a counter only if everything commits. */
    private static final class FakePay {
        final AtomicLong paid = new AtomicLong();
        final Database db;
        boolean fail;

        FakePay(Database db) {
            this.db = db;
        }

        CompletableFuture<TxResult> pay(long amount, TxGuard guard) {
            return db.transaction(c -> {
                if (!guard.holds(c)) {
                    throw new SatchelLedger.Refused();
                }
                if (fail) {
                    throw new IllegalStateException("payment failed after the guard"); // rolls the guard back too
                }
                return TxResult.of(TxResult.Status.SUCCESS);
            }).thenApply(r -> {
                paid.addAndGet(amount);
                return r;
            }).exceptionally(e -> TxResult.of(e.getCause() instanceof SatchelLedger.Refused ? TxResult.Status.DUPLICATE : TxResult.Status.ERROR));
        }
    }

    @Test
    void batchedDropsAreSavedOnce() throws Exception {
        SatchelState s = loadedState(profile);
        s.add("depths_coal", 40);
        s.add("depths_iron", 2);
        assertTrue(await(ledger.flush(s)));
        s.add("depths_coal", 2);
        assertTrue(await(ledger.flush(s)));
        assertTrue(await(ledger.flush(s))); // nothing pending: no write
        assertEquals(Map.of("depths_coal", 42L, "depths_iron", 2L), rows(profile));
        assertEquals(0, s.inFlight);
    }

    @Test
    void reloadSeesSavedRowsAndUpgrades() throws Exception {
        SatchelState s = loadedState(profile);
        s.add("depths_gold", 5);
        await(ledger.flush(s));
        upgrade(0, 1);
        SatchelState again = loadedState(profile);
        assertEquals(5, again.amount("depths_gold"));
        assertEquals(1, again.upgradeLevel());
    }

    @Test
    void takeOutRemovesInOneTransactionIncludingUnsavedDrops() throws Exception {
        SatchelState s = loadedState(profile);
        s.add("depths_coal", 100);
        await(ledger.flush(s));
        s.add("depths_coal", 28); // not saved yet
        Map<String, Long> take = Map.of("depths_coal", 128L);
        assertTrue(s.escrow(take));
        assertTrue(await(ledger.takeOut(s, take)));
        assertEquals(Map.of(), rows(profile));
        assertEquals(0, s.amount("depths_coal"));
        assertFalse(s.hasPending());
    }

    @Test
    void takeOutOfMoreThanTheDatabaseHoldsRollsBackAndKeepsEverything() throws Exception {
        SatchelState s = loadedState(profile);
        s.add("depths_coal", 10);
        await(ledger.flush(s));
        // Simulate a cache that is wrong (e.g. a row changed behind our back): the DB must refuse.
        db.blocking(c -> SatchelStore.take(c, profile, Map.of("depths_coal", 5L), 1L));
        s.add("depths_coal", 1);
        Map<String, Long> take = Map.of("depths_coal", 11L);
        assertTrue(s.escrow(take));
        assertFalse(await(ledger.takeOut(s, take)));
        assertEquals(11, s.amount("depths_coal"), "escrowed items are held again");
        assertEquals(Map.of("depths_coal", 5L), rows(profile), "the unsaved drop was rolled back with it");
        assertEquals(Map.of("depths_coal", 1L), s.takePending(), "and is pending again, not lost");
    }

    @Test
    void saleCommitsPaymentAndRemovalTogether() throws Exception {
        FakePay pay = new FakePay(db);
        SatchelState s = loadedState(profile);
        s.add("depths_coal", 64);
        s.add("depths_iron", 3);
        Map<String, Long> sell = Map.of("depths_coal", 64L, "depths_iron", 3L);
        assertTrue(s.escrow(sell));
        TxResult r = await(ledger.guarded(s, sell, guard -> pay.pay(1388, guard), TxResult::ok));
        assertTrue(r.ok());
        assertEquals(1388, pay.paid.get());
        assertEquals(Map.of(), rows(profile));
        assertEquals(0, s.used());
    }

    @Test
    void failedPaymentKeepsTheItemsAndPaysNothing() throws Exception {
        FakePay pay = new FakePay(db);
        pay.fail = true;
        SatchelState s = loadedState(profile);
        s.add("depths_coal", 64);
        await(ledger.flush(s));
        s.add("depths_coal", 6);
        Map<String, Long> sell = Map.of("depths_coal", 70L);
        assertTrue(s.escrow(sell));
        TxResult r = await(ledger.guarded(s, sell, guard -> pay.pay(1400, guard), TxResult::ok));
        assertFalse(r.ok());
        assertEquals(0, pay.paid.get());
        assertEquals(70, s.amount("depths_coal"));
        assertEquals(Map.of("depths_coal", 64L), rows(profile));
        assertTrue(await(ledger.flush(s)));
        assertEquals(Map.of("depths_coal", 70L), rows(profile), "the unsaved drops are saved later, once");
    }

    @Test
    void secondSaleOfTheSameItemsCannotBePaid() throws Exception {
        FakePay pay = new FakePay(db);
        SatchelState s = loadedState(profile);
        s.add("depths_coal", 64);
        Map<String, Long> sell = Map.of("depths_coal", 64L);
        assertTrue(s.escrow(sell));
        assertFalse(s.escrow(sell), "memory refuses a second escrow");
        assertTrue(await(ledger.guarded(s, sell, guard -> pay.pay(100, guard), TxResult::ok)).ok());
        // A replay that skips the memory check is refused by the database.
        TxResult replay = await(ledger.guarded(s, sell, guard -> pay.pay(100, guard), TxResult::ok));
        assertFalse(replay.ok());
        assertEquals(100, pay.paid.get());
        // The refused replay "released" items that were never there: the cache shows them, the database does not.
        // This only happens when the caller skips escrow, which SatchelManager never does.
        assertEquals(Map.of(), rows(profile));
    }

    @Test
    void forgeUseIsSavedAsARemoval() throws Exception {
        SatchelState s = loadedState(profile);
        s.add("depths_coal", 300);
        await(ledger.flush(s));
        assertTrue(s.consume(Map.of("depths_coal", 256L)));
        assertTrue(await(ledger.flush(s)));
        assertEquals(Map.of("depths_coal", 44L), rows(profile));
    }

    private boolean upgrade(int from, int to) {
        Boolean ok = db.blocking(c -> SatchelStore.setUpgrade(c, profile, from, to, 1L));
        return ok;
    }

    @Test
    void upgradeLevelOnlyRisesOnce() throws Exception {
        assertTrue(upgrade(0, 1));
        assertFalse(upgrade(0, 1), "bought twice from level 0");
        assertFalse(upgrade(2, 3));
        assertTrue(upgrade(1, 2));
        assertEquals(2, (int) db.blocking(c -> SatchelStore.upgradeLevel(c, profile)));
    }

    @Test
    void profilesAreSeparateAndDeleteRemovesOnlyOne() throws Exception {
        UUID other = UUID.randomUUID();
        SatchelState a = loadedState(profile);
        SatchelState b = loadedState(other);
        a.add("depths_coal", 5);
        b.add("depths_coal", 9);
        await(ledger.flush(a));
        await(ledger.flush(b));
        assertEquals(Map.of("depths_coal", 5L), rows(profile));
        await(ledger.delete(b));
        assertEquals(Map.of(), rows(other));
        assertEquals(Map.of("depths_coal", 5L), rows(profile));
    }

    @Test
    void shutdownFlushWritesWithoutTheMainThread() {
        SatchelState s = new SatchelState(profile);
        s.add("depths_coal", 7);
        ledger.flushBlocking(s);
        assertEquals(Map.of("depths_coal", 7L), rows(profile));
        assertFalse(s.hasPending());
    }
}
