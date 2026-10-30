package net.skybornmc.skyblock.shop;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.DatabaseConfig;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.economy.Transaction;
import net.skybornmc.skyblock.common.economy.TxReason;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Payment + purchase row in one transaction against a real (H2, MySQL mode) database; hand-over exactly once. */
class ShopEngineTest {

    @TempDir
    Path dir;

    private Database db;
    private ShopStore store;
    private FakeEconomy economy;
    private ShopEngine engine;
    private final AtomicLong clock = new AtomicLong(1_000_000_000L);
    private final UUID player = UUID.randomUUID();
    private final UUID profile = UUID.randomUUID();
    private final ShopCatalog.Item logs = new ShopCatalog.Item("oak_log", "wood", 8, Map.of(), Map.of(), true);

    @BeforeEach
    void start() throws Exception {
        DatabaseConfig dbc = new DatabaseConfig();
        dbc.type = DatabaseConfig.Type.H2;
        dbc.h2File = "shop-test";
        dbc.poolSize = 4;
        dbc.slowQueryMs = 60_000;
        Logger log = Logger.getLogger("shop-test");
        db = new Database(dbc, dir.toFile(), log);
        db.start();
        new Migrations(db, log).migrate("core", getClass().getClassLoader());
        new Migrations(db, log).migrate("shop", getClass().getClassLoader());
        store = new ShopStore(db);
        economy = new FakeEconomy(db);
        engine = new ShopEngine(() -> economy, clock::get, () -> 3);
        economy.set(profile, Currency.MONEY, 1_000);
    }

    @AfterEach
    void stop() {
        db.close();
    }

    private ShopEngine.Result buy(ShopCatalog.Item item, int amount) {
        return engine.buy(new ShopEngine.Request(player, profile, "Steve", item, amount, Actor.player(player, "Steve"))).join();
    }

    @Test
    void purchasePaysFromTheProfileAndWritesTheRowTogether() {
        ShopEngine.Result r = buy(logs, 64);
        assertEquals(ShopEngine.Status.OK, r.status());
        assertEquals(1_000 - 512, economy.get(profile, Currency.MONEY));
        assertEquals(488L, r.balance());
        ShopStore.Purchase row = db.blocking(c -> ShopStore.get(c, r.purchase().id()));
        assertNotNull(row);
        assertEquals(ShopStore.DELIVERING, row.state());
        assertEquals(64, row.quantity());
        assertEquals(8, row.unitPrice());
        assertEquals(512, row.totalPrice());
        assertEquals(profile, row.profile());
        assertEquals(3, row.seasonId());
        Transaction tx = economy.done.getFirst();
        assertEquals(TxReason.SHOP_BUY, tx.reason());
        assertEquals("shop_buy:" + row.id(), tx.idempotencyKey());
        assertEquals(profile, tx.from().owner());
        assertNull(tx.to(), "money leaves the economy (a sink)");
        assertEquals("oak_log", tx.details().get("item"));
        assertEquals(64, tx.details().get("quantity"));
    }

    @Test
    void notEnoughMoneyChangesNothing() {
        ShopEngine.Result r = buy(logs, 200);
        assertEquals(ShopEngine.Status.NOT_ENOUGH, r.status());
        assertEquals(1_000, economy.get(profile, Currency.MONEY));
        assertEquals(0, (int) db.<Integer>blocking(ShopEngineTest::count));
    }

    @Test
    void overflowingTotalsAreRefused() {
        ShopCatalog.Item gold = new ShopCatalog.Item("dirt", "x", Long.MAX_VALUE / 2, Map.of(), Map.of(), true);
        assertEquals(ShopEngine.Status.INVALID, buy(gold, 3).status());
        assertEquals(ShopEngine.Status.INVALID, buy(logs, 0).status());
        assertEquals(1_000, economy.get(profile, Currency.MONEY));
    }

    @Test
    void aWaitingPurchaseIsHandedOverExactlyOnce() {
        ShopEngine.Result r = buy(logs, 10);
        UUID id = r.purchase().id();
        assertTrue(store.move(id, ShopStore.DELIVERING, ShopStore.PENDING, clock.get()).join());
        List<ShopStore.Purchase> waiting = store.pending(profile).join();
        assertEquals(1, waiting.size());
        assertTrue(store.move(id, ShopStore.PENDING, ShopStore.DELIVERING, clock.get()).join(), "first claim wins");
        assertFalse(store.move(id, ShopStore.PENDING, ShopStore.DELIVERING, clock.get()).join(), "second claim loses");
        assertTrue(store.move(id, ShopStore.DELIVERING, ShopStore.DELIVERED, clock.get()).join());
        assertFalse(store.move(id, ShopStore.DELIVERING, ShopStore.DELIVERED, clock.get()).join());
        assertTrue(store.pending(profile).join().isEmpty());
    }

    /**
     * SECFIX1 F-12: a purchase left 'delivering' by a crash may already be in the player's saved inventory, so recovery
     * never hands it over again: it goes to 'review' for staff. Waiting (pending) and delivered ones are untouched.
     */
    @Test
    void startupRecoveryNeverDeliversTwiceButMarksForReview() {
        UUID first = buy(logs, 1).purchase().id();
        UUID second = buy(logs, 2).purchase().id();
        UUID third = buy(logs, 3).purchase().id();
        store.move(second, ShopStore.DELIVERING, ShopStore.DELIVERED, clock.get()).join();
        store.move(third, ShopStore.DELIVERING, ShopStore.PENDING, clock.get()).join();
        List<ShopStore.Purchase> review = store.recover(clock.get()).join();
        assertEquals(List.of(first), review.stream().map(ShopStore.Purchase::id).toList());
        assertEquals(List.of(third), store.pending(profile).join().stream().map(ShopStore.Purchase::id).toList(),
                "only the purchase that surely was not handed over still waits");
        assertFalse(store.move(first, ShopStore.PENDING, ShopStore.DELIVERING, clock.get()).join(), "a review row can't be claimed");
        assertTrue(store.recover(clock.get()).join().isEmpty(), "a second start changes nothing");
    }

    private static int count(java.sql.Connection c) throws java.sql.SQLException {
        try (var rs = c.createStatement().executeQuery("SELECT COUNT(*) FROM shop_purchases")) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
