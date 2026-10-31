package net.skybornmc.skyblock.blackmarket;

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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The real SQL on H2 in MySQL mode: rotations stored once, stock, limits and charters counted atomically, hand-outs once. */
class BlackMarketStoreTest {

    @TempDir
    Path dir;
    private Database db;
    private final UUID account = UUID.randomUUID();
    private final UUID profile = UUID.randomUUID();
    private static final long ROTATION = 2_043_401L;

    @BeforeEach
    void start() throws Exception {
        DatabaseConfig config = new DatabaseConfig();
        config.type = DatabaseConfig.Type.H2;
        config.h2File = "blackmarket-test";
        config.poolSize = 4;
        db = new Database(config, dir.toFile(), Logger.getLogger("blackmarket-test"));
        db.start();
        new Migrations(db, Logger.getLogger("blackmarket-test")).migrate("blackmarket", getClass().getClassLoader());
    }

    @AfterEach
    void stop() {
        db.close();
    }

    private static final List<Rotation.Pick> PICKS = List.of(new Rotation.Pick(0, "regalia", 400, 2), new Rotation.Pick(1, "elytra", 30, -1));

    private void store() {
        db.blockingTransaction(c -> BlackMarketStore.ensureRotation(c, ROTATION, PICKS, 1000));
    }

    private BlackMarketStore.BuyRow row(String offer) {
        return new BlackMarketStore.BuyRow(UUID.randomUUID(), account, profile, ROTATION, offer, "item", "elytra", 1, "shards", 30, null,
                BlackMarketStore.PENDING, 5000);
    }

    private BlackMarketStore.Outcome buy(BlackMarketStore.BuyRow row, int slot, int limit) {
        return db.blockingTransaction(c -> {
            BlackMarketStore.Outcome o = BlackMarketStore.buy(c, row, slot,
                    List.of(new BlackMarketStore.Count(account.toString(), row.offerId(), "r:" + ROTATION, limit)), null, 5000);
            if (o != BlackMarketStore.Outcome.OK) {
                throw new Refused(o); // like a guard returning false: the whole transaction rolls back
            }
            return o;
        });
    }

    private BlackMarketStore.Outcome tryBuy(BlackMarketStore.BuyRow row, int slot, int limit) {
        try {
            return buy(row, slot, limit);
        } catch (RuntimeException e) {
            Throwable t = e;
            while (t != null && !(t instanceof Refused)) {
                t = t.getCause();
            }
            if (t == null) {
                throw e;
            }
            return ((Refused) t).outcome;
        }
    }

    private static final class Refused extends RuntimeException {
        final BlackMarketStore.Outcome outcome;

        Refused(BlackMarketStore.Outcome outcome) {
            super(outcome.name());
            this.outcome = outcome;
        }
    }

    private int buys() {
        return db.blocking(c -> BlackMarketStore.buys(c, BlackMarketStore.PENDING, 100).size());
    }

    @Test
    void aRotationIsStoredOnceAndNeverReRolled() {
        store();
        List<BlackMarketStore.RotationRow> again = db.blockingTransaction(c -> BlackMarketStore.ensureRotation(c, ROTATION,
                List.of(new Rotation.Pick(0, "voidforged", 250, 25)), 2000));
        assertEquals(List.of("regalia", "elytra"), again.stream().map(BlackMarketStore.RotationRow::offerId).toList());
        assertEquals(2, again.get(0).left());
        assertEquals(-1, again.get(1).left());
    }

    @Test
    void aStaffRerollReplacesTheOffers() {
        store();
        List<BlackMarketStore.RotationRow> rows = db.blockingTransaction(c -> BlackMarketStore.replaceRotation(c, ROTATION,
                List.of(new Rotation.Pick(0, "voidforged", 250, 25)), 2000));
        assertEquals(List.of("voidforged"), rows.stream().map(BlackMarketStore.RotationRow::offerId).toList());
    }

    @Test
    void theLimitPassesOnlyAsOftenAsAllowed() {
        store();
        assertEquals(BlackMarketStore.Outcome.OK, tryBuy(row("elytra"), 1, 2));
        assertEquals(BlackMarketStore.Outcome.OK, tryBuy(row("elytra"), 1, 2));
        assertEquals(BlackMarketStore.Outcome.LIMIT, tryBuy(row("elytra"), 1, 2));
        assertEquals(2, buys(), "the refused buy wrote nothing");
        assertEquals(2, db.blocking(c -> BlackMarketStore.counts(c, account.toString(), Set.of("r:" + ROTATION)))
                .get(BlackMarketStore.countKey("elytra", "r:" + ROTATION)));
    }

    @Test
    void noLimitCountsButNeverRefuses() {
        store();
        for (int i = 0; i < 5; i++) {
            assertEquals(BlackMarketStore.Outcome.OK, tryBuy(row("elytra"), 1, 0));
        }
        assertEquals(5, buys());
    }

    @Test
    void theSharedStockSellsOutAndARefusedBuyGivesItsStockBack() {
        store();
        assertEquals(BlackMarketStore.Outcome.OK, tryBuy(row("regalia"), 0, 0));
        // A buy refused later in the guard (limit) rolls back its stock step too.
        assertEquals(BlackMarketStore.Outcome.OK, tryBuy(row("regalia"), 0, 0));
        assertEquals(BlackMarketStore.Outcome.SOLD_OUT, tryBuy(row("regalia"), 0, 0));
        assertEquals(0, db.blocking(c -> BlackMarketStore.rotation(c, ROTATION)).get(0).left());
    }

    @Test
    void aRefusedLimitDoesNotUseStock() {
        store();
        assertEquals(BlackMarketStore.Outcome.OK, tryBuy(row("regalia"), 0, 1));
        assertEquals(BlackMarketStore.Outcome.LIMIT, tryBuy(row("regalia"), 0, 1));
        assertEquals(1, db.blocking(c -> BlackMarketStore.rotation(c, ROTATION)).get(0).left());
    }

    @Test
    void aStaleOfferIsRefused() {
        store();
        assertEquals(BlackMarketStore.Outcome.STALE, tryBuy(row("voidforged"), 0, 0));
        assertEquals(BlackMarketStore.Outcome.STALE, tryBuy(row("elytra"), 0, 0), "wrong slot");
        assertEquals(0, buys());
    }

    @Test
    void theFixedRowNeedsNoRotation() {
        assertEquals(BlackMarketStore.Outcome.OK, tryBuy(row("scroll"), -1, 10));
    }

    @Test
    void aCharterStepIsSoldOncePerIsland() {
        UUID island = UUID.randomUUID();
        BlackMarketStore.Count charter = new BlackMarketStore.Count("island:" + island, "charter_11", "s:0", 1);
        BlackMarketStore.Outcome first = db.blockingTransaction(c -> BlackMarketStore.buy(c, row("charter_11"), -1, List.of(), charter, 1));
        assertEquals(BlackMarketStore.Outcome.OK, first);
        BlackMarketStore.Outcome second = db.blockingTransaction(c -> BlackMarketStore.buy(c, row("charter_11"), -1, List.of(), charter, 2));
        assertEquals(BlackMarketStore.Outcome.CHARTER_TAKEN, second);
    }

    @Test
    void parallelBuysNeverPassTheLimit() throws Exception {
        store();
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<BlackMarketStore.Outcome>> results = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                results.add(pool.submit(() -> {
                    for (int attempt = 0; ; attempt++) {
                        try {
                            return tryBuy(row("elytra"), 1, 3);
                        } catch (RuntimeException e) {
                            if (attempt > 20) {
                                throw e; // lock timeouts on H2 are retried like the economy retries deadlocks
                            }
                            Thread.sleep(5);
                        }
                    }
                }));
            }
            int ok = 0;
            for (Future<BlackMarketStore.Outcome> f : results) {
                if (f.get() == BlackMarketStore.Outcome.OK) {
                    ok++;
                }
            }
            assertEquals(3, ok);
            assertEquals(3, buys());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void aHandOutIsTakenOnlyOnce() {
        BlackMarketStore.BuyRow r = row("elytra");
        db.blockingTransaction(c -> {
            BlackMarketStore.insertBuy(c, r);
            return true;
        });
        assertTrue(db.<Boolean>blocking(c -> BlackMarketStore.move(c, r.id(), BlackMarketStore.PENDING, BlackMarketStore.SENDING, 1)));
        assertFalse(db.<Boolean>blocking(c -> BlackMarketStore.move(c, r.id(), BlackMarketStore.PENDING, BlackMarketStore.SENDING, 2)));
        assertTrue(db.<Boolean>blocking(c -> BlackMarketStore.move(c, r.id(), BlackMarketStore.SENDING, BlackMarketStore.DELIVERED, 3)));
        assertEquals(1, db.blocking(c -> BlackMarketStore.history(c, account, 10)).size());
        assertEquals(BlackMarketStore.DELIVERED, db.blocking(c -> BlackMarketStore.history(c, account, 10)).get(0).state());
    }

    @Test
    void onlyTagsAndChartersAreRepeatedAfterACrash() {
        assertTrue(Deliveries.repeatable("tag"));
        assertTrue(Deliveries.repeatable(Purchases.CHARTER_KIND));
        assertFalse(Deliveries.repeatable("key"));
        assertFalse(Deliveries.repeatable("item"));
        assertFalse(Deliveries.repeatable("set-token"));
    }
}
