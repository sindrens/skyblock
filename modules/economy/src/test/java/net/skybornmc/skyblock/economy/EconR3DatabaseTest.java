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
import net.skybornmc.skyblock.common.economy.SendGate;
import net.skybornmc.skyblock.common.economy.Transaction;
import net.skybornmc.skyblock.common.economy.TxReason;
import net.skybornmc.skyblock.common.economy.TxResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ECON-R3 against a real (H2) database: crystals refused everywhere (R3-5b), the F1 send gate, note purge (R3-40). */
class EconR3DatabaseTest {

    @TempDir
    Path dir;

    private Database db;
    private EconomyServiceImpl economy;
    private NoteService notes;
    private final AtomicLong clock = new AtomicLong(5_000_000L);
    private final EconomyConfig config = new EconomyConfig();
    private final UUID steve = UUID.randomUUID();
    private final UUID alex = UUID.randomUUID();
    private final UUID mate = UUID.randomUUID();

    @BeforeEach
    void start() throws Exception {
        DatabaseConfig dbc = new DatabaseConfig();
        dbc.type = DatabaseConfig.Type.H2;
        dbc.h2File = "econ-r3";
        dbc.poolSize = 4;
        dbc.slowQueryMs = 60_000;
        db = new Database(dbc, dir.toFile(), Logger.getLogger("econ-r3"));
        db.start();
        new Migrations(db, Logger.getLogger("econ-r3")).migrate("core", getClass().getClassLoader());
        new Migrations(db, Logger.getLogger("econ-r3")).migrate("economy", getClass().getClassLoader());
        AuditLog audit = new AuditLog(db, new AuditConfig(), dir.toFile(), Logger.getLogger("econ-r3"));
        EconomyStore store = new EconomyStore(db, () -> 0, clock::get);
        economy = new EconomyServiceImpl(store, new BalanceCache(), audit, () -> config, uuid -> null, c -> "$", () -> 0,
                Logger.getLogger("econ-r3"));
        notes = new NoteService(db, economy, clock::get, Logger.getLogger("econ-r3"));
        economy.loadPlayer(steve, "Steve").join();
        economy.loadPlayer(alex, "Alex").join();
    }

    @AfterEach
    void stop() {
        db.close();
    }

    private long count(String sql, Object... params) {
        Long n = db.blocking(c -> Sql.queryOne(c, sql, rs -> rs.getLong(1), params).orElse(0L));
        return n;
    }

    private void give(UUID who, Currency currency, long amount, String reason) {
        assertTrue(economy.execute(Transaction.deposit(AccountRef.player(who), currency, amount, reason)).join().ok());
    }

    // ------------------------------------------------------------------ R3-5b

    @Test
    void crystalsAreRefusedForPayTradeAndBatches() {
        give(steve, Currency.CRYSTALS, 100, "island_task");
        TxResult pay = economy.execute(Transaction.transfer(AccountRef.player(steve), AccountRef.player(alex), Currency.CRYSTALS, 10,
                TxReason.PAY)).join();
        assertEquals(TxResult.Status.INVALID, pay.status());
        assertEquals(TxResult.Status.INVALID, economy.executeBatch(java.util.List.of(Transaction.transfer(AccountRef.player(steve),
                AccountRef.player(alex), Currency.CRYSTALS, 10, TxReason.TRADE)), null).join().status());
        assertEquals(100L, economy.balance(AccountRef.player(steve), Currency.CRYSTALS).join(), "nothing moved");
        assertEquals(0L, economy.balance(AccountRef.player(alex), Currency.CRYSTALS).join());
        assertFalse(economy.tradeable(Currency.CRYSTALS));
    }

    @Test
    void anOldCrystalNoteCanOnlyBeCashedByItsMaker() {
        give(steve, Currency.CRYSTALS, 100, "island_task");
        UUID id = UUID.randomUUID();
        assertTrue(notes.withdraw(id, steve, "Steve", Currency.CRYSTALS, 40).join().ok());
        assertEquals(NoteService.Outcome.NOT_TRANSFERABLE, notes.redeem(id, alex, "Alex").join().outcome());
        assertEquals(0L, economy.balance(AccountRef.player(alex), Currency.CRYSTALS).join());
        assertEquals(NoteService.Outcome.SUCCESS, notes.redeem(id, steve, "Steve").join().outcome(), "its maker gets them back");
        assertEquals(100L, economy.balance(AccountRef.player(steve), Currency.CRYSTALS).join());
    }

    // ------------------------------------------------------------------ F1

    private SendGateImpl gate(Set<UUID> islandOfSteve) {
        return new SendGateImpl(db, () -> config, (a, b) -> islandOfSteve.contains(a) && islandOfSteve.contains(b), clock::get);
    }

    @Test
    void onlyIncomeCountsAndIslandMembersAreAlwaysAllowed() {
        SendGateImpl gate = gate(Set.of(steve, mate));
        // money from players, staff and notes never counts
        give(steve, Currency.MONEY, 50_000, TxReason.ADMIN_GIVE);
        give(steve, Currency.MONEY, 50_000, TxReason.NOTE_REDEEM);
        give(steve, Currency.MONEY, 50_000, TxReason.AH_SALE);
        assertTrue(economy.execute(Transaction.transfer(AccountRef.player(alex), AccountRef.player(steve), Currency.MONEY, 500,
                TxReason.PAY)).join().ok());
        SendGate.Check before = gate.check(steve, alex, Currency.MONEY).join();
        assertFalse(before.allowed());
        assertEquals(0, before.earned());
        assertEquals(10_000, before.missing());
        assertTrue(gate.check(steve, mate, Currency.MONEY).join().allowed(), "island member: always");
        assertFalse(gate.check(steve, null, Currency.SHARDS).join().allowed(), "notes to anyone: no island exception");
        assertTrue(gate.check(steve, alex, Currency.CREDITS_EARNED).join().allowed(), "gems are not covered");

        give(steve, Currency.MONEY, 6_000, TxReason.SELL);
        assertTrue(economy.execute(Transaction.deposit(AccountRef.bank(steve), Currency.MONEY, 1_000, TxReason.INTEREST)).join().ok());
        SendGate.Check partly = gate.check(steve, alex, Currency.MONEY).join();
        assertEquals(7_000, partly.earned(), "sales and bank interest count");
        assertEquals(3_000, partly.missing());

        give(steve, Currency.MONEY, 3_000, TxReason.AFK_POOL);
        assertTrue(gate.check(steve, alex, Currency.MONEY).join().allowed());
        assertEquals(1L, count("SELECT COUNT(*) FROM eco_send_unlocked"), "remembered once unlocked");
        assertTrue(gate(Set.of()).check(steve, alex, Currency.MONEY).join().allowed(), "a fresh gate (restart) reads the row");
    }

    // ------------------------------------------------------------------ R3-40

    @Test
    void cashedNotesAreDeletedAfterTheirAgeInBatches() {
        for (int i = 0; i < 5; i++) {
            UUID id = UUID.randomUUID();
            notes.withdraw(id, steve, "Steve", Currency.MONEY, 10).join();
            notes.redeem(id, alex, "Alex").join();
        }
        UUID active = UUID.randomUUID();
        notes.withdraw(active, steve, "Steve", Currency.MONEY, 10).join();
        long cashedAt = clock.get();
        assertEquals(0, notes.purgeRedeemed(cashedAt, 2), "only notes cashed before the cut-off");
        assertEquals(5, notes.purgeRedeemed(cashedAt + 1, 2), "5 rows in batches of 2");
        assertTrue(notes.find(active).join().isPresent(), "an active note is never deleted");
        assertEquals(1L, count("SELECT COUNT(*) FROM money_notes"));
        assertEquals(5L, count("SELECT COUNT(*) FROM eco_ledger WHERE reason = ?", TxReason.NOTE_REDEEM),
                "the ledger keeps the money history");
    }
}
