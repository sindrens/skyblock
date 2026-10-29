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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The service layer: start balance once per season, cache kept equal to database results, audit row per change. */
class EconomyServiceTest {

    @TempDir
    Path dir;

    private Database db;
    private AuditLog audit;
    private EconomyServiceImpl service;
    private final EconomyConfig config = new EconomyConfig();
    private final AtomicInteger season = new AtomicInteger(1);
    private final UUID steve = UUID.randomUUID();
    private final UUID alex = UUID.randomUUID();

    @BeforeEach
    void start() throws Exception {
        DatabaseConfig dbc = new DatabaseConfig();
        dbc.type = DatabaseConfig.Type.H2;
        dbc.h2File = "service-test";
        dbc.poolSize = 4;
        dbc.slowQueryMs = 60_000;
        db = new Database(dbc, dir.toFile(), Logger.getLogger("economy-test"));
        db.start();
        new Migrations(db, Logger.getLogger("economy-test")).migrate("core", getClass().getClassLoader());
        new Migrations(db, Logger.getLogger("economy-test")).migrate("economy", getClass().getClassLoader());
        audit = new AuditLog(db, new AuditConfig(), dir.toFile(), Logger.getLogger("economy-test"));
        EconomyStore store = new EconomyStore(db, season::get, System::currentTimeMillis);
        service = new EconomyServiceImpl(store, new BalanceCache(), audit, () -> config, uuid -> null, c -> "$", season::get,
                Logger.getLogger("economy-test"));
    }

    @AfterEach
    void stop() {
        db.close();
    }

    @Test
    void startBalanceIsGivenOncePerSeason() {
        service.loadPlayer(steve, "Steve").join();
        service.loadPlayer(steve, "Steve").join();
        assertEquals(1_000L, service.balance(AccountRef.player(steve), Currency.MONEY).join());
        assertEquals(1_000L, service.cached(steve, Currency.MONEY).getAsLong());
        assertEquals(0L, service.cached(steve, Currency.GEMS).getAsLong());

        season.set(2);
        service.loadPlayer(steve, "Steve").join();
        assertEquals(2_000L, service.balance(AccountRef.player(steve), Currency.MONEY).join());
    }

    @Test
    void transfersUpdateTheCacheAndTheAuditLog() {
        service.loadPlayer(steve, "Steve").join();
        service.loadPlayer(alex, "Alex").join();
        int before = audit.queued();
        TxResult result = service.transfer(steve, alex, Currency.MONEY, 400, TxReason.PAY, Actor.player(steve, "Steve")).join();
        assertTrue(result.ok());
        assertEquals(600L, service.cached(steve, Currency.MONEY).getAsLong());
        assertEquals(1_400L, service.cached(alex, Currency.MONEY).getAsLong());
        assertEquals(before + 1, audit.queued());

        TxResult broke = service.withdraw(steve, Currency.MONEY, 601, TxReason.SHOP_BUY, Actor.SYSTEM).join();
        assertEquals(TxResult.Status.INSUFFICIENT_FUNDS, broke.status());
        assertEquals(600L, service.cached(steve, Currency.MONEY).getAsLong());
        assertEquals(before + 1, audit.queued()); // failed changes are not money changes
    }

    @Test
    void setUpdatesCacheAndAudits() {
        service.loadPlayer(steve, "Steve").join();
        int before = audit.queued();
        TxResult result = service.set(AccountRef.player(steve), Currency.MONEY, 50, TxReason.ADMIN_SET, Actor.CONSOLE,
                Map.of("note", "test")).join();
        assertTrue(result.ok());
        assertEquals(50L, service.cached(steve, Currency.MONEY).getAsLong());
        assertEquals(before + 1, audit.queued());
    }

    @Test
    void invalidAndDuplicateTransactionsNeverThrow() {
        assertEquals(TxResult.Status.INVALID,
                service.execute(Transaction.deposit(AccountRef.player(steve), Currency.MONEY, -1, TxReason.PAY)).join().status());
        Transaction once = Transaction.deposit(AccountRef.player(steve), Currency.SHARDS, 5, TxReason.NOTE_REDEEM).idempotencyKey("note:1");
        assertTrue(service.execute(once).join().ok());
        assertEquals(TxResult.Status.DUPLICATE, service.execute(once).join().status());
        assertEquals("$1.2M", service.format(Currency.MONEY, 1_234_567));
    }
}
