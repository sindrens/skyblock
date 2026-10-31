package net.skybornmc.skyblock.pass;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.DatabaseConfig;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.economy.Currency;
import net.skybornmc.skyblock.common.economy.Transaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** XP and claims on H2 in MySQL mode (same SQL as MariaDB): XP once per event key, a tier once, premium cosmetic-only and once per account. */
class PassStoreTest {

    private static final String SEASON = "s1";

    @TempDir
    Path dir;
    private Database db;
    private final UUID player = UUID.randomUUID();
    private final UUID profile = UUID.randomUUID();
    private final PassMath.Curve curve = new PassMath.Curve(100, 800, 2_000);
    private final long[] ref = {827, 1467};
    private PassTable table;
    /** Ledger transactions that "committed" (the test runner records them instead of an economy). */
    private final List<Transaction> paid = new CopyOnWriteArrayList<>();
    private PassClaims.Runner runner;

    @BeforeEach
    void start() throws Exception {
        DatabaseConfig config = new DatabaseConfig();
        config.type = DatabaseConfig.Type.H2;
        config.h2File = "pass-test";
        config.poolSize = 4;
        db = new Database(config, dir.toFile(), Logger.getLogger("pass-test"));
        db.start();
        new Migrations(db, Logger.getLogger("pass-test")).migrate("pass", getClass().getClassLoader());
        runner = (txs, guard) -> PassClaims.local(db, guard).thenApply(r -> {
            if (r.ok()) {
                paid.addAll(txs);
            }
            return r;
        });
        PassSettings s = new PassSettings();
        table = PassTable.parse(s.freeEveryLevel, s.freeMilestones, s.freeOverflow, s.premiumMilestones, s.maxLevel, p -> { });
    }

    @AfterEach
    void stop() {
        db.close();
    }

    private PassStore.XpResult add(UUID who, int xp, String key) {
        return db.blockingTransaction(c -> PassStore.addXp(c, who, SEASON, key, "quest_daily", before -> xp, 1L));
    }

    private void setXp(UUID who, long xp) {
        db.blockingTransaction(c -> {
            PassStore.setXp(c, who, SEASON, xp, 1L);
            return true;
        });
    }

    private PassClaims.Status claim(UUID prof, boolean premium, PassRules.TierRef... tiers) {
        PassClaims.Plan plan = PassClaims.plan(new PassClaims.Claimer(prof, player, SEASON, 1, premium), List.of(tiers), table, curve, ref,
                1.3, Actor.SYSTEM, 5L);
        return PassClaims.commit(runner, plan).join();
    }

    private static PassRules.TierRef free(int tier) {
        return new PassRules.TierRef(PassTable.Track.FREE, tier);
    }

    private static PassRules.TierRef premium(int tier) {
        return new PassRules.TierRef(PassTable.Track.PREMIUM, tier);
    }

    private List<PassStore.GrantRow> grants() {
        return db.blocking(c -> PassStore.grants(c, PassStore.PENDING, 100));
    }

    @Test
    void xpIsAddedOncePerEventKey() {
        PassStore.XpResult first = add(profile, 100, "quests:abc");
        assertFalse(first.duplicate());
        assertEquals(100, first.after());
        PassStore.XpResult again = add(profile, 100, "quests:abc");
        assertTrue(again.duplicate(), "a retried hand-out adds nothing");
        assertEquals(0, again.added());
        assertEquals(100, (long) db.blocking(c -> PassStore.xp(c, profile, SEASON)));
        add(profile, 25, "rewards:vote-1");
        add(profile, 20, null);
        add(profile, 20, null);
        assertEquals(165, (long) db.blocking(c -> PassStore.xp(c, profile, SEASON)), "no key = every call counts");
        assertEquals(0, (long) db.blocking(c -> PassStore.xp(c, UUID.randomUUID(), SEASON)), "profiles are separate");
        assertEquals(0, (long) db.blocking(c -> PassStore.xp(c, profile, "s2")), "seasons are separate");
    }

    @Test
    void catchUpIsDecidedFromTheXpBefore() {
        setXp(profile, 1_000);
        PassStore.XpResult r = db.blockingTransaction(c -> PassStore.addXp(c, profile, SEASON, "k1", "vote",
                before -> PassMath.applied(100, PassMath.catchUp(true, 30, curve.level(before), 100, 1.1, 15, 1.5), 10_000), 2L));
        assertEquals(150, r.added(), "level 1 on day 30 is far behind: +50%");
        assertEquals(1_000, r.before());
    }

    @Test
    void aTierIsClaimedOnce() {
        setXp(profile, 5 * 800);
        assertEquals(PassClaims.Status.OK, claim(profile, false, free(1), free(2), free(3), free(4), free(5)));
        assertEquals(PassClaims.Status.CHANGED, claim(profile, false, free(5)), "a second click or a retry pays nothing");
        assertEquals(PassClaims.Status.CHANGED, claim(profile, false, free(4), free(6)), "all or nothing");
        long money = paid.stream().filter(t -> t.currency() == Currency.MONEY).mapToLong(Transaction::amount).sum();
        assertEquals(3 * 1654, money, "money at levels 1, 3, 5 (2 minutes at tier 1)");
        assertEquals(2, paid.size(), "one money and one crystal ledger row for the whole claim");
        assertTrue(paid.getFirst().idempotencyKey().startsWith("pass:"));
        List<PassStore.GrantRow> rows = grants();
        assertEquals(1, rows.size(), "one key row");
        assertEquals("ember:pass", rows.getFirst().ref(), "pass keys use the tradeable source");
        assertEquals(2, rows.getFirst().amount());
        assertEquals(Set.of(1, 2, 3, 4, 5), db.blocking(c -> PassStore.claimed(c, profile, SEASON, PassTable.Track.FREE)));
    }

    @Test
    void unreachedTiersAreRefused() {
        setXp(profile, 5 * 800 - 1);
        assertEquals(PassClaims.Status.CHANGED, claim(profile, false, free(5)));
        assertEquals(Set.of(), db.blocking(c -> PassStore.claimed(c, profile, SEASON, PassTable.Track.FREE)));
        assertTrue(paid.isEmpty());
    }

    @Test
    void premiumNeedsPremiumGivesOnlyCosmeticsAndOncePerAccount() {
        setXp(profile, 10 * 800);
        assertEquals(PassClaims.Status.CHANGED, claim(profile, false, premium(5)), "no premium, no premium tier");
        assertEquals(PassClaims.Status.OK, claim(profile, true, premium(5), premium(10)));
        assertTrue(paid.isEmpty(), "premium tiers never pay money or crystals");
        List<PassStore.GrantRow> rows = grants();
        assertEquals(2, rows.size());
        assertTrue(rows.stream().allMatch(r -> r.kind().equals(PassStore.TITLE)), "titles only");
        UUID second = UUID.randomUUID();
        setXp(second, 10 * 800);
        assertEquals(PassClaims.Status.CHANGED, claim(second, true, premium(5)), "the same account's other profile can't claim it again");
        assertEquals(PassClaims.Status.OK, claim(second, false, free(5)), "but its own free track is separate");
        assertEquals(Set.of(5, 10), db.blocking(c -> PassStore.claimed(c, player, SEASON, PassTable.Track.PREMIUM)));
    }

    @Test
    void overflowKeysAreBound() {
        setXp(profile, 80_000 + 2_000);
        assertEquals(PassClaims.Status.OK, claim(profile, false, free(101)));
        assertEquals("ember:" + PassClaims.OVERFLOW_KEY_REASON, grants().getFirst().ref());
    }

    @Test
    void premiumRowsAndHandOutStates() {
        assertTrue(db.<Boolean>blockingTransaction(c -> PassStore.setPremium(c, player, SEASON, true, "staff:Admin", 1L)));
        assertFalse(db.<Boolean>blockingTransaction(c -> PassStore.setPremium(c, player, SEASON, true, "staff:Admin", 1L)), "already on");
        assertTrue(db.<Boolean>blocking(c -> PassStore.premium(c, player, SEASON)));
        assertFalse(db.<Boolean>blocking(c -> PassStore.premium(c, player, "s2")));
        assertTrue(db.<Boolean>blockingTransaction(c -> PassStore.setPremium(c, player, SEASON, false, "staff:Admin", 1L)));
        assertFalse(db.<Boolean>blocking(c -> PassStore.premium(c, player, SEASON)));

        setXp(profile, 12 * 800);
        claim(profile, false, free(12));
        PassStore.GrantRow booster = grants().getFirst();
        assertEquals(PassStore.BOOSTER, booster.kind());
        assertTrue(db.<Boolean>blockingTransaction(c -> PassStore.move(c, booster.id(), PassStore.PENDING, PassStore.SENDING, 2L)));
        assertFalse(db.<Boolean>blockingTransaction(c -> PassStore.move(c, booster.id(), PassStore.PENDING, PassStore.SENDING, 2L)),
                "a row is taken once");
        List<PassStore.GrantRow> sending = new ArrayList<>(db.blocking(c -> PassStore.grants(c, PassStore.SENDING, 10)));
        assertEquals(1, sending.size());
    }

    @Test
    void aDeletedProfileLosesItsPassButNotTheAccountsPremium() {
        setXp(profile, 10 * 800);
        claim(profile, true, free(1), premium(5));
        db.blockingTransaction(c -> {
            PassStore.deleteProfile(c, profile);
            return true;
        });
        assertEquals(0, (long) db.blocking(c -> PassStore.xp(c, profile, SEASON)));
        assertEquals(Set.of(), db.blocking(c -> PassStore.claimed(c, profile, SEASON, PassTable.Track.FREE)));
        assertEquals(Set.of(5), db.blocking(c -> PassStore.claimed(c, player, SEASON, PassTable.Track.PREMIUM)));
    }
}
