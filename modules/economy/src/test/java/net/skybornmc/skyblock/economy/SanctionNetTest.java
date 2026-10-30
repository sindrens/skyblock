package net.skybornmc.skyblock.economy;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.audit.AuditConfig;
import net.skybornmc.skyblock.common.audit.AuditLog;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.DatabaseConfig;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.economy.AccountRef;
import net.skybornmc.skyblock.common.economy.BatchResult;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.economy.Transaction;
import net.skybornmc.skyblock.common.economy.TxReason;
import net.skybornmc.skyblock.common.economy.TxResult;
import net.skybornmc.skyblock.common.moderation.SanctionService;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase P7 (spec 13.1): the economy refuses every transaction an active sanction forbids, before anything touches the
 * database (no balance change, no ledger row, guards never run), for single transactions and batches (trades). Without
 * the moderation module nothing changes.
 */
class SanctionNetTest {

    @TempDir
    Path dir;

    private Database db;
    private EconomyServiceImpl service;
    private final UUID frozen = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();
    /** Players whose money may not leave (a stand-in for the moderation module's rules). */
    private final Set<UUID> blocked = ConcurrentHashMap.newKeySet();

    private final SanctionService fake = new SanctionService() {
        @Override
        public Optional<Block> block(UUID player, Use use) {
            return blocked.contains(player) ? Optional.of(new Block(1, Kind.FREEZE, null, "test")) : Optional.empty();
        }

        @Override
        public boolean blocks(Transaction tx) {
            return tx.from() != null && blocked.contains(tx.from().owner()) && !tx.reason().startsWith("admin_");
        }

        @Override
        public void tell(Player player, Block block, Use use) {
        }

        @Override
        public void tellOther(Player viewer, String otherName, Use use) {
        }
    };

    @BeforeEach
    void start() throws Exception {
        DatabaseConfig dbc = new DatabaseConfig();
        dbc.type = DatabaseConfig.Type.H2;
        dbc.h2File = "sanction-net-test";
        dbc.poolSize = 4;
        dbc.slowQueryMs = 60_000;
        db = new Database(dbc, dir.toFile(), Logger.getLogger("economy-test"));
        db.start();
        new Migrations(db, Logger.getLogger("economy-test")).migrate("core", getClass().getClassLoader());
        new Migrations(db, Logger.getLogger("economy-test")).migrate("economy", getClass().getClassLoader());
        AuditLog audit = new AuditLog(db, new AuditConfig(), dir.toFile(), Logger.getLogger("economy-test"));
        EconomyConfig config = new EconomyConfig();
        service = new EconomyServiceImpl(new EconomyStore(db, () -> 1, System::currentTimeMillis), new BalanceCache(), audit,
                () -> config, uuid -> null, c -> "$", () -> 1, Logger.getLogger("economy-test"));
        service.sanctions(() -> fake);
        assertTrue(service.deposit(frozen, Currency.MONEY, 1000, TxReason.ADMIN_GIVE, Actor.CONSOLE).join().ok());
    }

    @AfterEach
    void stop() {
        db.close();
    }

    private long balance(UUID who) {
        return service.balance(AccountRef.player(who), Currency.MONEY).join();
    }

    @Test
    void aSanctionedTransferChangesNothing() {
        blocked.add(frozen);
        TxResult r = service.transfer(frozen, other, Currency.MONEY, 100, TxReason.PAY, Actor.CONSOLE).join();
        assertEquals(TxResult.Status.INVALID, r.status());
        assertEquals(1000, balance(frozen));
        assertEquals(0, balance(other));
    }

    @Test
    void theGuardOfARefusedTransactionNeverRuns() {
        blocked.add(frozen);
        AtomicBoolean ran = new AtomicBoolean();
        TxResult r = service.executeGuarded(Transaction.withdraw(AccountRef.player(frozen), Currency.MONEY, 10, TxReason.SHOP_BUY),
                c -> {
                    ran.set(true);
                    return true;
                }).join();
        assertEquals(TxResult.Status.INVALID, r.status());
        assertFalse(ran.get());
    }

    @Test
    void staffActionsAndEarningStillWork() {
        blocked.add(frozen);
        assertTrue(service.withdraw(frozen, Currency.MONEY, 100, TxReason.ADMIN_TAKE, Actor.CONSOLE).join().ok());
        assertTrue(service.deposit(frozen, Currency.MONEY, 50, TxReason.SELL, Actor.CONSOLE).join().ok());
        assertEquals(950, balance(frozen));
    }

    @Test
    void aBatchWithOneSanctionedSideIsRefusedAsAWhole() {
        assertTrue(service.deposit(other, Currency.MONEY, 500, TxReason.ADMIN_GIVE, Actor.CONSOLE).join().ok());
        blocked.add(frozen);
        BatchResult r = service.executeBatch(List.of(
                Transaction.transfer(AccountRef.player(other), AccountRef.player(frozen), Currency.MONEY, 100, TxReason.TRADE),
                Transaction.transfer(AccountRef.player(frozen), AccountRef.player(other), Currency.MONEY, 100, TxReason.TRADE)), null).join();
        assertFalse(r.ok());
        assertEquals(1000, balance(frozen));
        assertEquals(500, balance(other));
    }

    @Test
    void liftedOrNoModerationModuleMeansNormalTransactions() {
        assertTrue(service.transfer(frozen, other, Currency.MONEY, 100, TxReason.PAY, Actor.CONSOLE).join().ok());
        blocked.add(frozen);
        service.sanctions(() -> null);
        assertTrue(service.transfer(frozen, other, Currency.MONEY, 100, TxReason.PAY, Actor.CONSOLE).join().ok());
        assertEquals(200, balance(other));
    }

    // ------------------------------------------------------------------ SECFIX1 F-15: fail closed

    private final SanctionService broken = new SanctionService() {
        @Override
        public Optional<Block> block(UUID player, Use use) {
            throw new IllegalStateException("sanction cache broken");
        }

        @Override
        public boolean blocks(Transaction tx) {
            throw new IllegalStateException("sanction cache broken");
        }

        @Override
        public void tell(Player player, Block block, Use use) {
        }

        @Override
        public void tellOther(Player viewer, String otherName, Use use) {
        }
    };

    @Test
    void aFailingCheckRefusesMoneyLeavingAnAccount() {
        service.sanctions(() -> broken);
        TxResult pay = service.transfer(frozen, other, Currency.MONEY, 100, TxReason.PAY, Actor.CONSOLE).join();
        assertEquals(TxResult.Status.INVALID, pay.status());
        TxResult shop = service.withdraw(frozen, Currency.MONEY, 10, TxReason.SHOP_BUY, Actor.CONSOLE).join();
        assertEquals(TxResult.Status.INVALID, shop.status());
        BatchResult trade = service.executeBatch(List.of(
                Transaction.transfer(AccountRef.player(frozen), AccountRef.player(other), Currency.MONEY, 100, TxReason.TRADE)), null).join();
        assertFalse(trade.ok());
        assertEquals(1000, balance(frozen));
        assertEquals(0, balance(other));
    }

    @Test
    void aFailingCheckStillLetsPlayersEarnAndStaffAct() {
        service.sanctions(() -> broken);
        assertTrue(service.deposit(frozen, Currency.MONEY, 50, TxReason.SELL, Actor.CONSOLE).join().ok(), "selling still pays");
        assertTrue(service.withdraw(frozen, Currency.MONEY, 100, TxReason.ADMIN_TAKE, Actor.CONSOLE).join().ok(), "staff can fix things");
        assertEquals(950, balance(frozen));
    }

    @Test
    void aThrowingLookupFailsClosedToo() {
        service.sanctions(() -> {
            throw new IllegalStateException("registry gone");
        });
        assertEquals(TxResult.Status.INVALID, service.transfer(frozen, other, Currency.MONEY, 1, TxReason.PAY, Actor.CONSOLE).join().status());
    }

    @Test
    void theFailClosedRule() {
        assertTrue(EconomyServiceImpl.refuseWhenCheckFails(Transaction.transfer(AccountRef.player(frozen), AccountRef.player(other),
                Currency.MONEY, 1, TxReason.PAY)));
        assertTrue(EconomyServiceImpl.refuseWhenCheckFails(Transaction.withdraw(AccountRef.player(frozen), Currency.MONEY, 1, TxReason.SHOP_BUY)));
        assertFalse(EconomyServiceImpl.refuseWhenCheckFails(Transaction.deposit(AccountRef.player(frozen), Currency.MONEY, 1, TxReason.SELL)));
        assertFalse(EconomyServiceImpl.refuseWhenCheckFails(Transaction.withdraw(AccountRef.player(frozen), Currency.MONEY, 1, TxReason.ADMIN_TAKE)));
    }

    @Test
    void theEntryPointHooksFailClosedAndTellThePlayer() {
        java.util.List<String> told = new java.util.ArrayList<>();
        SanctionService brokenWithText = new SanctionService() {
            @Override
            public Optional<Block> block(UUID player, Use use) {
                throw new IllegalStateException("sanction cache broken");
            }

            @Override
            public boolean blocks(Transaction tx) {
                throw new IllegalStateException("sanction cache broken");
            }

            @Override
            public void tell(Player player, Block block, Use use) {
            }

            @Override
            public void tellOther(Player viewer, String otherName, Use use) {
            }

            @Override
            public void tellUnavailable(Player player, Use use) {
                told.add(player.getName() + ":" + use.id());
            }
        };
        net.skybornmc.skyblock.common.service.ServiceRegistry registry =
                new net.skybornmc.skyblock.common.service.ServiceRegistry(Logger.getLogger("economy-test"));
        registry.register("moderation", SanctionService.class, brokenWithText);
        Player alex = fakePlayer("Alex");
        assertTrue(SanctionService.denied(registry, alex, SanctionService.Use.PAY), "refused when the check throws");
        assertTrue(SanctionService.deniedOther(registry, alex, fakePlayer("Steve"), SanctionService.Use.TRADE));
        assertEquals(List.of("Alex:pay", "Alex:trade"), told);
    }

    /** A Player that only knows its name and uuid (enough for the sanction hooks). */
    private static Player fakePlayer(String name) {
        UUID id = UUID.randomUUID();
        return (Player) java.lang.reflect.Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> name;
                    case "getUniqueId" -> id;
                    case "hashCode" -> id.hashCode();
                    case "equals" -> proxy == args[0];
                    case "toString" -> name;
                    default -> method.getReturnType() == boolean.class ? false : null;
                });
    }
}
