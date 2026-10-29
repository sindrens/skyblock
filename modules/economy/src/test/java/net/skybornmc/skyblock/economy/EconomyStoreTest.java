package net.skybornmc.skyblock.economy;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.DatabaseConfig;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.db.Sql;
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
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The economy SQL against a real (H2, MySQL mode) database: atomicity, ledger, idempotency, concurrency. */
class EconomyStoreTest {

    @TempDir
    Path dir;

    private Database db;
    private EconomyStore store;
    private final AtomicLong clock = new AtomicLong(1_000_000L);
    private final AccountRef alice = AccountRef.player(UUID.fromString("00000000-0000-0000-0000-00000000000a"));
    private final AccountRef bob = AccountRef.player(UUID.fromString("ffffffff-0000-0000-0000-00000000000b"));

    @BeforeEach
    void start() throws Exception {
        DatabaseConfig config = new DatabaseConfig();
        config.type = DatabaseConfig.Type.H2;
        config.h2File = "economy-test";
        config.poolSize = 8;
        config.slowQueryMs = 60_000;
        db = new Database(config, dir.toFile(), Logger.getLogger("economy-test"));
        db.start();
        new Migrations(db, Logger.getLogger("economy-test")).migrate("core", getClass().getClassLoader());
        new Migrations(db, Logger.getLogger("economy-test")).migrate("economy", getClass().getClassLoader());
        db.blocking(c -> Sql.update(c, "INSERT INTO players (uuid, name, name_lower, first_seen, last_seen) VALUES (?, ?, ?, 0, 0)",
                alice.owner(), "Alice", "alice"));
        db.blocking(c -> Sql.update(c, "INSERT INTO players (uuid, name, name_lower, first_seen, last_seen) VALUES (?, ?, ?, 0, 0)",
                bob.owner(), "Bob", "bob"));
        store = new EconomyStore(db, () -> 3, clock::get);
    }

    @AfterEach
    void stop() {
        db.close();
    }

    private TxResult run(Transaction tx) {
        return store.execute(tx).join();
    }

    private long balance(AccountRef account, Currency currency) {
        return store.balance(account, currency).join();
    }

    private TxResult give(AccountRef to, long amount) {
        return run(Transaction.deposit(to, Currency.MONEY, amount, TxReason.ADMIN_GIVE).actor(Actor.CONSOLE));
    }

    private long ledgerRows() {
        return db.blocking(c -> Sql.queryOne(c, "SELECT COUNT(*) FROM eco_ledger", rs -> rs.getLong(1)).orElse(0L));
    }

    @Test
    void depositCreatesTheAccountAndALedgerRow() {
        TxResult result = give(alice, 1_000);
        assertTrue(result.ok());
        assertNull(result.fromBalance());
        assertEquals(1_000L, result.toBalance());
        assertEquals(1_000L, balance(alice, Currency.MONEY));
        assertEquals(0L, balance(alice, Currency.TOKENS));

        LedgerEntry row = db.blocking(c -> EconomyStore.entry(c, result.ledgerId())).orElseThrow();
        assertEquals(3, row.season());
        assertEquals(Currency.MONEY, row.currency());
        assertNull(row.from());
        assertEquals(alice, row.to());
        assertEquals(1_000L, row.amount());
        assertEquals(1_000L, row.toBalance());
        assertEquals("admin_give", row.reason());
        assertEquals("CONSOLE", row.actorName());
        assertEquals(1_000_000L, row.time());
    }

    @Test
    void transferMovesMoneyAndRecordsBothBalances() {
        give(alice, 5_000);
        TxResult result = run(Transaction.transfer(alice, bob, Currency.MONEY, 1_500, TxReason.PAY)
                .reference("test").actor(Actor.player(alice.owner(), "Alice")));
        assertTrue(result.ok());
        assertEquals(3_500L, result.fromBalance());
        assertEquals(1_500L, result.toBalance());
        assertEquals(3_500L, balance(alice, Currency.MONEY));
        assertEquals(1_500L, balance(bob, Currency.MONEY));

        LedgerEntry row = db.blocking(c -> EconomyStore.entry(c, result.ledgerId())).orElseThrow();
        assertEquals(alice, row.from());
        assertEquals(bob, row.to());
        assertEquals(3_500L, row.fromBalance());
        assertEquals(1_500L, row.toBalance());
        assertEquals("test", row.reference());
        assertEquals("Alice", row.fromName());
        assertEquals("Bob", row.toName());
        assertEquals(-1_500L, row.signedAmount(alice));
        assertEquals(1_500L, row.signedAmount(bob));
        assertEquals(3_500L, row.balanceAfter(alice));
        assertEquals("Bob", row.counterpartyName(alice));
    }

    @Test
    void insufficientFundsChangesNothing() {
        give(alice, 100);
        long rows = ledgerRows();
        TxResult result = run(Transaction.transfer(alice, bob, Currency.MONEY, 101, TxReason.PAY));
        assertEquals(TxResult.Status.INSUFFICIENT_FUNDS, result.status());
        assertEquals(100L, balance(alice, Currency.MONEY));
        assertEquals(0L, balance(bob, Currency.MONEY));
        assertEquals(rows, ledgerRows());

        assertEquals(TxResult.Status.INSUFFICIENT_FUNDS,
                run(Transaction.withdraw(bob, Currency.MONEY, 1, TxReason.SHOP_BUY)).status());
        assertTrue(run(Transaction.withdraw(alice, Currency.MONEY, 100, TxReason.SHOP_BUY)).ok());
        assertEquals(0L, balance(alice, Currency.MONEY));
    }

    @Test
    void overflowRollsBackTheDebit() {
        give(alice, 10);
        give(bob, Long.MAX_VALUE - 5);
        TxResult result = run(Transaction.transfer(alice, bob, Currency.MONEY, 10, TxReason.PAY));
        assertEquals(TxResult.Status.OVERFLOW, result.status());
        assertEquals(10L, balance(alice, Currency.MONEY));
        assertEquals(Long.MAX_VALUE - 5, balance(bob, Currency.MONEY));
    }

    @Test
    void invalidTransactionsAreRefusedBeforeTheDatabase() {
        assertEquals(TxResult.Status.INVALID, run(Transaction.transfer(alice, alice, Currency.MONEY, 1, TxReason.PAY)).status());
        assertEquals(TxResult.Status.INVALID, run(Transaction.deposit(alice, Currency.MONEY, 0, TxReason.PAY)).status());
        assertEquals(TxResult.Status.INVALID, run(Transaction.deposit(alice, Currency.MONEY, -5, TxReason.PAY)).status());
        assertEquals(TxResult.Status.INVALID, run(Transaction.deposit(alice, Currency.MONEY, 5, "Bad Reason")).status());
        assertEquals(0L, ledgerRows());
    }

    @Test
    void idempotencyKeyStopsReplays() {
        Transaction start = Transaction.deposit(alice, Currency.MONEY, 1_000, TxReason.START_BALANCE).idempotencyKey("start:3:money:a");
        assertTrue(run(start).ok());
        assertEquals(TxResult.Status.DUPLICATE, run(start).status());
        assertEquals(1_000L, balance(alice, Currency.MONEY));
        assertEquals(1L, ledgerRows());
    }

    @Test
    void concurrentReplaysOfOneKeySucceedOnce() throws Exception {
        give(alice, 1_000);
        List<CompletableFuture<TxResult>> futures = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            futures.add(store.execute(Transaction.transfer(alice, bob, Currency.MONEY, 100, TxReason.AH_BUY)
                    .idempotencyKey("ah_buy:42")));
        }
        long ok = futures.stream().map(CompletableFuture::join).filter(TxResult::ok).count();
        assertEquals(1, ok);
        assertEquals(900L, balance(alice, Currency.MONEY));
        assertEquals(100L, balance(bob, Currency.MONEY));
    }

    @Test
    void concurrentSpendingNeverGoesBelowZero() throws Exception {
        give(alice, 1_000);
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<TxResult>> results = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            results.add(pool.submit(() -> {
                go.await();
                return store.execute(Transaction.withdraw(alice, Currency.MONEY, 70, TxReason.SHOP_BUY)).join();
            }));
        }
        go.countDown();
        int ok = 0;
        for (Future<TxResult> f : results) {
            TxResult r = f.get(30, TimeUnit.SECONDS);
            if (r.ok()) {
                ok++;
            } else {
                assertEquals(TxResult.Status.INSUFFICIENT_FUNDS, r.status());
            }
        }
        pool.shutdown();
        assertEquals(14, ok); // 14 × 70 = 980, the 15th would need 1,050
        assertEquals(20L, balance(alice, Currency.MONEY));
        assertEquals(1L + 14, ledgerRows());
    }

    @Test
    void concurrentOppositeTransfersDoNotDeadlockAndConserveMoney() throws Exception {
        give(alice, 10_000);
        give(bob, 10_000);
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            boolean forward = i % 2 == 0;
            long amount = 1 + (i % 7) * 100;
            futures.add(pool.submit(() -> {
                go.await();
                TxResult r = store.execute(forward
                        ? Transaction.transfer(alice, bob, Currency.MONEY, amount, TxReason.PAY)
                        : Transaction.transfer(bob, alice, Currency.MONEY, amount, TxReason.PAY)).join();
                if (r.ok()) {
                    succeeded.incrementAndGet();
                }
                return null;
            }));
        }
        go.countDown();
        for (Future<?> f : futures) {
            f.get(60, TimeUnit.SECONDS);
        }
        pool.shutdown();
        long a = balance(alice, Currency.MONEY);
        long b = balance(bob, Currency.MONEY);
        assertEquals(20_000L, a + b);
        assertTrue(a >= 0 && b >= 0);
        assertEquals(2L + succeeded.get(), ledgerRows());

        // The ledger replays to the final balances: last row per side holds the balance after.
        long net = db.blocking(c -> Sql.queryOne(c, "SELECT COALESCE(SUM(CASE WHEN to_id = ? THEN amount ELSE -amount END), 0) "
                + "FROM eco_ledger WHERE from_id = ? OR to_id = ?", rs -> rs.getLong(1), alice.owner(), alice.owner(), alice.owner())
                .orElse(0L));
        assertEquals(a, net);
    }

    @Test
    void setWritesTheDifferenceToTheLedger() {
        give(alice, 500);
        EconomyStore.SetOutcome up = store.set(alice, Currency.MONEY, 2_000, TxReason.ADMIN_SET, null, Actor.CONSOLE).join();
        assertTrue(up.result().ok());
        assertEquals(500L, up.previous());
        LedgerEntry upRow = db.blocking(c -> EconomyStore.entry(c, up.result().ledgerId())).orElseThrow();
        assertEquals(1_500L, upRow.amount());
        assertEquals(alice, upRow.to());

        EconomyStore.SetOutcome down = store.set(alice, Currency.MONEY, 300, TxReason.ADMIN_RESET, null, Actor.CONSOLE).join();
        LedgerEntry downRow = db.blocking(c -> EconomyStore.entry(c, down.result().ledgerId())).orElseThrow();
        assertEquals(1_700L, downRow.amount());
        assertEquals(alice, downRow.from());
        assertEquals(300L, downRow.fromBalance());
        assertEquals(300L, balance(alice, Currency.MONEY));

        EconomyStore.SetOutcome same = store.set(alice, Currency.MONEY, 300, TxReason.ADMIN_SET, null, Actor.CONSOLE).join();
        assertEquals(0L, same.result().ledgerId());
        assertEquals(TxResult.Status.INVALID,
                store.set(alice, Currency.MONEY, -1, TxReason.ADMIN_SET, null, Actor.CONSOLE).join().result().status());
    }

    @Test
    void historyFiltersByCurrencyReasonGroupAndTime() {
        give(alice, 10_000);
        run(Transaction.transfer(alice, bob, Currency.MONEY, 100, TxReason.PAY));
        clock.addAndGet(Duration.ofDays(2).toMillis());
        run(Transaction.transfer(alice, bob, Currency.MONEY, 200, TxReason.AH_BUY));
        run(Transaction.transfer(bob, alice, Currency.MONEY, 50, TxReason.AH_SALE));
        run(Transaction.deposit(alice, Currency.TOKENS, 7, TxReason.SELL));

        EconomyStore.LedgerPage all = store.history(alice, LedgerFilter.ALL, 0, 50).join();
        assertEquals(5, all.total());
        assertEquals(TxReason.SELL, all.rows().getFirst().reason()); // newest first

        assertEquals(1, store.history(alice, LedgerFilter.ALL.withCurrency(Currency.TOKENS), 0, 50).join().total());
        assertEquals(2, store.history(alice, LedgerFilter.ALL.withReason("ah"), 0, 50).join().total());
        assertEquals(1, store.history(alice, LedgerFilter.ALL.withReason("pay"), 0, 50).join().total());
        assertEquals(1, store.history(alice, LedgerFilter.ALL.withReason("admin"), 0, 50).join().total());
        assertEquals(3, store.history(alice, LedgerFilter.ALL.withSince(Duration.ofDays(1)), 0, 50).join().total());
        assertEquals(3, store.history(bob, LedgerFilter.ALL, 0, 50).join().total());

        EconomyStore.LedgerPage page2 = store.history(alice, LedgerFilter.ALL, 2, 2).join();
        assertEquals(5, page2.total());
        assertEquals(2, page2.rows().size());
        assertEquals(TxReason.AH_BUY, page2.rows().getFirst().reason());
    }

    @Test
    void topListsRichestPlayersWithNames() {
        give(alice, 300);
        give(bob, 900);
        List<EconomyStore.TopEntry> top = store.top(Currency.MONEY, 10).join();
        assertEquals(2, top.size());
        assertEquals("Bob", top.get(0).name());
        assertEquals(900L, top.get(0).balance());
        assertEquals(1, store.top(Currency.MONEY, 1).join().size());
        assertTrue(store.top(Currency.GEMS, 10).join().isEmpty());
    }

    @Test
    void islandAccountsAreSeparateFromPlayers() {
        AccountRef island = AccountRef.island(alice.owner());
        give(alice, 1_000);
        assertTrue(run(Transaction.transfer(alice, island, Currency.MONEY, 400, TxReason.BANK_DEPOSIT)).ok());
        assertEquals(600L, balance(alice, Currency.MONEY));
        assertEquals(400L, balance(island, Currency.MONEY));
        assertEquals(400L, store.balances(island).join().get(Currency.MONEY));
        assertEquals(0L, store.balances(island).join().get(Currency.SHARDS));
    }
}
