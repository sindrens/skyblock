package net.skybornmc.skyblock.afk;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.DatabaseConfig;
import net.skybornmc.skyblock.common.db.Migrations;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The AFK tables on H2 in MySQL mode (same SQL as MariaDB): crash/restore of daily totals, replay safety, grants, boosters. */
class AfkStoreTest {

    private static final long MIN = 60_000L;
    @TempDir
    Path dir;
    private Database db;
    private final AfkStore store = new AfkStore();
    private final AfkRules rules = AfkRules.from(new AfkConfig());

    @BeforeEach
    void start() throws Exception {
        DatabaseConfig config = new DatabaseConfig();
        config.type = DatabaseConfig.Type.H2;
        config.h2File = "afk-test";
        config.poolSize = 2;
        db = new Database(config, dir.toFile(), Logger.getLogger("afk-test"));
        db.start();
        new Migrations(db, Logger.getLogger("afk-test")).migrate("afk", getClass().getClassLoader());
    }

    @AfterEach
    void stop() {
        db.close();
    }

    private boolean bool(net.skybornmc.skyblock.common.db.Sql.SqlFunction<Boolean> work) {
        return db.blockingTransaction(work);
    }

    private AfkAccount load(UUID player, int day) {
        AfkAccount a = new AfkAccount(player);
        a.restore(db.blockingTransaction(c -> store.load(c, player, day, 1L)));
        return a;
    }

    @Test
    void dailyTotalSurvivesACrashAndTheCapStillHolds() {
        UUID player = UUID.randomUUID();
        AfkAccount a = load(player, 20261030);
        assertEquals(0, a.creditedMillis);
        // 7 h 50 min credited, saved by the periodic save, then the server crashes.
        AfkLedger.credit(a, 20261030, 470 * MIN, Long.MAX_VALUE, rules);
        a.rewardSeq = 0; // rewards are stored by reward(); here only the plain save ran
        AfkAccount.Snapshot s = a.snapshot();
        db.blockingTransaction(c -> {
            store.save(c, s, 2L);
            return null;
        });
        AfkAccount restored = load(player, 20261030);
        assertEquals(470 * MIN, restored.creditedMillis);
        assertEquals(s.progressMillis(), restored.progressMillis);
        AfkLedger.Result r = AfkLedger.credit(restored, 20261030, 60 * MIN, Long.MAX_VALUE, rules);
        assertEquals(10 * MIN, r.credited(), "only 10 minutes left after the restart");
        // The next day starts at 0 from the database too.
        assertEquals(0, load(player, 20261031).creditedMillis);
        assertEquals(s.progressMillis(), load(player, 20261031).progressMillis, "progress is per account, not per day");
    }

    @Test
    void aRewardIsStoredOnceEvenWhenReplayed() {
        UUID player = UUID.randomUUID();
        AfkAccount a = load(player, 20261030);
        AfkLedger.Result r = AfkLedger.credit(a, 20261030, 15 * MIN, Long.MAX_VALUE, rules);
        assertEquals(1, r.rewards().size());
        AfkAccount.Snapshot s = a.snapshot();
        assertTrue(bool(c -> store.reward(c, s, 1, 3L)));
        assertFalse(bool(c -> store.reward(c, s, 1, 4L)), "same reward number again is refused");
        assertEquals(1L, (long) db.blocking(c -> store.rewardSeq(c, player)));
        assertEquals(15 * MIN, (long) db.blocking(c -> store.credited(c, player, 20261030)));
        // A stale copy (after a crash that lost the last save) cannot skip ahead either.
        assertFalse(bool(c -> store.reward(c, s, 3, 5L)));
    }

    @Test
    void keyMilestoneOncePerDay() {
        UUID player = UUID.randomUUID();
        assertTrue(bool(c -> store.milestone(c, player, 20261030, 60, 1L)));
        assertFalse(bool(c -> store.milestone(c, player, 20261030, 60, 2L)));
        assertTrue(bool(c -> store.milestone(c, player, 20261031, 60, 3L)));
    }

    @Test
    void grantsMergeClaimAndUnclaim() {
        UUID player = UUID.randomUUID();
        UUID profile = UUID.randomUUID();
        db.blockingTransaction(c -> {
            store.addGrant(c, player, profile, AfkStore.KIND_KEY, "ember", 1, 1L);
            store.addGrant(c, player, profile, AfkStore.KIND_KEY, "ember", 1, 2L);
            store.addGrant(c, player, profile, AfkStore.KIND_FUEL, UUID.randomUUID().toString(), 15, 3L);
            return null;
        });
        List<AfkStore.Grant> open = db.blocking(c -> store.openGrants(c, player));
        assertEquals(2, open.size());
        AfkStore.Grant keys = open.getFirst();
        assertEquals(2, keys.amount());
        assertTrue(bool(c -> store.claim(c, keys.id(), 4L)));
        assertFalse(bool(c -> store.claim(c, keys.id(), 5L)), "a grant is handed out once");
        // While it was out, a new key was earned; the hand-out failed and the claimed one comes back merged.
        db.blockingTransaction(c -> {
            store.addGrant(c, player, profile, AfkStore.KIND_KEY, "ember", 1, 6L);
            store.unclaim(c, keys, 7L);
            return null;
        });
        List<AfkStore.Grant> after = db.blocking(c -> store.openGrants(c, player));
        assertEquals(2, after.size());
        assertEquals(3, after.stream().filter(g -> g.kind().equals(AfkStore.KIND_KEY)).mapToLong(AfkStore.Grant::amount).sum());
    }

    @Test
    void boosterIsBankedCappedAndStartedOnce() {
        UUID profile = UUID.randomUUID();
        db.blockingTransaction(c -> store.bank(c, profile, 50, 10 * MIN, 25 * MIN, 1L));
        db.blockingTransaction(c -> store.bank(c, profile, 50, 10 * MIN, 25 * MIN, 2L));
        AfkStore.Booster b = db.blockingTransaction(c -> store.bank(c, profile, 50, 10 * MIN, 25 * MIN, 3L));
        assertEquals(25 * MIN, b.bankedMillis(), "capped at the max banked time");
        assertFalse(b.active(1_000));
        AfkStore.Booster started = db.blockingTransaction(c -> store.activate(c, profile, 1_000)).orElseThrow();
        assertEquals(1_000 + 25 * MIN, started.activeUntil());
        assertTrue(started.active(2_000));
        assertTrue(db.blockingTransaction(c -> store.activate(c, profile, 2_000)).isEmpty(), "nothing banked any more");
    }
}
