package net.skybornmc.skyblock.moderation;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.DatabaseConfig;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.db.Sql;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P7 against a real (H2, MySQL mode) database: island punishment types fit the widened column, freeze/trade blocks
 * (one active per kind, lift once, expire once), money sanctions claimed for restore exactly once (the guard the economy
 * runs in its own transaction), and the confiscated item life cycle (pending → held → restored once, back to held when
 * it could not be handed over; a pending row is never restorable).
 */
class P7StoreTest {

    private static final UUID STEVE = TestData.STEVE;
    private static final UUID ALEX = TestData.ALEX;

    @TempDir
    Path dir;

    private Database db;
    private SanctionStore store;
    private PunishmentStore punishments;
    private long now;

    @BeforeEach
    void start() throws Exception {
        DatabaseConfig config = new DatabaseConfig();
        config.type = DatabaseConfig.Type.H2;
        config.h2File = "moderation-p7-test";
        config.poolSize = 4;
        config.slowQueryMs = 60_000;
        db = new Database(config, dir.toFile(), Logger.getLogger("moderation-p7-test"));
        db.start();
        new Migrations(db, Logger.getLogger("moderation-p7-test")).migrate("core", getClass().getClassLoader());
        new Migrations(db, Logger.getLogger("moderation-p7-test")).migrate("moderation", getClass().getClassLoader());
        store = new SanctionStore(db);
        punishments = new PunishmentStore(db);
        now = System.currentTimeMillis();
    }

    @AfterEach
    void stop() {
        db.close();
    }

    private Sanction draft(Sanction.Kind kind, Long expires, Sanction.State state) {
        return new Sanction(0, kind, STEVE, "Steve", kind == Sanction.Kind.FREEZE || kind == Sanction.Kind.TRADE ? null : STEVE,
                ALEX, "Alex", "Duping", now, expires, kind == Sanction.Kind.MONEY ? 500L : null,
                kind == Sanction.Kind.MONEY ? "money" : null, kind == Sanction.Kind.ITEM ? "Diamond x3" : null, state,
                null, null, null, null);
    }

    @Test
    void islandPunishmentTypesAreStoredAndReadBack() {
        PunishDecision decision = PunishDecision.full(Duration.ofSeconds(15), Approval.Settings.DEFAULT);
        PunishDecision.Actor staff = TestData.staff(null);
        for (Punishment.Type type : List.of(Punishment.Type.ISLAND_KICK, Punishment.Type.ISLAND_BAN, Punishment.Type.COOP_REMOVE,
                Punishment.Type.COOP_BLOCK)) {
            Duration length = type.timed() ? Duration.ofDays(1) : null;
            Punishment d = new Punishment(0, type, STEVE, "Steve", null, false, "Griefing", "coop-griefing", 1, ALEX, "Alex",
                    now, null, false, null, null, null, null, null);
            PunishDecision.Input in = new PunishDecision.Input(staff, type, length, false, false, false, false, true, null, null, now);
            PunishmentStore.IssueResult r = punishments.issue(d, in, decision, null, Duration.ofHours(24), null).join();
            assertTrue(r.verdict().applied(), type + ": " + r.verdict().stage());
            assertEquals(type, r.punishment().type());
            assertEquals(type.timed(), r.punishment().expires() != null, type + " has a length only when timed");
        }
        List<Punishment> history = punishments.history(STEVE).join();
        assertEquals(4, history.size());
        assertEquals(1, history.stream().filter(p -> p.type() == Punishment.Type.COOP_BLOCK && p.active(now + 1)).count());
    }

    @Test
    void oneActiveBlockPerKindAndLiftOnce() {
        SanctionStore.BlockResult first = store.block(draft(Sanction.Kind.FREEZE, null, Sanction.State.ACTIVE), now).join();
        assertTrue(first.ok());
        SanctionStore.BlockResult second = store.block(draft(Sanction.Kind.FREEZE, now + 60_000, Sanction.State.ACTIVE), now).join();
        assertFalse(second.ok(), "already frozen: lift first");
        assertEquals(first.created().id(), second.existing().id());
        assertTrue(store.block(draft(Sanction.Kind.TRADE, now + 60_000, Sanction.State.ACTIVE), now).join().ok(), "another kind");
        assertEquals(2, store.activeBlocks().join().size());
        Optional<Sanction> lifted = store.lift(first.created().id(), ALEX, "Alex", "Lifted by staff", now).join();
        assertTrue(lifted.isPresent());
        assertEquals(Sanction.State.LIFTED, lifted.get().state());
        assertEquals("Alex", lifted.get().handledByName());
        assertTrue(store.lift(first.created().id(), ALEX, "Alex", "again", now).join().isEmpty(), "first click wins");
        assertTrue(store.block(draft(Sanction.Kind.FREEZE, null, Sanction.State.ACTIVE), now).join().ok(), "after a lift");
    }

    @Test
    void expiredBlocksAreMarkedOnceAndFreeTheSlot() {
        Sanction s = store.block(draft(Sanction.Kind.TRADE, now + 1000, Sanction.State.ACTIVE), now).join().created();
        assertTrue(store.expire(List.of(s.id()), now).join().isEmpty(), "not yet");
        List<Sanction> done = store.expire(List.of(s.id()), now + 1000).join();
        assertEquals(1, done.size());
        assertEquals(Sanction.State.EXPIRED, done.getFirst().state());
        assertTrue(store.expire(List.of(s.id()), now + 2000).join().isEmpty(), "audited once");
        assertTrue(store.lift(s.id(), ALEX, "Alex", "x", now + 2000).join().isEmpty(), "can't lift an ended one");
        assertTrue(store.block(draft(Sanction.Kind.TRADE, null, Sanction.State.ACTIVE), now + 2000).join().ok());
    }

    @Test
    void moneyIsClaimedForRestoreExactlyOnce() {
        long id = db.blockingTransaction(c -> SanctionStore.insertMoney(c, draft(Sanction.Kind.MONEY, null, Sanction.State.ACTIVE)));
        Sanction s = store.get(id).join().orElseThrow();
        assertEquals(500L, s.amount());
        assertEquals(STEVE, s.profileKey());
        assertTrue(s.restorable());
        Boolean first = db.blockingTransaction(c -> SanctionStore.claimMoneyRestore(c, id, ALEX, "Alex", "Restored", now));
        Boolean again = db.blockingTransaction(c -> SanctionStore.claimMoneyRestore(c, id, ALEX, "Alex", "Restored", now));
        assertTrue(first);
        assertFalse(again,
                "a second restore pays nothing (the economy gets DUPLICATE)");
        assertEquals(Sanction.State.RESTORED, store.get(id).join().orElseThrow().state());
        assertEquals(List.of(), store.restorableIds(10).join());
    }

    @Test
    void aRolledBackRemoveLeavesNoSanction() {
        try {
            db.blockingTransaction(c -> {
                SanctionStore.insertMoney(c, draft(Sanction.Kind.MONEY, null, Sanction.State.ACTIVE));
                throw new java.sql.SQLException("balance too low (the economy rolls back)");
            });
        } catch (RuntimeException expected) {
            // the economy's transaction failed: the sanction row must be gone too
        }
        int rows = db.blocking(c -> Sql.queryOne(c, "SELECT COUNT(*) FROM sanctions", rs -> rs.getInt(1)).orElse(-1));
        assertEquals(0, rows);
    }

    @Test
    void confiscatedItemLifeCycle() {
        byte[] item = {1, 2, 3, 4};
        long id = store.insertPendingItem(draft(Sanction.Kind.ITEM, null, Sanction.State.PENDING), item, "ender", 5).join();
        assertTrue(store.claimItemRestore(id, ALEX, "Alex", "x", now).join().isEmpty(), "pending is never restorable");
        assertTrue(store.settleItem(id, true, now).join());
        assertFalse(store.settleItem(id, false, now).join(), "settled once");
        assertEquals(List.of(id), store.restorableIds(10).join());
        Optional<byte[]> back = store.claimItemRestore(id, ALEX, "Alex", "Restored", now).join();
        assertArrayEquals(item, back.orElseThrow());
        assertTrue(store.claimItemRestore(id, ALEX, "Alex", "Restored", now).join().isEmpty(), "first click wins");
        assertTrue(store.unclaimItemRestore(id).join(), "player left: held again");
        assertArrayEquals(item, store.claimItemRestore(id, ALEX, "Alex", "Restored", now).join().orElseThrow());
        assertEquals("ender", db.blocking(c -> Sql.queryOne(c, "SELECT source FROM confiscated_items WHERE sanction_id = ?",
                rs -> rs.getString(1), id).orElse("?")));
    }

    @Test
    void aMovedItemIsMarkedFailedAndHoldsNothing() {
        long id = store.insertPendingItem(draft(Sanction.Kind.ITEM, null, Sanction.State.PENDING), new byte[]{9}, "inventory", 0).join();
        assertTrue(store.settleItem(id, false, now).join());
        Sanction s = store.get(id).join().orElseThrow();
        assertEquals(Sanction.State.FAILED, s.state());
        assertEquals("not-held", SanctionRules.restoreProblem(s));
        assertEquals(0L, store.givenBy(ALEX, now - 1, now + 1).join(), "failed attempts don't count in /pstats");
    }

    @Test
    void historyAndStats() {
        store.block(draft(Sanction.Kind.FREEZE, null, Sanction.State.ACTIVE), now).join();
        db.blockingTransaction(c -> SanctionStore.insertMoney(c, draft(Sanction.Kind.MONEY, null, Sanction.State.ACTIVE)));
        assertEquals(2, store.history(STEVE).join().size());
        assertEquals(0, store.history(ALEX).join().size());
        assertEquals(2L, store.givenBy(ALEX, now - 1, now + 1).join());
        assertEquals(0L, store.givenBy(STEVE, now - 1, now + 1).join());
    }
}
