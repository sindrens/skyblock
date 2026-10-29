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
import net.skybornmc.skyblock.common.economy.TxReason;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Money notes against a real (H2, MySQL mode) database: withdraw, redeem, copies, races. */
class NoteServiceTest {

    @TempDir
    Path dir;

    private Database db;
    private EconomyServiceImpl economy;
    private NoteService notes;
    private final AtomicLong clock = new AtomicLong(5_000_000L);
    private final EconomyConfig config = new EconomyConfig();
    private final UUID steve = UUID.randomUUID();
    private final UUID alex = UUID.randomUUID();
    private final UUID duper = UUID.randomUUID();

    @BeforeEach
    void start() throws Exception {
        DatabaseConfig dbc = new DatabaseConfig();
        dbc.type = DatabaseConfig.Type.H2;
        dbc.h2File = "notes-test";
        dbc.poolSize = 8;
        dbc.slowQueryMs = 60_000;
        db = new Database(dbc, dir.toFile(), Logger.getLogger("notes-test"));
        db.start();
        new Migrations(db, Logger.getLogger("notes-test")).migrate("core", getClass().getClassLoader());
        new Migrations(db, Logger.getLogger("notes-test")).migrate("economy", getClass().getClassLoader());
        AuditLog audit = new AuditLog(db, new AuditConfig(), dir.toFile(), Logger.getLogger("notes-test"));
        EconomyStore store = new EconomyStore(db, () -> 0, clock::get);
        economy = new EconomyServiceImpl(store, new BalanceCache(), audit, () -> config, uuid -> null, c -> "$", () -> 0,
                Logger.getLogger("notes-test"));
        notes = new NoteService(db, economy, clock::get, Logger.getLogger("notes-test"));
        economy.loadPlayer(steve, "Steve").join(); // $1,000 start balance
        economy.loadPlayer(alex, "Alex").join();
        economy.loadPlayer(duper, "Duper").join();
    }

    @AfterEach
    void stop() {
        db.close();
    }

    private long money(UUID player) {
        return economy.balance(AccountRef.player(player), Currency.MONEY).join();
    }

    private long ledgerRows(String reason) {
        return db.blocking(c -> Sql.queryOne(c, "SELECT COUNT(*) FROM eco_ledger WHERE reason = ?", rs -> rs.getLong(1), reason)
                .orElse(0L));
    }

    private long noteRows() {
        return db.blocking(c -> Sql.queryOne(c, "SELECT COUNT(*) FROM money_notes", rs -> rs.getLong(1)).orElse(0L));
    }

    @Test
    void withdrawTakesTheMoneyAndRecordsTheNote() {
        UUID id = UUID.randomUUID();
        NoteService.Result result = notes.withdraw(id, steve, "Steve", Currency.MONEY, 400).join();
        assertEquals(NoteService.Outcome.SUCCESS, result.outcome());
        assertEquals(600L, result.balance());
        assertEquals(600L, money(steve));
        assertEquals(600L, economy.cached(steve, Currency.MONEY).getAsLong());

        MoneyNote note = notes.find(id).join().orElseThrow();
        assertEquals(400L, note.amount());
        assertEquals(Currency.MONEY, note.currency());
        assertEquals(MoneyNote.Status.ACTIVE, note.status());
        assertEquals(MoneyNote.Kind.WITHDRAW, note.kind());
        assertEquals(steve, note.createdBy());
        assertEquals("Steve", note.createdName());
        assertEquals(1L, ledgerRows(TxReason.NOTE_WITHDRAW));
    }

    @Test
    void withdrawWithInsufficientFundsChangesNothing() {
        UUID id = UUID.randomUUID();
        NoteService.Result result = notes.withdraw(id, steve, "Steve", Currency.MONEY, 1_001).join();
        assertEquals(NoteService.Outcome.INSUFFICIENT_FUNDS, result.outcome());
        assertEquals(1_000L, money(steve));
        assertTrue(notes.find(id).join().isEmpty(), "the note row is rolled back with the failed payment");
        assertEquals(0L, noteRows());
        assertEquals(0L, ledgerRows(TxReason.NOTE_WITHDRAW));
    }

    @Test
    void theSameNoteIdCanNotBeWithdrawnTwice() {
        UUID id = UUID.randomUUID();
        assertTrue(notes.withdraw(id, steve, "Steve", Currency.MONEY, 100).join().ok());
        NoteService.Result again = notes.withdraw(id, steve, "Steve", Currency.MONEY, 100).join();
        assertFalse(again.ok());
        assertEquals(900L, money(steve), "money is only taken once per note id");
    }

    @Test
    void redeemPaysOnceAndMarksTheNoteUsed() {
        UUID id = UUID.randomUUID();
        notes.withdraw(id, steve, "Steve", Currency.MONEY, 300).join();
        NoteService.Result result = notes.redeem(id, alex, "Alex").join();
        assertEquals(NoteService.Outcome.SUCCESS, result.outcome());
        assertEquals(1_300L, result.balance());
        assertEquals(1_300L, money(alex));
        MoneyNote note = notes.find(id).join().orElseThrow();
        assertEquals(MoneyNote.Status.REDEEMED, note.status());
        assertEquals(alex, note.redeemedBy());
        assertEquals("Alex", note.redeemedName());
        assertEquals(clock.get(), note.redeemedAt());
        assertEquals(1L, ledgerRows(TxReason.NOTE_REDEEM));
    }

    @Test
    void redeemingACopyAfterTheOriginalIsRefused() {
        UUID id = UUID.randomUUID();
        notes.withdraw(id, steve, "Steve", Currency.MONEY, 500).join();
        assertTrue(notes.redeem(id, steve, "Steve").join().ok());

        // A duplicated item carries the same id.
        NoteService.Result copy = notes.redeem(id, duper, "Duper").join();
        assertEquals(NoteService.Outcome.ALREADY_REDEEMED, copy.outcome());
        assertEquals("Steve", copy.note().redeemedName(), "the alert can say who cashed it");
        assertEquals(1_000L, money(duper));
        assertEquals(1_000L, money(steve));
        assertEquals(1L, ledgerRows(TxReason.NOTE_REDEEM));

        // The original holder can't cash it again either.
        assertEquals(NoteService.Outcome.ALREADY_REDEEMED, notes.redeem(id, steve, "Steve").join().outcome());
        assertEquals(1_000L, money(steve));
    }

    @Test
    void doubleRedeemRaceCashesExactlyOnce() throws Exception {
        UUID id = UUID.randomUUID();
        notes.withdraw(id, steve, "Steve", Currency.MONEY, 1_000).join();
        int copies = 12;
        List<UUID> holders = new ArrayList<>();
        for (int i = 0; i < copies; i++) {
            UUID holder = UUID.randomUUID();
            economy.loadPlayer(holder, "Holder" + i).join();
            holders.add(holder);
        }
        ExecutorService pool = Executors.newFixedThreadPool(copies);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<NoteService.Result>> results = new ArrayList<>();
        try {
            for (int i = 0; i < copies; i++) {
                UUID holder = holders.get(i);
                String name = "Holder" + i;
                results.add(pool.submit(() -> {
                    go.await();
                    return notes.redeem(id, holder, name).get(30, TimeUnit.SECONDS);
                }));
            }
            go.countDown();
            int success = 0;
            int refused = 0;
            for (Future<NoteService.Result> f : results) {
                NoteService.Result r = f.get(60, TimeUnit.SECONDS);
                if (r.ok()) {
                    success++;
                } else {
                    assertEquals(NoteService.Outcome.ALREADY_REDEEMED, r.outcome());
                    refused++;
                }
            }
            assertEquals(1, success);
            assertEquals(copies - 1, refused);
        } finally {
            pool.shutdownNow();
        }
        long total = 0;
        for (UUID holder : holders) {
            total += money(holder);
        }
        assertEquals(copies * 1_000L + 1_000L, total, "exactly one holder got the note's money");
        assertEquals(1L, ledgerRows(TxReason.NOTE_REDEEM));
        assertEquals(MoneyNote.Status.REDEEMED, notes.find(id).join().orElseThrow().status());
    }

    @Test
    void unknownAndVoidNotesPayNothing() {
        assertEquals(NoteService.Outcome.UNKNOWN, notes.redeem(UUID.randomUUID(), steve, "Steve").join().outcome());

        UUID id = UUID.randomUUID();
        assertTrue(notes.createAdmin(id, Currency.MONEY, 5_000, Actor.CONSOLE, "Event prize").join().ok());
        assertTrue(notes.voidNote(id).join());
        assertFalse(notes.voidNote(id).join(), "only active notes can be voided");
        assertEquals(NoteService.Outcome.VOID, notes.redeem(id, steve, "Steve").join().outcome());
        assertEquals(1_000L, money(steve));
    }

    @Test
    void adminNotesTakeNoMoneyAndPayOnRedeem() {
        UUID id = UUID.randomUUID();
        NoteService.Result made = notes.createAdmin(id, Currency.MONEY, 5_000, Actor.player(alex, "Alex"), "Event prize").join();
        assertTrue(made.ok());
        assertEquals(1_000L, money(alex), "staff money is not touched");
        MoneyNote note = notes.find(id).join().orElseThrow();
        assertEquals(MoneyNote.Kind.ADMIN, note.kind());
        assertEquals("Event prize", note.note());
        assertEquals(0L, ledgerRows(TxReason.NOTE_WITHDRAW));

        assertTrue(notes.redeem(id, steve, "Steve").join().ok());
        assertEquals(6_000L, money(steve));
        assertFalse(notes.voidNote(id).join(), "a cashed note can't be voided");
    }

    @Test
    void anOverflowingRedeemLeavesTheNoteActive() {
        UUID id = UUID.randomUUID();
        notes.createAdmin(id, Currency.MONEY, 10, Actor.CONSOLE, "Testing").join();
        economy.set(AccountRef.player(steve), Currency.MONEY, Long.MAX_VALUE - 5, TxReason.ADMIN_SET, Actor.CONSOLE,
                java.util.Map.of()).join();
        assertEquals(NoteService.Outcome.OVERFLOW, notes.redeem(id, steve, "Steve").join().outcome());
        assertEquals(MoneyNote.Status.ACTIVE, notes.find(id).join().orElseThrow().status(), "rolled back with the deposit");
        assertTrue(notes.redeem(id, alex, "Alex").join().ok(), "someone else can still cash it");
    }
}
