package net.skybornmc.skyblock.economy;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditConfig;
import net.skybornmc.skyblock.common.audit.AuditLog;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.DatabaseConfig;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.db.Sql;
import net.skybornmc.skyblock.common.economy.AccountRef;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.economy.store.CreditPackages;
import net.skybornmc.skyblock.economy.store.StoreGrants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CREDIT-SHOP 5.2/5.3 and section 11: Tebex grant and chargeback against H2 with the real economy service. */
class StoreGrantTest {

    @TempDir
    Path dir;

    private Database db;
    private EconomyServiceImpl economy;
    private StoreGrants grants;
    private final FakeProfiles profiles = new FakeProfiles();
    private final EconomyConfig config = new EconomyConfig();
    private final UUID steve = UUID.randomUUID();
    private final CreditPackages packs = CreditPackages.parse(new EconomyConfig().creditStorePackages, p -> { });

    @BeforeEach
    void start() throws Exception {
        DatabaseConfig dbc = new DatabaseConfig();
        dbc.type = DatabaseConfig.Type.H2;
        dbc.h2File = "store-grant-test";
        dbc.poolSize = 8;
        dbc.slowQueryMs = 60_000;
        db = new Database(dbc, dir.toFile(), Logger.getLogger("store-grant-test"));
        db.start();
        new Migrations(db, Logger.getLogger("t")).migrate("core", getClass().getClassLoader());
        new Migrations(db, Logger.getLogger("t")).migrate("economy", getClass().getClassLoader());
        AuditLog audit = new AuditLog(db, new AuditConfig(), dir.toFile(), Logger.getLogger("t"));
        EconomyStore store = new EconomyStore(db, () -> 0, System::currentTimeMillis);
        store.owners(profiles::ownerOf);
        economy = new EconomyServiceImpl(store, new BalanceCache(), audit, () -> config, uuid -> null, c -> "$", () -> 0,
                Logger.getLogger("t"), () -> profiles);
        profiles.add(steve, steve);
        db.blocking(c -> Sql.update(c, "INSERT INTO players (uuid, name, name_lower, first_seen, last_seen) VALUES (?, 'Steve', 'steve', 0, 0)", steve));
        grants = new StoreGrants(economy, db, System::currentTimeMillis);
    }

    @AfterEach
    void stop() {
        db.close();
    }

    private long bought() {
        return economy.balance(AccountRef.player(steve), Currency.CREDITS).join();
    }

    private long earned() {
        return economy.balance(AccountRef.player(steve), Currency.CREDITS_EARNED).join();
    }

    private long receipts(String tx) {
        return db.blocking(c -> Sql.queryOne(c, "SELECT COUNT(*) FROM store_purchases WHERE tx_id = ?", rs -> rs.getLong(1), tx)).orElse(0L);
    }

    private long ledgerRows(String reason) {
        return db.blocking(c -> Sql.queryOne(c, "SELECT COUNT(*) FROM eco_ledger WHERE reason = ?", rs -> rs.getLong(1), reason)).orElse(0L);
    }

    @Test
    void grantWritesLedgerAndReceiptOnce() {
        StoreGrants.Grant g = grants.grant(steve, packs.get("c2700").orElseThrow(), "tbx-1").join();
        assertEquals(StoreGrants.Outcome.GRANTED, g.outcome());
        assertEquals(2_700, bought());
        assertEquals(0, earned(), "a store grant is bought credits, never earned (the gems alias is never used)");
        assertEquals(1, receipts("tbx-1"));
        assertEquals(1, ledgerRows("store_grant"));
        assertEquals("granted", db.blocking(c -> Sql.queryOne(c, "SELECT state FROM store_purchases WHERE tx_id = 'tbx-1'",
                rs -> rs.getString(1))).orElseThrow());
    }

    @Test
    void replayHundredTimesWritesOnce() {
        CreditPackages.Pack pack = packs.get("c500").orElseThrow();
        List<CompletableFuture<StoreGrants.Grant>> all = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            all.add(grants.grant(steve, pack, "tbx-replay"));
        }
        long granted = all.stream().map(CompletableFuture::join).filter(g -> g.outcome() == StoreGrants.Outcome.GRANTED).count();
        assertEquals(1, granted);
        assertEquals(500, bought());
        assertEquals(1, receipts("tbx-replay"));
        assertEquals(1, ledgerRows("store_grant"));
        assertEquals(StoreGrants.Outcome.DUPLICATE, grants.grant(steve, pack, "tbx-replay").join().outcome());
    }

    @Test
    void twoPackagesInOneTransactionAreTwoReceipts() {
        grants.grant(steve, packs.get("c500").orElseThrow(), "tbx-2").join();
        grants.grant(steve, packs.get("c1000").orElseThrow(), "tbx-2").join();
        assertEquals(1_500, bought());
        assertEquals(2, receipts("tbx-2"));
    }

    @Test
    void badInputIsRefusedWithoutWriting() {
        assertEquals(StoreGrants.Outcome.FAILED, grants.grant(steve, packs.get("c500").orElseThrow(), "x'; DROP TABLE x").join().outcome());
        assertEquals(StoreGrants.Outcome.FAILED, grants.grant(null, packs.get("c500").orElseThrow(), "tbx-3").join().outcome());
        assertTrue(packs.get("c999").isEmpty(), "unknownPackageRefused: the command looks packages up here");
        assertEquals(0, bought());
    }

    @Test
    void uuidXuidAndNameResolve() {
        UUID id = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
        assertEquals(id, StoreGrants.parseId("069a79f4-44e9-4726-a5be-fca90e38aaf5").orElseThrow());
        assertEquals(id, StoreGrants.parseId("069a79f444e94726a5befca90e38aaf5").orElseThrow());
        assertEquals(new UUID(0, 2535428624195543L), StoreGrants.parseId("2535428624195543").orElseThrow(), "Floodgate UUID");
        assertTrue(StoreGrants.parseId("Steve").isEmpty(), "a name is resolved by the command");
        assertTrue(StoreGrants.parseId("12345").isEmpty(), "too short for an XUID: a name");
    }

    @Test
    void chargebackTakesAtMostTheBoughtBalance() {
        grants.grant(steve, packs.get("c2700").orElseThrow(), "tbx-cb").join();
        economy.withdraw(steve, Currency.CREDITS, 2_000, "test_spend", Actor.SYSTEM).join();
        StoreGrants.Chargeback cb = grants.chargeback(steve, "tbx-cb").join();
        assertFalse(cb.failed());
        assertEquals(1, cb.rows());
        assertEquals(700, cb.taken());
        assertEquals(2_000, cb.owed());
        assertEquals(0, bought());
        assertEquals("chargeback", db.blocking(c -> Sql.queryOne(c, "SELECT state FROM store_purchases WHERE tx_id = 'tbx-cb'",
                rs -> rs.getString(1))).orElseThrow());
    }

    @Test
    void chargebackTwiceIsANoop() {
        grants.grant(steve, packs.get("c500").orElseThrow(), "tbx-cb2").join();
        assertEquals(500, grants.chargeback(steve, "tbx-cb2").join().taken());
        StoreGrants.Chargeback again = grants.chargeback(steve, "tbx-cb2").join();
        assertEquals(0, again.rows());
        assertEquals(1, ledgerRows("store_chargeback"));
    }

    @Test
    void chargebackIgnoresEarnedCredits() {
        grants.grant(steve, packs.get("c500").orElseThrow(), "tbx-cb3").join();
        economy.withdraw(steve, Currency.CREDITS, 500, "test_spend", Actor.SYSTEM).join();
        economy.deposit(steve, Currency.CREDITS_EARNED, 300, "vote", Actor.SYSTEM).join();
        StoreGrants.Chargeback cb = grants.chargeback(steve, "tbx-cb3").join();
        assertEquals(0, cb.taken());
        assertEquals(500, cb.owed());
        assertEquals(300, earned(), "earned credits are never taken");
        assertEquals(1, cb.rows(), "the receipt is still closed");
    }

    @Test
    void chargebackOfAnotherPlayersTransactionFindsNothing() {
        grants.grant(steve, packs.get("c500").orElseThrow(), "tbx-cb4").join();
        assertEquals(0, grants.chargeback(UUID.randomUUID(), "tbx-cb4").join().rows());
        assertEquals(500, bought());
    }

    @Test
    void recentIdsAreNewestFirstAndBounded() {
        grants.remember(List.of("a", "b"));
        grants.remember("c");
        grants.remember("a");
        assertEquals(List.of("a", "c", "b"), grants.recent());
    }
}
