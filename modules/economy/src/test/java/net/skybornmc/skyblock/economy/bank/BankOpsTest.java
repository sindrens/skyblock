package net.skybornmc.skyblock.economy.bank;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.DatabaseConfig;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.db.Sql;
import net.skybornmc.skyblock.common.economy.AccountRef;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.economy.EconomyService;
import net.skybornmc.skyblock.economy.FakeProfiles;
import net.skybornmc.skyblock.economy.TestEconomy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Banks against a real (H2, MySQL mode) database with the real economy service: one ledger transaction per deposit and
 * withdrawal, capacity, fee and forfeit, the lowest-balance rule, interest idempotency per period, catch-up limit, daily
 * island limits, tier purchases, island payout and the P4 block.
 */
class BankOpsTest {

    private static final long P = 3 * 3_600_000L;
    private static final long HOUR = 3_600_000L;

    @TempDir
    Path dir;

    private Database db;
    private EconomyService economy;
    private BankOps ops;
    private final BankConfig config = new BankConfig();
    private final FakeProfiles profiles = new FakeProfiles();
    private final AtomicLong clock = new AtomicLong(5 * P + HOUR);
    private final AtomicInteger day = new AtomicInteger(20_000);
    private final UUID steve = UUID.randomUUID();
    private final UUID steve2 = UUID.randomUUID();
    private final UUID alex = UUID.randomUUID();
    private final Actor actor = Actor.SYSTEM;

    @BeforeEach
    void start() throws Exception {
        DatabaseConfig dbc = new DatabaseConfig();
        dbc.type = DatabaseConfig.Type.H2;
        dbc.h2File = "bank-test";
        dbc.poolSize = 8;
        dbc.slowQueryMs = 60_000;
        db = new Database(dbc, dir.toFile(), Logger.getLogger("bank-test"));
        db.start();
        new Migrations(db, Logger.getLogger("t")).migrate("core", getClass().getClassLoader());
        new Migrations(db, Logger.getLogger("t")).migrate("economy", getClass().getClassLoader());
        profiles.add(steve, steve);
        profiles.add(steve, steve2);
        profiles.add(alex, alex);
        economy = TestEconomy.create(db, dir.toFile(), clock::get, profiles);
        config.activityMinutes = 0;
        BankTiers tiers = BankTiers.parse(config);
        ops = new BankOps(economy, new BankStore(db, clock::get), () -> config, () -> tiers, () -> profiles, clock::get, day::get,
                Logger.getLogger("t"));
        give(steve, 1_000_000);
        give(steve2, 1_000_000);
        give(alex, 1_000_000);
    }

    @AfterEach
    void stop() {
        db.close();
    }

    private void give(UUID key, long amount) {
        assertTrue(economy.deposit(key, Currency.MONEY, amount, "admin_give", actor).join().ok());
    }

    private long purse(UUID key) {
        return economy.balance(AccountRef.player(key), Currency.MONEY).join();
    }

    private long bal(AccountRef account) {
        return economy.balance(account, Currency.MONEY).join();
    }

    private long ledgerRows(String reason) {
        return db.blocking(c -> Sql.queryOne(c, "SELECT COUNT(*) FROM eco_ledger WHERE reason = ?", rs -> rs.getLong(1), reason).orElse(0L));
    }

    private BankOps.Terms personal() {
        return ops.personalTerms(1, true);
    }

    /** A time inside period {@code period}, {@code minutes} after its payout. */
    private void at(long period, long minutes) {
        clock.set(period * P + minutes * 60_000L);
    }

    // ------------------------------------------------------------------------------------------------ deposit / withdraw

    @Test
    void depositAndWithdrawAreOneLedgerTransactionEach() {
        AccountRef bank = AccountRef.bank(steve);
        BankOps.Outcome in = ops.deposit(steve, bank, 50_000, personal(), actor).join();
        assertEquals(BankOps.Status.OK, in.status());
        assertEquals(50_000L, in.bank());
        assertEquals(950_000L, in.purse());
        assertEquals(1, ledgerRows("bank_deposit"));

        at(5, 60); // not in the free minutes: 2% fee
        BankOps.Outcome out = ops.withdraw(steve, bank, 10_000, personal(), null, actor).join();
        assertEquals(BankOps.Status.OK, out.status());
        assertEquals(200, out.fee());
        assertEquals(40_000, bal(bank));
        assertEquals(959_800, purse(steve), "gets the amount minus the fee");
        assertEquals(1, ledgerRows("bank_withdraw"));
        assertEquals(1, ledgerRows("bank_fee"));
    }

    @Test
    void capacityAndBalancesAreCheckedInsideTheTransaction() {
        AccountRef bank = AccountRef.bank(steve);
        assertEquals(BankOps.Status.FULL, ops.deposit(steve, bank, 100_001, personal(), actor).join().status(), "tier 1 holds 100k");
        assertEquals(1_000_000, purse(steve), "nothing moved");
        assertEquals(BankOps.Status.OK, ops.deposit(steve, bank, 100_000, personal(), actor).join().status());
        assertEquals(BankOps.Status.FULL, ops.deposit(steve, bank, 1, personal(), actor).join().status());
        assertEquals(BankOps.Status.BANK_EMPTY, ops.withdraw(steve, bank, 100_001, personal(), null, actor).join().status());
        assertEquals(BankOps.Status.BELOW_MIN, ops.withdraw(steve, bank, 99, personal(), null, actor).join().status());
        UUID poor = profiles.add(UUID.randomUUID(), UUID.randomUUID());
        assertEquals(BankOps.Status.NO_MONEY, ops.deposit(poor, AccountRef.bank(poor), 5_000, personal(), actor).join().status());
        assertEquals(0, bal(AccountRef.bank(poor)));
    }

    @Test
    void p4NoDepositIntoAnotherOwnProfilesBank() {
        BankOps.Outcome own = ops.deposit(steve2, AccountRef.bank(steve), 1_000, personal(), actor).join();
        assertEquals(BankOps.Status.OWN_PROFILE, own.status());
        assertEquals(List.of("bank_deposit"), profiles.blocked, "the attempt is logged");
        assertEquals(BankOps.Status.OWN_PROFILE, ops.withdraw(steve, AccountRef.bank(steve2), 1_000, personal(), null, actor).join().status());
        assertEquals(BankOps.Status.NOT_YOURS, ops.deposit(alex, AccountRef.bank(steve), 1_000, personal(), actor).join().status());
        assertEquals(1_000_000, purse(steve2));
        assertEquals(0, bal(AccountRef.bank(steve)));
        assertEquals(0, ledgerRows("bank_deposit"));
    }

    // ------------------------------------------------------------------------------------------------ interest

    @Test
    void interestIsPaidOncePerPeriodWhateverHappens() throws Exception {
        AccountRef bank = AccountRef.bank(steve);
        ops.deposit(steve, bank, 50_000, personal(), actor).join(); // period 5: lowest balance 0 (it was empty)
        at(7, 30);
        BankOps.Settled first = ops.settle(bank, personal()).join();
        assertEquals(75, first.paid(), "period 5 pays nothing (lowest 0), period 6 pays 0.15% of 50k");
        assertEquals(0, ops.settle(bank, personal()).join().paid(), "a second run pays nothing");
        // A crowd of runs at once for the next period: still paid exactly once.
        at(8, 1);
        List<CompletableFuture<BankOps.Settled>> runs = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            runs.add(CompletableFuture.supplyAsync(() -> ops.settle(bank, personal()).join()));
        }
        long paid = 0;
        for (CompletableFuture<BankOps.Settled> run : runs) {
            paid += run.get().paid();
        }
        assertEquals(75, paid, "period 7: 0.15% of 50,075 = 75 (rounded down), once");
        assertEquals(2, ledgerRows("interest"));
        assertEquals(50_150, bal(bank));
        assertEquals(1L, (long) db.blocking(c -> Sql.queryOne(c, "SELECT COUNT(*) FROM eco_ledger WHERE idem_key = ?", rs -> rs.getLong(1),
                BankOps.interestKey(bank, 7)).orElse(0L)));
    }

    @Test
    void depositsDuringAPeriodOnlyCountFromTheNextOne() {
        AccountRef bank = AccountRef.bank(steve);
        ops.deposit(steve, bank, 10_000, personal(), actor).join();
        at(6, 60);
        ops.deposit(steve, bank, 40_000, personal(), actor).join(); // deposit before payout: period 6 still counts 10k
        at(7, 1);
        assertEquals(15, ops.settle(bank, personal()).join().paid(), "0.15% of the lowest balance 10k");
        at(8, 1);
        assertEquals(75, ops.settle(bank, personal()).join().paid(), "period 7: the whole 50,015");
    }

    @Test
    void aWithdrawalWithAFeeForfeitsThePeriodButAFreeOneDoesNot() {
        AccountRef bank = AccountRef.bank(steve);
        ops.deposit(steve, bank, 50_000, personal(), actor).join();
        at(6, 60);
        BankOps.Outcome withFee = ops.withdraw(steve, bank, 1_000, personal(), null, actor).join();
        assertEquals(20, withFee.fee());
        at(7, 5); // in the free minutes
        assertEquals(0, ops.settle(bank, personal()).join().paid(), "period 6 forfeited");
        BankOps.Outcome free = ops.withdraw(steve, bank, 9_000, personal(), null, actor).join();
        assertEquals(0, free.fee(), "free right after a payout");
        at(8, 1);
        assertEquals(60, ops.settle(bank, personal()).join().paid(), "period 7 pays on the lowest balance 40,000");
    }

    @Test
    void capsAndRoomLimitEachPayout() {
        AccountRef bank = AccountRef.bank(steve);
        ops.deposit(steve, bank, 100_000, personal(), actor).join();
        at(7, 1);
        assertEquals(0, ops.settle(bank, personal()).join().paid(), "a full bank has no room for interest");
        ops.withdraw(steve, bank, 1_000, personal(), null, actor).join(); // free window, room 1,000 now
        at(9, 1);
        assertEquals(296, ops.settle(bank, personal()).join().paid(), "period 7 (lowest 99,000): 148; period 8 (99,148): 148");
        at(30, 1);
        ops.settle(bank, personal()).join();
        assertEquals(100_000, bal(bank), "interest stops at the capacity, never above");
    }

    @Test
    void afterDowntimeOnlyTheLastPeriodsArePaid() {
        AccountRef bank = AccountRef.bank(steve);
        ops.deposit(steve, bank, 10_000, personal(), actor).join();
        at(105, 1);
        BankOps.Settled s = ops.settle(bank, personal()).join();
        assertEquals(config.catchUpPeriods, s.periods());
        assertEquals(8 * 15, s.paid());
    }

    @Test
    void inactiveAccountsEarnNothingButStaySettled() {
        AccountRef bank = AccountRef.bank(steve);
        ops.deposit(steve, bank, 10_000, personal(), actor).join();
        at(8, 1);
        assertEquals(0, ops.settle(bank, ops.personalTerms(1, false)).join().paid());
        at(9, 1);
        assertEquals(15, ops.settle(bank, personal()).join().paid(), "only period 8, the open one");
    }

    @Test
    void activityRuleNeedsTwoHoursInSevenDays() {
        config.activityMinutes = 120;
        ops.store().addActivity(Map.of(steve, 60), day.get() - 3).join();
        assertFalse(ops.active(List.of(steve)).join());
        ops.store().addActivity(Map.of(steve, 60), day.get()).join();
        assertTrue(ops.active(List.of(steve)).join());
        ops.store().addActivity(Map.of(alex, 500), day.get() - 7).join();
        assertFalse(ops.active(List.of(alex)).join(), "older than 7 days");
        assertTrue(ops.active(List.of(alex, steve)).join(), "island: one member is enough");
    }

    // ------------------------------------------------------------------------------------------------ island

    @Test
    void islandDailyLimitPerMember() {
        UUID islandId = UUID.randomUUID();
        AccountRef island = AccountRef.island(islandId);
        BankOps.Terms terms = ops.islandTerms(0, 0, true);
        assertEquals(BankOps.Status.OK, ops.deposit(steve, island, 500_000, terms, actor).join().status());
        BankOps.Limit limit = new BankOps.Limit(islandId, alex, 100_000);
        assertEquals(BankOps.Status.OK, ops.withdraw(alex, island, 60_000, terms, limit, actor).join().status());
        assertEquals(BankOps.Status.LIMIT, ops.withdraw(alex, island, 50_000, terms, limit, actor).join().status());
        assertEquals(440_000, bal(island), "the refused one moved nothing");
        assertEquals(BankOps.Status.OK, ops.withdraw(alex, island, 40_000, terms, limit, actor).join().status(), "exactly the limit");
        day.incrementAndGet();
        assertEquals(BankOps.Status.OK, ops.withdraw(alex, island, 60_000, terms, limit, actor).join().status(), "a new day");
        BankOps.Limit none = new BankOps.Limit(islandId, steve, Long.MAX_VALUE);
        assertEquals(BankOps.Status.OK, ops.withdraw(steve, island, 300_000, terms, none, actor).join().status(), "no limit");
    }

    @Test
    void islandBankMoneyIsDeletedOnceWhenTheIslandIsDisbanded() {
        AccountRef island = AccountRef.island(UUID.randomUUID());
        ops.deposit(alex, island, 30_000, ops.islandTerms(0, 0, true), actor).join();
        long steveBefore = purse(steve);
        long alexBefore = purse(alex);
        assertEquals(30_000L, ops.deleteMoney(island, actor).join());
        assertEquals(0L, ops.deleteMoney(island, actor).join(), "a second event deletes nothing more");
        assertEquals(0, bal(island));
        assertEquals(steveBefore, purse(steve), "owner answer 263: nobody gets the island's money");
        assertEquals(alexBefore, purse(alex));
        assertEquals(1, ledgerRows(BankOps.REASON_DELETED));
    }

    @Test
    void onlyADisbandDeletesTheBankMoney() {
        assertEquals(BankRules.Removal.DELETE_MONEY, BankRules.onRemoved("disband"));
        assertEquals(BankRules.Removal.KEEP_CLOSED, BankRules.onRemoved("delete"), "staff delete: staff decide");
        assertEquals(BankRules.Removal.KEEP_CLOSED, BankRules.onRemoved("reset"));
    }

    // ------------------------------------------------------------------------------------------------ tiers

    @Test
    void tiersAreBoughtOneAtATimeAndOnlyOnce() {
        BankOps.Outcome bought = ops.buyTier(steve, 1, actor).join();
        assertEquals(BankOps.Status.OK, bought.status());
        assertEquals(10_000, bought.amount());
        assertEquals(990_000, purse(steve));
        assertEquals(BankOps.Status.BUSY, ops.buyTier(steve, 1, actor).join().status(), "a double click buys nothing");
        assertEquals(990_000, purse(steve));
        assertEquals(500_000, ops.personalTerms(steve, true).join().capacity(), "tier 2 holds 500k");
        assertEquals(BankOps.Status.OK, ops.deposit(steve, AccountRef.bank(steve), 400_000, ops.personalTerms(2, true), actor).join().status());
        UUID poor = profiles.add(UUID.randomUUID(), UUID.randomUUID());
        assertEquals(BankOps.Status.NO_MONEY, ops.buyTier(poor, 1, actor).join().status());
        assertEquals(1, ops.personalTerms(poor, true).join().capacity() == 100_000 ? 1 : 0, "tier unchanged");
        assertEquals(BankOps.Status.MAX_TIER, ops.buyTier(steve, 10, actor).join().status());
        assertEquals(1, ledgerRows("bank_tier"));
    }
}
