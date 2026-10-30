package net.skybornmc.skyblock.crates;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.DatabaseConfig;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.economy.BatchResult;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.economy.Transaction;
import net.skybornmc.skyblock.common.economy.TxResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The crates tables and the atomic open on H2 in MySQL mode (same SQL as MariaDB). */
class CrateStoreTest {

    @TempDir
    Path dir;
    private Database db;
    private final UUID profile = UUID.randomUUID();
    private final UUID player = UUID.randomUUID();
    /** Transactions the fake economy committed. */
    private final List<Transaction> paid = new ArrayList<>();

    @BeforeEach
    void start() throws Exception {
        DatabaseConfig config = new DatabaseConfig();
        config.type = DatabaseConfig.Type.H2;
        config.h2File = "crates-test";
        config.poolSize = 2;
        db = new Database(config, dir.toFile(), Logger.getLogger("crates-test"));
        db.start();
        new Migrations(db, Logger.getLogger("crates-test")).migrate("crates", getClass().getClassLoader());
    }

    @AfterEach
    void stop() {
        db.close();
    }

    /** Runs the guard in a real transaction; the transactions are "paid" only if it commits (like executeBatch). */
    private OpenEngine.Runner runner() {
        return (txs, guard) -> CompletableFuture.completedFuture(db.blocking(c -> {
            c.setAutoCommit(false);
            try {
                if (!guard.holds(c)) {
                    c.rollback();
                    return BatchResult.failed(TxResult.Status.DUPLICATE, -1);
                }
                c.commit();
                paid.addAll(txs);
                return BatchResult.success(List.of());
            } catch (java.sql.SQLException e) {
                c.rollback();
                return BatchResult.failed(TxResult.Status.ERROR, -1);
            } finally {
                c.setAutoCommit(true);
            }
        }));
    }

    @Test
    void keysAddAndTakeOnlyWhatIsThere() {
        db.blockingTransaction(c -> {
            CrateStore.addKeys(c, profile, "ember", 3, 0, 1);
            CrateStore.addKeys(c, profile, "ember", 0, 2, 2);
            return null;
        });
        assertEquals(new CrateStore.Keys(3, 2), db.blocking(c -> CrateStore.keys(c, profile, "ember")));
        assertTrue(db.<Boolean>blocking(c -> CrateStore.take(c, profile, "ember", 2, 1, 3)));
        assertFalse(db.<Boolean>blocking(c -> CrateStore.take(c, profile, "ember", 2, 0, 4)), "only 1 bound left");
        assertFalse(db.<Boolean>blocking(c -> CrateStore.take(c, profile, "ember", 0, 0, 4)), "taking nothing is refused");
        assertEquals(new CrateStore.Keys(1, 1), db.blocking(c -> CrateStore.keys(c, profile, "ember")));
        assertEquals(CrateStore.Keys.NONE, db.blocking(c -> CrateStore.keys(c, UUID.randomUUID(), "ember")), "profiles are separate");
    }

    @Test
    void pityIsCompareAndSet() {
        assertTrue(db.<Boolean>blocking(c -> CrateStore.setPity(c, profile, "ember", Optional.empty(), 4, 1, 1)));
        assertFalse(db.<Boolean>blocking(c -> {
            try {
                return CrateStore.setPity(c, profile, "ember", Optional.empty(), 9, 1, 1);
            } catch (java.sql.SQLException duplicateRow) {
                return false;
            }
        }), "a second first row is refused");
        Optional<CrateStore.Pity> now = db.blocking(c -> CrateStore.pity(c, profile, "ember"));
        assertEquals(4, now.orElseThrow().counter(1));
        assertEquals(0, now.orElseThrow().counter(2), "another season starts at 0");
        assertFalse(db.<Boolean>blocking(c -> CrateStore.setPity(c, profile, "ember", Optional.of(new CrateStore.Pity(3, 1)), 5, 1, 2)));
        assertTrue(db.<Boolean>blocking(c -> CrateStore.setPity(c, profile, "ember", now, 5, 1, 2)));
    }

    private OpenPlan.Plan plan(CrateCatalog.Crate crate, int bound, int tradeable, int counter) {
        return OpenPlan.build(crate, new CrateMath.KeyUse(bound, tradeable), counter, Fixtures.context(1, k -> false), new Random(3),
                UUID::randomUUID);
    }

    @Test
    void anOpenTakesKeysRollsAndPaysInOneGo() {
        CrateCatalog.Crate ember = Fixtures.bundled().crate("ember").orElseThrow();
        db.blockingTransaction(c -> {
            CrateStore.addKeys(c, profile, "ember", 4, 6, 1);
            return null;
        });
        CrateStore.State state = db.blocking(c -> CrateStore.state(c, profile, "ember"));
        OpenPlan.Plan p = plan(ember, 4, 6, 0);
        OpenEngine.Status status = OpenEngine.commit(runner(), p, profile, player, state.pity(), 1, Actor.SYSTEM, 10).join();
        assertEquals(OpenEngine.Status.OK, status);
        long cinderBound = p.keyGrants().stream().filter(k -> k.crate().equals("cinder")).mapToLong(OpenPlan.KeyGrant::bound).sum();
        long cinderTrade = p.keyGrants().stream().filter(k -> k.crate().equals("cinder")).mapToLong(OpenPlan.KeyGrant::tradeable).sum();
        assertEquals(new CrateStore.Keys(0, 0), db.blocking(c -> CrateStore.keys(c, profile, "ember")));
        assertEquals(new CrateStore.Keys(cinderBound, cinderTrade), db.blocking(c -> CrateStore.keys(c, profile, "cinder")));
        assertEquals(p.pityAfter(), db.blocking(c -> CrateStore.pity(c, profile, "ember")).orElseThrow().counter(1));
        List<CrateStore.OpenRow> rows = db.blocking(c -> CrateStore.batch(c, p.batchId()));
        assertEquals(10, rows.size());
        assertEquals(4, rows.stream().filter(CrateStore.OpenRow::boundKey).count());
        for (CrateStore.OpenRow r : rows) {
            OpenPlan.Grant g = p.grants().stream().filter(x -> x.openId().equals(r.openId())).findFirst().orElseThrow();
            assertEquals(g.inTransaction() ? CrateStore.DONE : CrateStore.PENDING, r.state());
        }
        long money = paid.stream().filter(t -> t.currency() == Currency.MONEY).mapToLong(Transaction::amount).sum();
        assertEquals(p.total(Currency.MONEY), money);
        assertTrue(paid.stream().allMatch(t -> t.idempotencyKey().startsWith("crate_open:" + p.batchId())));
        assertTrue(paid.stream().allMatch(t -> t.reason().equals("crate_reward")));
    }

    @Test
    void changedKeysOrPityRollEverythingBack() {
        CrateCatalog.Crate ember = Fixtures.bundled().crate("ember").orElseThrow();
        db.blockingTransaction(c -> {
            CrateStore.addKeys(c, profile, "ember", 1, 0, 1);
            return null;
        });
        CrateStore.State state = db.blocking(c -> CrateStore.state(c, profile, "ember"));
        // Planned for 2 keys but only 1 is there (another open took one meanwhile): nothing happens.
        OpenPlan.Plan tooMany = plan(ember, 2, 0, 0);
        assertEquals(OpenEngine.Status.CHANGED,
                OpenEngine.commit(runner(), tooMany, profile, player, state.pity(), 1, Actor.SYSTEM, 10).join());
        assertEquals(new CrateStore.Keys(1, 0), db.blocking(c -> CrateStore.keys(c, profile, "ember")));
        assertTrue(db.<List<CrateStore.OpenRow>>blocking(c -> CrateStore.batch(c, tooMany.batchId())).isEmpty());
        assertTrue(paid.isEmpty());
        // Pity moved since it was read: nothing happens either.
        db.blocking(c -> CrateStore.setPity(c, profile, "ember", Optional.empty(), 7, 1, 1));
        OpenPlan.Plan stale = plan(ember, 1, 0, 0);
        assertEquals(OpenEngine.Status.CHANGED,
                OpenEngine.commit(runner(), stale, profile, player, Optional.empty(), 1, Actor.SYSTEM, 11).join());
        assertEquals(new CrateStore.Keys(1, 0), db.blocking(c -> CrateStore.keys(c, profile, "ember")));
        assertEquals(7, db.blocking(c -> CrateStore.pity(c, profile, "ember")).orElseThrow().counter(1));
    }

    @Test
    void aPlanThatDoesNotMatchItsKeysIsRefused() {
        CrateCatalog.Crate ember = Fixtures.bundled().crate("ember").orElseThrow();
        OpenPlan.Plan p = plan(ember, 1, 0, 0);
        OpenPlan.Plan broken = new OpenPlan.Plan(p.batchId(), ember, new CrateMath.KeyUse(2, 0), p.grants(), p.pityAfter());
        assertEquals(OpenEngine.Status.ERROR, OpenEngine.commit(runner(), broken, profile, player, Optional.empty(), 1, Actor.SYSTEM, 1).join());
    }

    @Test
    void recoveryRegrantsCosmeticsAndReviewsItems() {
        long now = 5;
        UUID batch = UUID.randomUUID();
        CrateStore.OpenRow item = new CrateStore.OpenRow(UUID.randomUUID(), batch, profile, player, "ember", "book", "enchant-book",
                "vein_rend:1", 1, false, false, true, CrateStore.PENDING, 1, now);
        CrateStore.OpenRow tag = new CrateStore.OpenRow(UUID.randomUUID(), batch, profile, player, "ember", "tag", "tag", "spark", 1,
                false, false, true, CrateStore.PENDING, 1, now);
        CrateStore.OpenRow paidRow = new CrateStore.OpenRow(UUID.randomUUID(), batch, profile, player, "ember", "money", "money",
                "money", 900, false, false, true, CrateStore.DONE, 1, now);
        db.blocking(c -> {
            CrateStore.insertOpens(c, List.of(item, tag, paidRow));
            return null;
        });
        List<List<CrateStore.OpenRow>> r = db.blocking(c -> CrateStore.recover(c, 9));
        assertEquals(List.of(tag.openId()), r.get(0).stream().map(CrateStore.OpenRow::openId).toList());
        assertEquals(List.of(item.openId()), r.get(1).stream().map(CrateStore.OpenRow::openId).toList());
        List<CrateStore.OpenRow> after = db.blocking(c -> CrateStore.batch(c, batch));
        assertEquals(CrateStore.REVIEW, after.stream().filter(x -> x.openId().equals(item.openId())).findFirst().orElseThrow().state());
        assertEquals(CrateStore.DONE, after.stream().filter(x -> x.openId().equals(paidRow.openId())).findFirst().orElseThrow().state());
        assertTrue(db.<Boolean>blocking(c -> CrateStore.move(c, tag.openId(), CrateStore.PENDING, CrateStore.DELIVERED, 10)));
        assertFalse(db.<Boolean>blocking(c -> CrateStore.move(c, tag.openId(), CrateStore.PENDING, CrateStore.DELIVERED, 11)), "exactly once");
        assertTrue(db.<List<List<CrateStore.OpenRow>>>blocking(c -> CrateStore.recover(c, 12)).get(1).isEmpty(), "a review row is never handed out again");
    }

    @Test
    void deletedProfileLosesKeysAndPity() {
        db.blockingTransaction(c -> {
            CrateStore.addKeys(c, profile, "ember", 3, 3, 1);
            CrateStore.setPity(c, profile, "ember", Optional.empty(), 2, 1, 1);
            return null;
        });
        db.blocking(c -> {
            CrateStore.deleteProfile(c, profile);
            return null;
        });
        assertTrue(db.<java.util.Map<String, CrateStore.Keys>>blocking(c -> CrateStore.keys(c, profile)).isEmpty());
        assertTrue(db.<java.util.Map<String, CrateStore.Pity>>blocking(c -> CrateStore.pities(c, profile)).isEmpty());
    }
}
