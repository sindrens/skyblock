package net.skybornmc.skyblock.trade;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditConfig;
import net.skybornmc.skyblock.common.audit.AuditLog;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.DatabaseConfig;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.db.Sql;
import net.skybornmc.skyblock.common.economy.Currency;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The escrow and the swap against a real (H2, MySQL mode) database: nothing can be lost or copied. */
class TradeEngineTest {

    @TempDir
    Path dir;

    private Database db;
    private TradeEngine engine;
    private TradeStore store;
    private FakeEconomy economy;
    private final AtomicLong clock = new AtomicLong(1_000_000_000L);

    private final UUID alice = UUID.randomUUID();
    private final UUID aliceProfile = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final UUID bobProfile = UUID.randomUUID();

    @BeforeEach
    void start() throws Exception {
        DatabaseConfig dbc = new DatabaseConfig();
        dbc.type = DatabaseConfig.Type.H2;
        dbc.h2File = "trade-test";
        dbc.poolSize = 8;
        dbc.slowQueryMs = 60_000;
        Logger log = Logger.getLogger("trade-test");
        db = new Database(dbc, dir.toFile(), log);
        db.start();
        new Migrations(db, log).migrate("core", getClass().getClassLoader());
        new Migrations(db, log).migrate("trade", getClass().getClassLoader());
        AuditLog audit = new AuditLog(db, new AuditConfig(), dir.toFile(), log);
        store = new TradeStore(db);
        economy = new FakeEconomy(db);
        engine = new TradeEngine(store, () -> economy, audit, clock::get, log);
        economy.set(aliceProfile, Currency.MONEY, 10_000);
        economy.set(bobProfile, Currency.MONEY, 500);
        economy.set(bobProfile, Currency.SHARDS, 40);
    }

    @AfterEach
    void stop() {
        db.close();
    }

    private static Offer item(String key, int amount) {
        return new Offer(0, new byte[]{1, 2, (byte) key.hashCode(), (byte) amount}, key, null, "DIAMOND", amount, key);
    }

    private TradeSession open() {
        TradeSession.Side a = new TradeSession.Side(alice, aliceProfile, "Alice");
        TradeSession.Side b = new TradeSession.Side(bob, bobProfile, "Bob");
        long id = engine.open(a, b).join();
        return new TradeSession(id, a, b, clock.get());
    }

    /** Offers an item the way the module does (reserve, store, finish). */
    private Offer offer(TradeSession s, UUID player, UUID profile, Offer offer) {
        assertTrue(s.beginAdd(player, 12));
        Optional<Long> row = engine.escrow(s.id, player, profile, offer, UUID.randomUUID().toString()).join();
        assertTrue(row.isPresent());
        Offer stored = offer.withRow(row.get());
        s.finishAdd(player, stored);
        return stored;
    }

    private void bothConfirmAndCountDown(TradeSession s) {
        s.confirm(alice, clock.get(), 3000);
        assertEquals(TradeSession.Confirm.COUNTDOWN, s.confirm(bob, clock.get(), 3000));
        assertTrue(s.countdownDone(clock.get() + 3000));
        assertTrue(s.beginCompleting());
    }

    private List<TradeStore.EscrowRow> rows(TradeSession s) {
        return store.rows(s.id).join();
    }

    private String status(long id) {
        return store.status(id).join().orElse(null);
    }

    @Test
    void completedTradeSwapsItemsAndMoneyAtomically() {
        TradeSession s = open();
        Offer sword = offer(s, alice, aliceProfile, item("sword", 1));
        Offer gems = offer(s, bob, bobProfile, item("diamond", 64));
        assertTrue(s.setMoney(alice, Currency.MONEY, 2_500));
        assertTrue(s.setMoney(bob, Currency.SHARDS, 15));
        bothConfirmAndCountDown(s);

        TradeEngine.CompleteResult r = engine.complete(s.snapshot(), Actor.SYSTEM).join();

        assertEquals(TradeEngine.Outcome.COMPLETED, r.outcome());
        assertEquals(TradeStore.COMPLETED, status(s.id));
        for (TradeStore.EscrowRow row : rows(s)) {
            assertEquals(TradeStore.PENDING, row.state());
            if (row.id() == sword.rowId()) {
                assertEquals(bobProfile, row.ownerProfile());
                assertEquals(aliceProfile, row.originProfile());
            } else {
                assertEquals(gems.rowId(), row.id());
                assertEquals(aliceProfile, row.ownerProfile());
            }
        }
        assertEquals(7_500, economy.get(aliceProfile, Currency.MONEY));
        assertEquals(3_000, economy.get(bobProfile, Currency.MONEY));
        assertEquals(15, economy.get(aliceProfile, Currency.SHARDS));
        assertEquals(25, economy.get(bobProfile, Currency.SHARDS));
        assertEquals(2, economy.done.size());
        assertTrue(economy.done.stream().allMatch(t -> t.idempotencyKey().startsWith("trade:" + s.id + ":")));
    }

    @Test
    void replayedSwapChangesNothing() {
        TradeSession s = open();
        offer(s, alice, aliceProfile, item("sword", 1));
        s.setMoney(alice, Currency.MONEY, 1_000);
        bothConfirmAndCountDown(s);
        TradeSession.Snapshot snap = s.snapshot();
        assertEquals(TradeEngine.Outcome.COMPLETED, engine.complete(snap, Actor.SYSTEM).join().outcome());
        TradeEngine.Outcome second = engine.complete(snap, Actor.SYSTEM).join().outcome();
        assertEquals(TradeEngine.Outcome.STATE_CHANGED, second);
        assertEquals(9_000, economy.get(aliceProfile, Currency.MONEY));
        assertEquals(1_500, economy.get(bobProfile, Currency.MONEY));
    }

    @Test
    void moneyShortChangesNothingAndTheTradeStaysOpen() {
        TradeSession s = open();
        Offer sword = offer(s, alice, aliceProfile, item("sword", 1));
        s.setMoney(bob, Currency.MONEY, 400);
        bothConfirmAndCountDown(s);
        economy.set(bobProfile, Currency.MONEY, 100); // spent it meanwhile

        TradeEngine.CompleteResult r = engine.complete(s.snapshot(), Actor.SYSTEM).join();

        assertEquals(TradeEngine.Outcome.INSUFFICIENT, r.outcome());
        assertEquals(bob, r.shortSide());
        assertEquals(TradeStore.OPEN, status(s.id));
        TradeStore.EscrowRow row = rows(s).getFirst();
        assertEquals(sword.rowId(), row.id());
        assertEquals(TradeStore.HELD, row.state());
        assertEquals(aliceProfile, row.ownerProfile());
        assertEquals(10_000, economy.get(aliceProfile, Currency.MONEY));
        assertEquals(100, economy.get(bobProfile, Currency.MONEY));
        s.reopen();
        assertEquals(TradeSession.State.OPEN, s.state());
        assertFalse(s.a.ready());
        assertFalse(s.b.ready());
    }

    @Test
    void itemsThatDifferFromTheMenuStopTheSwap() {
        TradeSession s = open();
        offer(s, alice, aliceProfile, item("sword", 1));
        bothConfirmAndCountDown(s);
        TradeSession.Snapshot snap = s.snapshot();
        // A row the menu doesn't know about (e.g. a late write) must never be swapped blindly.
        engine.escrow(s.id, bob, bobProfile, item("extra", 1), "t-extra").join();

        assertEquals(TradeEngine.Outcome.STATE_CHANGED, engine.complete(snap, Actor.SYSTEM).join().outcome());
        assertEquals(TradeStore.OPEN, status(s.id));
        assertTrue(rows(s).stream().allMatch(r -> r.state().equals(TradeStore.HELD) && r.ownerProfile().equals(r.originProfile())));
    }

    @Test
    void ownProfilesCanNeverTrade() {
        TradeSession s = open();
        offer(s, alice, aliceProfile, item("sword", 1));
        s.setMoney(alice, Currency.MONEY, 1_000);
        bothConfirmAndCountDown(s);
        AtomicBoolean asked = new AtomicBoolean();
        engine.ownProfileCheck((a, b, actor) -> {
            asked.set(true);
            return true; // P4: same player
        });

        assertEquals(TradeEngine.Outcome.OWN_PROFILE, engine.complete(s.snapshot(), Actor.SYSTEM).join().outcome());
        assertTrue(asked.get());
        assertEquals(TradeStore.OPEN, status(s.id));
        assertEquals(10_000, economy.get(aliceProfile, Currency.MONEY));
        assertEquals(TradeStore.HELD, rows(s).getFirst().state());
    }

    @Test
    void cancelReturnsEveryItemToItsOwner() {
        TradeSession s = open();
        offer(s, alice, aliceProfile, item("sword", 1));
        offer(s, bob, bobProfile, item("apple", 16));

        TradeStore.CancelOutcome outcome = engine.cancel(s.id, "cancelled", Actor.SYSTEM, alice, "Alice", bob, "Bob").join();

        assertEquals(TradeStore.OPEN, outcome.previous());
        assertEquals(2, outcome.returned().size());
        assertEquals(TradeStore.CANCELLED, status(s.id));
        for (TradeStore.EscrowRow row : rows(s)) {
            assertEquals(TradeStore.PENDING, row.state());
            assertEquals(row.originProfile(), row.ownerProfile());
        }
        // Nothing can be added or swapped afterwards.
        assertTrue(engine.escrow(s.id, alice, aliceProfile, item("late", 1), "t-late").join().isEmpty());
        assertFalse(engine.release(s.id, rows(s).getFirst().id(), aliceProfile).join());
        assertEquals(TradeStore.CANCELLED, engine.cancel(s.id, "again", Actor.SYSTEM, alice, "Alice", bob, "Bob").join().previous());
    }

    @Test
    void cancelAfterCompletionChangesNothing() {
        TradeSession s = open();
        offer(s, alice, aliceProfile, item("sword", 1));
        bothConfirmAndCountDown(s);
        engine.complete(s.snapshot(), Actor.SYSTEM).join();
        TradeStore.CancelOutcome outcome = engine.cancel(s.id, "quit", Actor.SYSTEM, alice, "Alice", bob, "Bob").join();
        assertEquals(TradeStore.COMPLETED, outcome.previous());
        assertEquals(bobProfile, rows(s).getFirst().ownerProfile());
    }

    @Test
    void takingAnItemBackReleasesItOnce() {
        TradeSession s = open();
        Offer sword = offer(s, alice, aliceProfile, item("sword", 1));
        assertTrue(s.beginRemove(alice, sword.rowId()).isPresent());
        assertTrue(engine.release(s.id, sword.rowId(), aliceProfile).join());
        s.finishRemove(alice);
        assertFalse(engine.release(s.id, sword.rowId(), aliceProfile).join());
        assertFalse(engine.release(s.id, sword.rowId(), bobProfile).join());
        assertEquals(TradeStore.PENDING, rows(s).getFirst().state());
        // The released row is not part of the swap any more.
        s.setMoney(alice, Currency.MONEY, 10);
        bothConfirmAndCountDown(s);
        assertEquals(TradeEngine.Outcome.COMPLETED, engine.complete(s.snapshot(), Actor.SYSTEM).join().outcome());
        assertEquals(aliceProfile, rows(s).getFirst().ownerProfile());
    }

    @Test
    void crashRecoveryGivesEverythingBack() {
        TradeSession s1 = open();
        offer(s1, alice, aliceProfile, item("sword", 1));
        offer(s1, bob, bobProfile, item("apple", 3));
        TradeSession s2 = open();
        offer(s2, alice, aliceProfile, item("pickaxe", 1));
        TradeSession done = open();
        offer(done, bob, bobProfile, item("bread", 5));
        bothConfirmAndCountDown(done);
        engine.complete(done.snapshot(), Actor.SYSTEM).join();

        // "Restart": a new engine on the same database.
        TradeEngine fresh = new TradeEngine(store, () -> economy, new AuditLog(db, new AuditConfig(), dir.toFile(),
                Logger.getLogger("trade-test")), clock::get, Logger.getLogger("trade-test"));
        clock.incrementAndGet();
        TradeSession startedDuringRecovery = open(); // created at the cutoff: must not be touched
        assertEquals(2, fresh.recover(clock.get()).join());
        assertEquals(TradeStore.OPEN, status(startedDuringRecovery.id));

        assertEquals(TradeStore.CANCELLED, status(s1.id));
        assertEquals(TradeStore.CANCELLED, status(s2.id));
        assertEquals(TradeStore.COMPLETED, status(done.id));
        for (TradeSession s : List.of(s1, s2)) {
            assertTrue(rows(s).stream().allMatch(r -> r.state().equals(TradeStore.PENDING) && r.ownerProfile().equals(r.originProfile())));
        }
        assertEquals(aliceProfile, rows(done).getFirst().ownerProfile());
        assertEquals(0, fresh.recover(clock.get()).join());
        assertTrue(store.pendingOwners().join().containsAll(List.of(aliceProfile, bobProfile)));
    }

    @Test
    void orphanTradesAreCancelledButActiveOnesAreKept() {
        TradeSession active = open();
        offer(active, alice, aliceProfile, item("sword", 1));
        TradeSession orphan = open();
        offer(orphan, bob, bobProfile, item("apple", 2));
        clock.addAndGet(300_000);
        var cancelled = engine.cancelOrphans(java.util.Set.of(active.id), 120_000).join();
        assertEquals(1, cancelled.size());
        assertEquals(TradeStore.OPEN, status(active.id));
        assertEquals(TradeStore.CANCELLED, status(orphan.id));
        // Young trades are never touched (their start may still be on its way).
        TradeSession young = open();
        assertTrue(engine.cancelOrphans(java.util.Set.of(active.id), 120_000).join().isEmpty());
        assertEquals(TradeStore.OPEN, status(young.id));
    }

    @Test
    void deliveryClaimsEachRowExactlyOnce() {
        TradeSession s = open();
        offer(s, alice, aliceProfile, item("sword", 1));
        engine.cancel(s.id, "cancelled", Actor.SYSTEM, alice, "Alice", bob, "Bob").join();
        List<TradeStore.EscrowRow> pending = store.pending(aliceProfile).join();
        assertEquals(1, pending.size());
        assertTrue(store.pending(bobProfile).join().isEmpty());
        assertTrue(engine.claim(pending.getFirst(), Actor.SYSTEM).join());
        assertFalse(engine.claim(pending.getFirst(), Actor.SYSTEM).join());
        assertTrue(store.pending(aliceProfile).join().isEmpty());
        assertEquals(TradeStore.DELIVERED, rows(s).getFirst().state());
    }

    @Test
    void tokenTellsWhetherAnOfferWasStored() {
        TradeSession s = open();
        long id = engine.escrow(s.id, alice, aliceProfile, item("sword", 1), "token-1").join().orElseThrow();
        assertEquals(id, store.byToken("token-1").join().orElseThrow().id());
        assertTrue(store.byToken("token-2").join().isEmpty());
    }

    @Test
    void putBackKeepsTheItemWaiting() {
        TradeSession s = open();
        long id = store.putBack(s.id, alice, aliceProfile, item("left", 7), clock.get()).join();
        TradeStore.EscrowRow row = store.pending(aliceProfile).join().getFirst();
        assertEquals(id, row.id());
        assertEquals(7, row.offer().amount());
    }

    @Test
    void uniqueItemBytesSurviveUnchanged() {
        TradeSession s = open();
        byte[] bytes = new byte[300];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) (i * 7);
        }
        Offer tool = new Offer(0, bytes, "living_pickaxe", UUID.randomUUID().toString(), "DIAMOND_PICKAXE", 1, "My Pick");
        Offer stored = offer(s, alice, aliceProfile, tool);
        bothConfirmAndCountDown(s);
        engine.complete(s.snapshot(), Actor.SYSTEM).join();
        TradeStore.EscrowRow row = store.pending(bobProfile).join().getFirst();
        assertEquals(stored.rowId(), row.id());
        assertTrue(java.util.Arrays.equals(bytes, row.offer().bytes()));
        assertEquals(tool.itemUuid(), row.offer().itemUuid());
    }

    @Test
    void legsUseProfileAccountsAndOneKeyPerSideAndCurrency() {
        TradeSession s = open();
        s.setMoney(alice, Currency.MONEY, 5);
        s.setMoney(bob, Currency.MONEY, 6);
        s.setMoney(bob, Currency.SHARDS, 7);
        var legs = TradeEngine.legs(s.snapshot());
        assertEquals(3, legs.size());
        assertEquals(aliceProfile, legs.get(0).from().owner());
        assertEquals(bobProfile, legs.get(0).to().owner());
        assertEquals("trade:" + s.id + ":a:money", legs.get(0).idempotencyKey());
        assertEquals("trade:" + s.id + ":b:money", legs.get(1).idempotencyKey());
        assertEquals("trade:" + s.id + ":b:shards", legs.get(2).idempotencyKey());
        assertTrue(legs.stream().allMatch(t -> t.reason().equals("trade")));
    }

    @Test
    void escrowRowsLiveInTheDatabaseOnly() {
        TradeSession s = open();
        offer(s, alice, aliceProfile, item("sword", 1));
        long count = db.blocking(c -> Sql.queryOne(c, "SELECT COUNT(*) FROM trade_escrow WHERE trade_id = ? AND state = ?",
                rs -> rs.getLong(1), s.id, TradeStore.HELD).orElse(0L));
        assertEquals(1L, count);
    }
}
