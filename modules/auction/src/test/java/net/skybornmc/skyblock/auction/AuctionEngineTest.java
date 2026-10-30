package net.skybornmc.skyblock.auction;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditConfig;
import net.skybornmc.skyblock.common.audit.AuditLog;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.DatabaseConfig;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.db.Sql;
import net.skybornmc.skyblock.common.economy.TxReason;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The listing state machine against real SQL (H2, MySQL mode) and an economy with the real contract. */
class AuctionEngineTest {

    private static final long HOUR = 3_600_000L;
    private static final java.util.concurrent.ExecutorService POOL = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();

    @TempDir
    Path dir;

    private Database db;
    private AuctionEngine engine;
    private AuctionStore store;
    private final FakeEconomy economy = new FakeEconomy();
    private final AuctionConfig config = new AuctionConfig();
    private final AtomicLong clock = new AtomicLong(1_000_000_000L);
    private final UUID seller = UUID.randomUUID();
    private final UUID buyer = UUID.randomUUID();

    @BeforeEach
    void start() throws Exception {
        DatabaseConfig dbc = new DatabaseConfig();
        dbc.type = DatabaseConfig.Type.H2;
        dbc.h2File = "auction-test";
        dbc.poolSize = 8;
        dbc.slowQueryMs = 60_000;
        Logger log = Logger.getLogger("auction-test");
        db = new Database(dbc, dir.toFile(), log);
        db.start();
        new Migrations(db, log).migrate("core", getClass().getClassLoader());
        new Migrations(db, log).migrate("auction", getClass().getClassLoader());
        AuditLog audit = new AuditLog(db, new AuditConfig(), dir.toFile(), log);
        store = new AuctionStore(db);
        engine = new AuctionEngine(store, () -> economy, audit, () -> config, clock::get, () -> 0, log);
        config.listingCooldownSeconds = 0;
        economy.set(seller, 100_000);
        economy.set(buyer, 1_000_000);
    }

    @AfterEach
    void stop() {
        db.close();
    }

    private static ItemSnapshot item(String key) {
        return new ItemSnapshot(new byte[]{1, 2, 3, (byte) key.hashCode()}, key, null, "DIAMOND", 3, "Diamond", Category.MATERIALS);
    }

    private Listing list(long price) {
        AuctionEngine.CreateResult r = engine.create(seller, "Seller", item("diamond"), price, 24 * HOUR, 5, Actor.SYSTEM).join();
        assertEquals(AuctionEngine.CreateOutcome.LISTED, r.outcome(), String.valueOf(r));
        return r.listing();
    }

    private ListingStatus status(long id) {
        return store.get(id).join().orElseThrow().status();
    }

    private int collectionRows(UUID owner) {
        return store.collectionCount(owner).join();
    }

    // ------------------------------------------------------------------ happy path

    @Test
    void listingChargesTheFeeAndBuyingPaysTheSellerMinusTax() {
        Listing l = list(10_000);
        assertEquals(100_000 - 100, economy.get(seller));
        assertEquals(ListingStatus.ACTIVE, status(l.id()));
        assertEquals(1, engine.activeListings().size());
        assertArrayEquals(item("diamond").bytes(), store.get(l.id()).join().orElseThrow().item().bytes());

        AuctionEngine.BuyResult r = engine.buy(l.id(), buyer, "Buyer", Actor.SYSTEM).join();
        assertEquals(AuctionEngine.BuyOutcome.SOLD, r.outcome());
        assertEquals(1_000_000 - 10_000, economy.get(buyer));
        assertEquals(100_000 - 100 + 9_500, economy.get(seller));
        assertEquals(990_000L, r.buyerBalance());
        Listing sold = store.get(l.id()).join().orElseThrow();
        assertEquals(ListingStatus.SOLD, sold.status());
        assertEquals(500, sold.tax());
        assertTrue(sold.sellerNotice());
        assertTrue(engine.activeListings().isEmpty());

        List<CollectionEntry> bin = store.collection(buyer).join();
        assertEquals(1, bin.size());
        assertEquals(r.collectionId(), bin.getFirst().id());
        assertEquals("bought", bin.getFirst().source());

        assertEquals(AuctionEngine.BuyOutcome.GONE, engine.buy(l.id(), UUID.randomUUID(), "Late", Actor.SYSTEM).join().outcome());
        assertEquals(AuctionEngine.CancelOutcome.GONE, engine.cancel(l.id(), seller, false, Actor.SYSTEM, null).join().outcome());
        assertEquals(1, economy.count(TxReason.AH_BUY));
        assertEquals(1, economy.count(TxReason.AH_SALE));
    }

    @Test
    void sellerCannotBuyTheirOwnListing() {
        Listing l = list(500);
        assertEquals(AuctionEngine.BuyOutcome.OWN, engine.buy(l.id(), seller, "Seller", Actor.SYSTEM).join().outcome());
        assertEquals(ListingStatus.ACTIVE, status(l.id()));
    }

    @Test
    void anotherProfileOfTheSellerCantBuyAndNothingMoves() {
        // PROF/P4: the seller lists on profile 1, then switches to profile 2 (another key of the same player)
        UUID sellerProfile2 = UUID.randomUUID();
        economy.set(sellerProfile2, 1_000_000);
        java.util.List<String> audited = new java.util.ArrayList<>();
        engine.ownProfileCheck((s1, b, actor) -> {
            boolean own = s1.equals(seller) && b.equals(sellerProfile2);
            if (own) {
                audited.add("ah_buy");
            }
            return own;
        });
        Listing l = list(10_000);
        long sellerBefore = economy.get(seller);
        assertEquals(AuctionEngine.BuyOutcome.OWN_PROFILE, engine.buy(l.id(), sellerProfile2, "Seller", Actor.SYSTEM).join().outcome());
        assertEquals(java.util.List.of("ah_buy"), audited);
        assertEquals(ListingStatus.ACTIVE, status(l.id()), "the listing is untouched");
        assertEquals(1_000_000, economy.get(sellerProfile2), "no money left profile 2");
        assertEquals(sellerBefore, economy.get(seller), "no money reached profile 1");
        assertEquals(0, collectionRows(sellerProfile2), "no item reached profile 2");
        // still refused after the browse cache was reloaded from the database
        engine.refreshCache().join();
        assertEquals(AuctionEngine.BuyOutcome.OWN_PROFILE, engine.buy(l.id(), sellerProfile2, "Seller", Actor.SYSTEM).join().outcome());
        // another player still can
        assertEquals(AuctionEngine.BuyOutcome.SOLD, engine.buy(l.id(), buyer, "Buyer", Actor.SYSTEM).join().outcome());
    }

    @Test
    void sellerNoticesAreTakenOnce() {
        Listing l = list(500);
        engine.buy(l.id(), buyer, "Buyer", Actor.SYSTEM).join();
        assertEquals(1, store.takeNotices(seller).join().size());
        assertEquals(0, store.takeNotices(seller).join().size());
    }

    // ------------------------------------------------------------------ concurrency

    @Test
    void manyBuyersAtOnceOnlyOneGetsTheItem() throws Exception {
        Listing l = list(1_000);
        int buyers = 24;
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < buyers; i++) {
            UUID id = UUID.randomUUID();
            economy.set(id, 5_000);
            ids.add(id);
        }
        CountDownLatch go = new CountDownLatch(1);
        List<CompletableFuture<AuctionEngine.BuyResult>> results = new ArrayList<>();
        for (UUID id : ids) {
            results.add(CompletableFuture.supplyAsync(() -> {
                try {
                    go.await();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                return engine.buy(l.id(), id, "B", Actor.SYSTEM).join();
            }, POOL));
        }
        go.countDown();
        long sold = results.stream().map(CompletableFuture::join).filter(r -> r.outcome() == AuctionEngine.BuyOutcome.SOLD).count();
        assertEquals(1, sold);
        long charged = ids.stream().filter(id -> economy.get(id) != 5_000).count();
        assertEquals(1, charged);
        assertEquals(1, economy.count(TxReason.AH_BUY));
        assertEquals(ListingStatus.SOLD, status(l.id()));
        long items = ids.stream().mapToInt(this::collectionRows).sum();
        assertEquals(1, items);
    }

    @Test
    void buyAndCancelRacingNeverBothWin() throws Exception {
        config.relistMaxSameItem = 0;
        for (int round = 0; round < 10; round++) {
            Listing l = list(1_000);
            CountDownLatch go = new CountDownLatch(1);
            CompletableFuture<AuctionEngine.BuyResult> buy = CompletableFuture.supplyAsync(() -> {
                await(go);
                return engine.buy(l.id(), buyer, "Buyer", Actor.SYSTEM).join();
            }, POOL);
            CompletableFuture<AuctionEngine.CancelResult> cancel = CompletableFuture.supplyAsync(() -> {
                await(go);
                return engine.cancel(l.id(), seller, false, Actor.SYSTEM, null).join();
            }, POOL);
            go.countDown();
            boolean sold = buy.join().outcome() == AuctionEngine.BuyOutcome.SOLD;
            boolean cancelled = cancel.join().outcome() == AuctionEngine.CancelOutcome.CANCELLED;
            assertTrue(sold ^ cancelled, "round " + round + ": sold=" + sold + " cancelled=" + cancelled);
            assertEquals(sold ? ListingStatus.SOLD : ListingStatus.CANCELLED, status(l.id()));
            clock.addAndGet(60_000); // relist window
        }
        assertEquals(10, collectionRows(seller) + collectionRows(buyer));
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void collectionItemCanBeTakenOnlyOnce() throws Exception {
        Listing l = list(1_000);
        long entry = engine.cancel(l.id(), seller, false, Actor.SYSTEM, null).join().collectionId();
        CountDownLatch go = new CountDownLatch(1);
        List<CompletableFuture<Optional<CollectionEntry>>> takes = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            takes.add(CompletableFuture.supplyAsync(() -> {
                await(go);
                return engine.take(entry, seller, Actor.SYSTEM).join();
            }, POOL));
        }
        go.countDown();
        assertEquals(1, takes.stream().map(CompletableFuture::join).filter(Optional::isPresent).count());
        assertEquals(Optional.empty(), engine.take(entry, buyer, Actor.SYSTEM).join());
    }

    // ------------------------------------------------------------------ failures

    @Test
    void buyerWithoutMoneyLeavesTheListingActive() {
        Listing l = list(10_000);
        UUID poor = UUID.randomUUID();
        economy.set(poor, 9_999);
        assertEquals(AuctionEngine.BuyOutcome.INSUFFICIENT, engine.buy(l.id(), poor, "Poor", Actor.SYSTEM).join().outcome());
        assertEquals(ListingStatus.ACTIVE, status(l.id()));
        assertEquals(9_999, economy.get(poor));
        assertEquals(1, engine.activeListings().size());
        assertEquals(AuctionEngine.BuyOutcome.SOLD, engine.buy(l.id(), buyer, "Buyer", Actor.SYSTEM).join().outcome());
    }

    @Test
    void unpaidFeeSendsTheItemToTheCollectionBin() {
        economy.set(seller, 0);
        AuctionEngine.CreateResult r = engine.create(seller, "Seller", item("diamond"), 10_000, 24 * HOUR, 5, Actor.SYSTEM).join();
        assertEquals(AuctionEngine.CreateOutcome.FEE_UNPAID, r.outcome());
        assertEquals(ListingStatus.FAILED, status(r.listing().id()));
        List<CollectionEntry> bin = store.collection(seller).join();
        assertEquals(1, bin.size());
        assertEquals("failed", bin.getFirst().source());
        assertArrayEquals(item("diamond").bytes(), bin.getFirst().bytes());
        assertTrue(engine.activeListings().isEmpty());
    }

    @Test
    void rulesRefuseWithoutStoringAnything() {
        assertEquals(AuctionEngine.CreateOutcome.REJECTED,
                engine.create(seller, "Seller", item("x"), 5, HOUR, 5, Actor.SYSTEM).join().outcome());
        for (int i = 0; i < 2; i++) {
            assertEquals(AuctionEngine.CreateOutcome.LISTED,
                    engine.create(seller, "Seller", item("k" + i), 100, HOUR, 2, Actor.SYSTEM).join().outcome());
        }
        AuctionEngine.CreateResult full = engine.create(seller, "Seller", item("k9"), 100, HOUR, 2, Actor.SYSTEM).join();
        assertEquals(ListingRules.Problem.NO_SLOTS, full.rejection().problem());
        assertTrue(full.giveBack());

        config.listingCooldownSeconds = 10;
        AuctionEngine.CreateResult cooling = engine.create(seller, "Seller", item("k8"), 100, HOUR, 10, Actor.SYSTEM).join();
        assertEquals(ListingRules.Problem.COOLDOWN, cooling.rejection().problem());
        config.listingCooldownSeconds = 0;

        for (int i = 0; i < 3; i++) {
            engine.create(seller, "Seller", item("same"), 100, HOUR, 10, Actor.SYSTEM).join();
        }
        assertEquals(ListingRules.Problem.RELIST_LIMIT,
                engine.create(seller, "Seller", item("same"), 100, HOUR, 10, Actor.SYSTEM).join().rejection().problem());
        assertEquals(5, engine.activeListings().size());
    }

    // ------------------------------------------------------------------ expiry

    @Test
    void expiredListingsGoToTheSellersBinAndCannotBeBought() {
        Listing l = list(1_000);
        clock.addAndGet(25 * HOUR);
        assertEquals(AuctionEngine.BuyOutcome.GONE, engine.buy(l.id(), buyer, "Buyer", Actor.SYSTEM).join().outcome());
        List<Listing> expired = engine.expireDue().join();
        assertEquals(1, expired.size());
        assertEquals(ListingStatus.EXPIRED, status(l.id()));
        assertEquals(1, collectionRows(seller));
        assertEquals(0, engine.expireDue().join().size());
        assertEquals(AuctionEngine.CancelOutcome.GONE, engine.cancel(l.id(), seller, false, Actor.SYSTEM, null).join().outcome());
        assertEquals(1_000_000, economy.get(buyer));
    }

    @Test
    void staffCanRemoveAnyListingAndOthersCannotCancelIt() {
        Listing l = list(1_000);
        assertEquals(AuctionEngine.CancelOutcome.GONE, engine.cancel(l.id(), buyer, false, Actor.SYSTEM, null).join().outcome());
        AuctionEngine.CancelResult r = engine.cancel(l.id(), buyer, true, Actor.SYSTEM, "scam").join();
        assertEquals(AuctionEngine.CancelOutcome.CANCELLED, r.outcome());
        assertEquals("removed", store.collection(seller).join().getFirst().source());
    }

    @Test
    void feeIsRefundedOnCancelOnlyWhenConfigured() {
        Listing l = list(10_000);
        engine.cancel(l.id(), seller, false, Actor.SYSTEM, null).join();
        assertEquals(100_000 - 100, economy.get(seller));
        config.feeRefundOnCancel = true;
        clock.addAndGet(60_000);
        Listing second = list(10_000);
        engine.cancel(second.id(), seller, false, Actor.SYSTEM, null).join();
        assertEquals(100_000 - 100, economy.get(seller));
    }

    // ------------------------------------------------------------------ crash recovery

    @Test
    void paymentErrorLeavesTheListingBuyingUntilRecovery() {
        Listing l = list(10_000);
        economy.failAfterCommit(TxReason.AH_BUY, 1); // charged, but the answer was lost
        assertEquals(AuctionEngine.BuyOutcome.PROCESSING, engine.buy(l.id(), buyer, "Buyer", Actor.SYSTEM).join().outcome());
        assertEquals(ListingStatus.BUYING, status(l.id()));
        assertEquals(AuctionEngine.BuyOutcome.BUSY, engine.buy(l.id(), UUID.randomUUID(), "Other", Actor.SYSTEM).join().outcome());
        assertEquals(AuctionEngine.CancelOutcome.BUSY, engine.cancel(l.id(), seller, false, Actor.SYSTEM, null).join().outcome());

        assertEquals(0, engine.recover(60_000).join()); // too recent
        clock.addAndGet(120_000);
        assertEquals(1, engine.recover(60_000).join());
        assertEquals(ListingStatus.SOLD, status(l.id()));
        assertEquals(1_000_000 - 10_000, economy.get(buyer)); // charged exactly once
        assertEquals(100_000 - 100 + 9_500, economy.get(seller));
        assertEquals(1, collectionRows(buyer));
        assertEquals(0, engine.recover(0).join());
    }

    @Test
    void sellerPaymentErrorIsRetriedWithoutChargingTheBuyerAgain() {
        Listing l = list(10_000);
        economy.failNext(TxReason.AH_SALE, 1);
        assertEquals(AuctionEngine.BuyOutcome.PROCESSING, engine.buy(l.id(), buyer, "Buyer", Actor.SYSTEM).join().outcome());
        assertEquals(1_000_000 - 10_000, economy.get(buyer));
        engine.recover(0).join();
        assertEquals(ListingStatus.SOLD, status(l.id()));
        assertEquals(1_000_000 - 10_000, economy.get(buyer));
        assertEquals(100_000 - 100 + 9_500, economy.get(seller));
    }

    @Test
    void recoveryOfAnUnpaidBuyPutsTheListingBack() throws Exception {
        Listing l = list(10_000);
        UUID poor = UUID.randomUUID();
        economy.set(poor, 10);
        assertTrue(store.claimForBuy(l.id(), poor, "Poor", clock.get()).join()); // crash right after claiming
        engine.recover(0).join();
        assertEquals(ListingStatus.ACTIVE, status(l.id()));
        assertEquals(10, economy.get(poor));
        assertEquals(AuctionEngine.BuyOutcome.SOLD, engine.buy(l.id(), buyer, "Buyer", Actor.SYSTEM).join().outcome());
    }

    @Test
    void recoveryOfAPendingListingChargesTheFeeOnce() {
        economy.failNext(TxReason.AH_FEE, 1);
        AuctionEngine.CreateResult r = engine.create(seller, "Seller", item("diamond"), 10_000, 24 * HOUR, 5, Actor.SYSTEM).join();
        assertEquals(AuctionEngine.CreateOutcome.PENDING, r.outcome());
        assertEquals(ListingStatus.PENDING, status(r.listing().id()));
        assertTrue(engine.activeListings().isEmpty());
        engine.recover(0).join();
        assertEquals(ListingStatus.ACTIVE, status(r.listing().id()));
        assertEquals(100_000 - 100, economy.get(seller));
        engine.recover(0).join();
        assertEquals(1, economy.count(TxReason.AH_FEE));
        engine.refreshCache().join();
        assertEquals(1, engine.activeListings().size());
    }

    @Test
    void pendingListingWhoseFeeWasAlreadyTakenIsActivatedWithoutChargingAgain() {
        economy.failAfterCommit(TxReason.AH_FEE, 1);
        AuctionEngine.CreateResult r = engine.create(seller, "Seller", item("diamond"), 10_000, 24 * HOUR, 5, Actor.SYSTEM).join();
        assertEquals(AuctionEngine.CreateOutcome.PENDING, r.outcome());
        engine.recover(0).join();
        assertEquals(ListingStatus.ACTIVE, status(r.listing().id()));
        assertEquals(100_000 - 100, economy.get(seller));
    }

    @Test
    void storedRowsSurviveARoundTrip() throws Exception {
        Listing l = list(777);
        Listing back = db.blocking(c -> Sql.queryOne(c, "SELECT * FROM ah_listings WHERE id = ?", AuctionStore::listing, l.id()))
                .orElseThrow();
        assertEquals(l.item(), back.item());
        assertEquals(l.seller(), back.seller());
        assertEquals(l.price(), back.price());
        assertEquals(l.expiresAt(), back.expiresAt());
    }
}
