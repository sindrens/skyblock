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
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PROF in the economy: money/crystals/shards per profile, gems per player, one start balance per player, P4 note
 * guard, P7 best-profile /baltop, profile ledger with the player's gems.
 */
class ProfileEconomyTest {

    @TempDir
    Path dir;

    private Database db;
    private EconomyStore store;
    private EconomyServiceImpl economy;
    private NoteService notes;
    private final FakeProfiles profiles = new FakeProfiles();
    private final EconomyConfig config = new EconomyConfig();
    private final UUID steve = UUID.randomUUID();
    private final UUID steve2 = UUID.randomUUID();
    private final UUID alex = UUID.randomUUID();

    @BeforeEach
    void start() throws Exception {
        DatabaseConfig dbc = new DatabaseConfig();
        dbc.type = DatabaseConfig.Type.H2;
        dbc.h2File = "profile-economy-test";
        dbc.poolSize = 4;
        dbc.slowQueryMs = 60_000;
        db = new Database(dbc, dir.toFile(), Logger.getLogger("profile-economy-test"));
        db.start();
        new Migrations(db, Logger.getLogger("t")).migrate("core", getClass().getClassLoader());
        new Migrations(db, Logger.getLogger("t")).migrate("economy", getClass().getClassLoader());
        AuditLog audit = new AuditLog(db, new AuditConfig(), dir.toFile(), Logger.getLogger("t"));
        store = new EconomyStore(db, () -> 0, System::currentTimeMillis);
        store.owners(profiles::ownerOf);
        economy = new EconomyServiceImpl(store, new BalanceCache(), audit, () -> config, uuid -> null, c -> "$", () -> 0,
                Logger.getLogger("t"), () -> profiles);
        notes = new NoteService(db, economy, System::currentTimeMillis, Logger.getLogger("t"),
                (from, to, actor) -> profiles.blockOwnTransfer(null, "note_redeem", from, to, actor));
        profiles.add(steve, steve);
        profiles.add(steve, steve2);
        profiles.add(alex, alex);
        db.blocking(c -> {
            Sql.update(c, "INSERT INTO players (uuid, name, name_lower, first_seen, last_seen) VALUES (?, 'Steve', 'steve', 0, 0)", steve);
            Sql.update(c, "INSERT INTO players (uuid, name, name_lower, first_seen, last_seen) VALUES (?, 'Alex', 'alex', 0, 0)", alex);
            return null;
        });
    }

    @AfterEach
    void stop() {
        db.close();
    }

    private long bal(UUID key, Currency currency) {
        return economy.balance(AccountRef.player(key), currency).join();
    }

    @Test
    void moneyIsPerProfileAndGemsArePerPlayer() {
        economy.loadPlayer(steve, "Steve").join();
        assertEquals(1_000, bal(steve, Currency.MONEY), "profile 1 has the start balance");
        economy.deposit(steve, Currency.GEMS, 50, "test", Actor.SYSTEM).join();

        profiles.switchTo(steve, steve2);
        economy.switchedProfile(steve, steve2).join();
        assertEquals(0, bal(steve2, Currency.MONEY), "a new profile has its own money (no second start balance)");
        assertEquals(0, economy.cached(steve, Currency.MONEY).getAsLong());
        // gems paid to the profile key land on the player and are the same on every profile
        economy.deposit(steve2, Currency.GEMS, 25, "test", Actor.SYSTEM).join();
        assertEquals(75, bal(steve, Currency.GEMS));
        assertEquals(75, bal(steve2, Currency.GEMS));
        assertEquals(75, economy.cached(steve, Currency.GEMS).getAsLong());
        assertEquals(75, economy.balances(AccountRef.player(steve2)).join().get(Currency.GEMS));
        // money earned on profile 2 stays there
        economy.deposit(steve2, Currency.MONEY, 300, "test", Actor.SYSTEM).join();
        economy.deposit(steve2, Currency.CRYSTALS, 7, "test", Actor.SYSTEM).join();
        assertEquals(300, economy.cached(steve, Currency.MONEY).getAsLong());
        assertEquals(1_000, bal(steve, Currency.MONEY));
        assertEquals(0, bal(steve, Currency.CRYSTALS));
        assertEquals(7, bal(steve2, Currency.CRYSTALS));
        long rowsOnKey2Gems = db.blocking(c -> Sql.queryOne(c, "SELECT COUNT(*) FROM eco_accounts WHERE owner_id = ? AND currency = 'gems'",
                rs -> rs.getLong(1), steve2)).orElse(0L);
        assertEquals(0, rowsOnKey2Gems, "no gem account ever exists on a profile key");

        economy.loadPlayer(steve, "Steve").join();
        assertEquals(300, bal(steve2, Currency.MONEY), "rejoining never gives a second start balance");
    }

    @Test
    void noteFromOneProfileCantBeCashedOnTheOther() {
        economy.loadPlayer(steve, "Steve").join();
        UUID noteId = UUID.randomUUID();
        assertEquals(NoteService.Outcome.SUCCESS, notes.withdraw(noteId, steve, steve, "Steve", Currency.MONEY, 400).join().outcome());
        NoteService.Result blocked = notes.redeem(noteId, steve2, steve, "Steve").join();
        assertEquals(NoteService.Outcome.OWN_PROFILE, blocked.outcome());
        assertEquals(List.of("note_redeem"), profiles.blocked);
        assertEquals(0, bal(steve2, Currency.MONEY));
        // the same profile, or another player, can cash it
        assertEquals(NoteService.Outcome.SUCCESS, notes.redeem(noteId, alex, alex, "Alex").join().outcome());
        assertEquals(400, bal(alex, Currency.MONEY));
    }

    @Test
    void ownNoteOnTheSameProfileIsFine() {
        economy.loadPlayer(steve, "Steve").join();
        UUID noteId = UUID.randomUUID();
        notes.withdraw(noteId, steve2, steve, "Steve", Currency.MONEY, 1).join();
        assertEquals(NoteService.Outcome.INSUFFICIENT_FUNDS.name(),
                notes.withdraw(UUID.randomUUID(), steve2, steve, "Steve", Currency.MONEY, 5_000).join().outcome().name());
    }

    @Test
    void baltopShowsEachPlayersBestProfile() {
        economy.deposit(steve, Currency.MONEY, 100, "test", Actor.SYSTEM).join();
        economy.deposit(steve2, Currency.MONEY, 9_000, "test", Actor.SYSTEM).join();
        economy.deposit(alex, Currency.MONEY, 5_000, "test", Actor.SYSTEM).join();
        List<EconomyStore.TopEntry> top = store.topBest(Currency.MONEY, 10, profiles).join();
        assertEquals(2, top.size());
        assertEquals(new EconomyStore.TopEntry(steve, "Steve", 9_000), top.get(0));
        assertEquals(new EconomyStore.TopEntry(alex, "Alex", 5_000), top.get(1));

        profiles.deleted.add(steve2);
        List<EconomyStore.TopEntry> after = store.topBest(Currency.MONEY, 10, profiles).join();
        assertEquals(new EconomyStore.TopEntry(alex, "Alex", 5_000), after.get(0));
        assertEquals(new EconomyStore.TopEntry(steve, "Steve", 100), after.get(1));
    }

    @Test
    void profileLedgerShowsItsOwnMoneyAndThePlayersGems() {
        economy.deposit(steve, Currency.MONEY, 100, "test", Actor.SYSTEM).join();
        economy.deposit(steve2, Currency.MONEY, 200, "test", Actor.SYSTEM).join();
        economy.deposit(steve2, Currency.GEMS, 5, "test", Actor.SYSTEM).join();
        economy.execute(Transaction.transfer(AccountRef.player(steve2), AccountRef.player(alex), Currency.MONEY, 50, TxReason.PAY)).join();
        EconomyStore.LedgerPage page = store.history(AccountRef.player(steve2), AccountRef.player(steve), LedgerFilter.ALL, 0, 50).join();
        assertEquals(3, page.total(), "profile 2: its deposit, its payment and the player's gems; not profile 1's money");
        assertTrue(page.rows().stream().anyMatch(e -> e.currency() == Currency.GEMS));
        // names of profile accounts are their player's name
        LedgerEntry pay = page.rows().stream().filter(e -> e.reason().equals(TxReason.PAY)).findFirst().orElseThrow();
        assertEquals("Steve", pay.fromName());
        assertEquals("Alex", pay.toName());
    }

    @Test
    void zeroingADeletedProfileKeepsGems() {
        economy.deposit(steve, Currency.GEMS, 40, "test", Actor.SYSTEM).join();
        economy.deposit(steve, Currency.MONEY, 70, "test", Actor.SYSTEM).join();
        // what the module does on ProfileDeletedEvent for profile 1 (key = player UUID): non-gem currencies only
        economy.set(AccountRef.player(steve), Currency.MONEY, 0, "profile_delete", Actor.system("profiles"), Map.of()).join();
        assertEquals(0, bal(steve, Currency.MONEY));
        assertEquals(40, bal(steve2, Currency.GEMS));
    }
}
