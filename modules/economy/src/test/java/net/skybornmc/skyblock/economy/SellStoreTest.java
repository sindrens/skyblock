package net.skybornmc.skyblock.economy;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditConfig;
import net.skybornmc.skyblock.common.audit.AuditLog;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.DatabaseConfig;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.economy.AccountRef;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.economy.Transaction;
import net.skybornmc.skyblock.common.economy.TxReason;
import net.skybornmc.skyblock.common.economy.TxResult;
import net.skybornmc.skyblock.common.sell.SellPrice;
import net.skybornmc.skyblock.common.sell.SellQuote;
import net.skybornmc.skyblock.common.sell.Trend;
import net.skybornmc.skyblock.economy.sell.PriceBook;
import net.skybornmc.skyblock.economy.sell.PriceState;
import net.skybornmc.skyblock.economy.sell.SellStore;
import net.skybornmc.skyblock.economy.sell.VolumeWindow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Selling against a real (H2) database: one sale = one guarded ledger transaction + one sale record, never paid twice;
 * price states and refunds survive a restart; a refund is handed out once.
 */
class SellStoreTest {

    @TempDir
    Path dir;

    private Database db;
    private EconomyServiceImpl economy;
    private SellStore store;
    private final EconomyConfig config = new EconomyConfig();
    private final UUID profile = UUID.randomUUID();

    @BeforeEach
    void start() throws Exception {
        DatabaseConfig dbc = new DatabaseConfig();
        dbc.type = DatabaseConfig.Type.H2;
        dbc.h2File = "sell-test";
        dbc.poolSize = 4;
        dbc.slowQueryMs = 60_000;
        Logger log = Logger.getLogger("sell-test");
        db = new Database(dbc, dir.toFile(), log);
        db.start();
        new Migrations(db, log).migrate("core", getClass().getClassLoader());
        new Migrations(db, log).migrate("economy", getClass().getClassLoader());
        AuditLog audit = new AuditLog(db, new AuditConfig(), dir.toFile(), log);
        economy = new EconomyServiceImpl(new EconomyStore(db, () -> 1, System::currentTimeMillis), new BalanceCache(), audit,
                () -> config, uuid -> null, c -> "$", () -> 1, log);
        store = new SellStore(db);
    }

    @AfterEach
    void stop() {
        db.close();
    }

    private static SellQuote quote(long total) {
        SellPrice coal = new SellPrice("coal", "ores", 10, 1.0, false, 1.0, Trend.STEADY);
        return new SellQuote(List.of(new SellQuote.Line(coal, total / 10, total)), total, 0, 0, total, List.of());
    }

    private TxResult sell(UUID saleId, SellQuote q) {
        Transaction tx = Transaction.deposit(AccountRef.player(profile), Currency.MONEY, q.total(), TxReason.SELL)
                .idempotencyKey("sell:" + saleId).reference("sell:" + saleId).actor(Actor.SYSTEM);
        SellStore.SaleRecord record = new SellStore.SaleRecord(saleId, 1L, profile, profile, "Steve", "all", q);
        return economy.executeGuarded(tx, c -> store.insertSale(c, record)).join();
    }

    @Test
    void aSaleIsPaidOnceWithItsRecord() {
        UUID sale = UUID.randomUUID();
        TxResult first = sell(sale, quote(640));
        assertTrue(first.ok());
        assertEquals(640L, economy.balance(AccountRef.player(profile), Currency.MONEY).join());
        assertEquals(640L, store.saleTotal(sale).join());
        // Replaying the same sale (same idempotency key) pays nothing and writes no second record.
        TxResult again = sell(sale, quote(640));
        assertEquals(TxResult.Status.DUPLICATE, again.status());
        assertEquals(640L, economy.balance(AccountRef.player(profile), Currency.MONEY).join());
        // A different sale is paid.
        assertTrue(sell(UUID.randomUUID(), quote(100)).ok());
        assertEquals(740L, economy.balance(AccountRef.player(profile), Currency.MONEY).join());
    }

    @Test
    void aFailedRecordRollsThePaymentBack() {
        UUID sale = UUID.randomUUID();
        assertTrue(sell(sale, quote(500)).ok());
        // Same sale id, new idempotency key: the record insert fails (primary key), so the money is not paid either.
        Transaction tx = Transaction.deposit(AccountRef.player(profile), Currency.MONEY, 500, TxReason.SELL)
                .idempotencyKey("sell-retry:" + sale).actor(Actor.SYSTEM);
        SellStore.SaleRecord record = new SellStore.SaleRecord(sale, 2L, profile, profile, "Steve", "all", quote(500));
        TxResult result = economy.executeGuarded(tx, c -> store.insertSale(c, record)).join();
        assertFalse(result.ok());
        assertEquals(500L, economy.balance(AccountRef.player(profile), Currency.MONEY).join());
    }

    @Test
    void priceStatesSurviveARestart() {
        long now = 1_800_000_000_000L;
        PriceState coal = new PriceState("coal", 0.73, 2e-6, now - 5_000, 0.8, VolumeWindow.EMPTY.add(now, 120), 120);
        PriceState wheat = PriceState.fresh("wheat", 1e-6);
        store.savePrices(List.of(coal, wheat), now).join();
        store.savePrices(List.of(coal.withMultiplier(0.75)), now).join(); // update path
        SellStore.Loaded loaded = store.loadPrices().join();
        assertEquals(2, loaded.states().size());
        PriceState back = loaded.states().stream().filter(s -> s.key().equals("coal")).findFirst().orElseThrow();
        assertEquals(0.75, back.multiplier(), 1e-12);
        assertEquals(2e-6, back.impact(), 1e-15);
        assertEquals(120, back.volume().total(now));
        assertEquals(now, loaded.updatedAt().get("coal"));

        PriceBook book = new PriceBook(() -> net.skybornmc.skyblock.economy.sell.DynamicSettings.DEFAULTS);
        book.load(loaded.states(), loaded.updatedAt(), now);
        assertEquals(0.75, book.get("coal").multiplier(), 1e-12);
    }

    @Test
    void aRefundIsHandedOutOnce() {
        UUID owner = UUID.randomUUID();
        byte[] item = {1, 2, 3};
        store.addRefund(owner, profile, item, "COAL", 64, 1L).join();
        List<SellStore.Refund> refunds = store.refunds(owner).join();
        assertEquals(1, refunds.size());
        assertArrayEquals(item, refunds.get(0).item());
        assertEquals(profile, refunds.get(0).profile());
        assertTrue(store.takeRefund(refunds.get(0).id()).join());
        assertFalse(store.takeRefund(refunds.get(0).id()).join());
        assertTrue(store.refunds(owner).join().isEmpty());
    }
}
