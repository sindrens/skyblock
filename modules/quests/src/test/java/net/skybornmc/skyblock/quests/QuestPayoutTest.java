package net.skybornmc.skyblock.quests;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Payouts on H2 in MySQL mode (same SQL as MariaDB): once per quest, the all-done key once per set, island helpers once. */
class QuestPayoutTest {

    private static final int DAY = 20_757;

    @TempDir
    Path dir;
    private Database db;
    private final UUID player = UUID.randomUUID();
    private final UUID profile = UUID.randomUUID();
    /** Ledger transactions that "committed" (the test runner records them instead of an economy). */
    private final List<Transaction> paid = new CopyOnWriteArrayList<>();
    private Payouts.Runner runner;

    @BeforeEach
    void start() throws Exception {
        DatabaseConfig config = new DatabaseConfig();
        config.type = DatabaseConfig.Type.H2;
        config.h2File = "quests-test";
        config.poolSize = 4;
        db = new Database(config, dir.toFile(), Logger.getLogger("quests-test"));
        db.start();
        new Migrations(db, Logger.getLogger("quests-test")).migrate("quests", getClass().getClassLoader());
        runner = (txs, guard) -> Payouts.local(db, guard).thenApply(r -> {
            if (r.ok()) {
                paid.addAll(txs);
            }
            return r;
        });
    }

    @AfterEach
    void stop() {
        db.close();
    }

    private QuestSet stored(QuestKind kind, UUID owner, int count) {
        QuestSet set = new QuestSet(kind, owner, DAY, 0, false);
        QuestType[] types = {QuestType.MINE, QuestType.SELL, QuestType.VISIT, QuestType.CHOP, QuestType.KILL};
        for (int i = 0; i < count; i++) {
            set.quests.add(new Quest(UUID.randomUUID(), i, types[i], types[i].depths() ? 2 : 0, 100, kind == QuestKind.ISLAND ? 0 : 500, 20,
                    100, 0, 0));
        }
        return db.blockingTransaction(c -> QuestStore.insertOrLoad(c, set, 3, 1L));
    }

    private Payouts.Status pay(QuestSet set, Quest q) {
        return Payouts.commit(runner, Payouts.personal(set, q, player, new Payouts.Bonus("cinder", 1, "quest_daily"), true, Actor.SYSTEM, 10L)).join();
    }

    private List<QuestStore.GrantRow> grants(String kind) {
        return db.blocking(c -> QuestStore.grants(c, QuestStore.PENDING, 100)).stream().filter(g -> g.kind().equals(kind)).toList();
    }

    @Test
    void storedSetLoadsBackAndAFirstRollWins() {
        QuestSet first = stored(QuestKind.DAILY, profile, 3);
        QuestSet other = new QuestSet(QuestKind.DAILY, profile, DAY, 0, false);
        other.quests.add(new Quest(UUID.randomUUID(), 0, QuestType.LIKE, 0, 1, 1, 1, 1, 0, 0));
        QuestSet result = db.blockingTransaction(c -> QuestStore.insertOrLoad(c, other, 1, 2L));
        assertEquals(3, result.quests.size(), "the stored set wins over a second roll");
        assertEquals(first.quests.get(0).uid, result.quests.get(0).uid);
        QuestSet loaded = db.blocking(c -> QuestStore.load(c, QuestKind.DAILY, profile, DAY)).orElseThrow();
        assertEquals(List.of(QuestType.MINE, QuestType.SELL, QuestType.VISIT), loaded.quests.stream().map(q -> q.type).toList());
        assertEquals(2, loaded.quests.get(0).tierFrom);
        assertTrue(db.blocking(c -> QuestStore.load(c, QuestKind.WEEKLY, profile, DAY)).isEmpty(), "boards are separate");
        assertTrue(db.blocking(c -> QuestStore.load(c, QuestKind.DAILY, UUID.randomUUID(), DAY)).isEmpty(), "profiles are separate");
    }

    @Test
    void progressIsSavedButNeverLoweredOrWrittenAfterDone() {
        QuestSet set = stored(QuestKind.DAILY, profile, 1);
        Quest q = set.quests.getFirst();
        db.blockingTransaction(c -> QuestStore.saveProgress(c, q.uid, 60, 2L));
        db.blockingTransaction(c -> QuestStore.saveProgress(c, q.uid, 40, 3L));
        assertEquals(60, db.blocking(c -> QuestStore.load(c, QuestKind.DAILY, profile, DAY)).orElseThrow().quests.getFirst().progress);
        assertEquals(Payouts.Status.OK, pay(set, q));
        assertEquals(0, (int) db.<Integer>blockingTransaction(c -> QuestStore.saveProgress(c, q.uid, 70, 4L)));
        Quest after = db.blocking(c -> QuestStore.load(c, QuestKind.DAILY, profile, DAY)).orElseThrow().quests.getFirst();
        assertTrue(after.done());
        assertEquals(100, after.progress);
    }

    @Test
    void aQuestPaysOnce() {
        QuestSet set = stored(QuestKind.DAILY, profile, 3);
        Quest q = set.quests.getFirst();
        assertEquals(Payouts.Status.OK, pay(set, q));
        assertEquals(Payouts.Status.CHANGED, pay(set, q), "a second attempt (retry, crash recovery) pays nothing");
        assertEquals(2, paid.size(), "money + crystals once");
        assertEquals(500, paid.stream().filter(t -> t.currency() == Currency.MONEY).mapToLong(Transaction::amount).sum());
        assertEquals(1, grants(QuestStore.PASS_XP).size());
        assertEquals(0, grants(QuestStore.KEY).size(), "2 quests are still open");
        assertTrue(paid.getFirst().idempotencyKey().startsWith("quest:"), "ledger idempotency key per quest instance");
    }

    @Test
    void noPassXpRowWhileThereIsNoPass() {
        QuestSet set = stored(QuestKind.DAILY, profile, 1);
        Payouts.Status status = Payouts.commit(runner, Payouts.personal(set, set.quests.getFirst(), player, null, false, Actor.SYSTEM, 5L)).join();
        assertEquals(Payouts.Status.OK, status);
        assertEquals(0, grants(QuestStore.PASS_XP).size());
    }

    @Test
    void allDoneKeyOnce() {
        QuestSet set = stored(QuestKind.DAILY, profile, 3);
        assertEquals(Payouts.Status.OK, pay(set, set.quests.get(0)));
        assertEquals(Payouts.Status.OK, pay(set, set.quests.get(1)));
        assertEquals(0, grants(QuestStore.KEY).size());
        Payouts.Plan last = Payouts.personal(set, set.quests.get(2), player, new Payouts.Bonus("cinder", 1, "quest_daily"), true, Actor.SYSTEM, 9L);
        assertEquals(Payouts.Status.OK, Payouts.commit(runner, last).join());
        assertTrue(last.outcome().bonus);
        List<QuestStore.GrantRow> keys = grants(QuestStore.KEY);
        assertEquals(1, keys.size());
        assertEquals("cinder:quest_daily", keys.getFirst().ref());
        assertEquals(1, keys.getFirst().amount());
        assertEquals(profile, keys.getFirst().profile());
        assertFalse(db.<Boolean>blockingTransaction(c -> QuestStore.claimBonus(c, QuestKind.DAILY, profile, DAY)), "claimed already");
    }

    @Test
    void allDoneKeyOnceWhenTheLastTwoFinishTogether() {
        for (int round = 0; round < 10; round++) {
            UUID owner = UUID.randomUUID();
            QuestSet set = stored(QuestKind.WEEKLY, owner, 3);
            assertEquals(Payouts.Status.OK, Payouts.commit(runner, Payouts.personal(set, set.quests.get(0), player,
                    new Payouts.Bonus("blaze", 1, "quest_weekly"), true, Actor.SYSTEM, 1L)).join());
            Payouts.Plan a = Payouts.personal(set, set.quests.get(1), player, new Payouts.Bonus("blaze", 1, "quest_weekly"), true, Actor.SYSTEM, 2L);
            Payouts.Plan b = Payouts.personal(set, set.quests.get(2), player, new Payouts.Bonus("blaze", 1, "quest_weekly"), true, Actor.SYSTEM, 2L);
            CompletableFuture<Payouts.Status> fa = Payouts.commit(runner, a);
            CompletableFuture<Payouts.Status> fb = Payouts.commit(runner, b);
            assertEquals(Payouts.Status.OK, fa.join());
            assertEquals(Payouts.Status.OK, fb.join());
            assertEquals(1, (a.outcome().bonus ? 1 : 0) + (b.outcome().bonus ? 1 : 0), "exactly one of them pays the key");
        }
        assertEquals(10, grants(QuestStore.KEY).size());
    }

    @Test
    void swapNeedsAFreeSwapAndAnOpenQuest() {
        QuestSet set = stored(QuestKind.DAILY, profile, 3);
        Quest old = set.quests.get(1);
        Quest fresh = new Quest(UUID.randomUUID(), 1, QuestType.CHOP, 3, 400, 500, 20, 100, 0, 0);
        assertTrue(db.<Boolean>blockingTransaction(c -> QuestStore.swap(c, set, 0, old, fresh, 5L)));
        assertFalse(db.<Boolean>blockingTransaction(c -> QuestStore.swap(c, set, 0, fresh, old, 6L)), "the swap count moved on");
        QuestSet loaded = db.blocking(c -> QuestStore.load(c, QuestKind.DAILY, profile, DAY)).orElseThrow();
        assertEquals(1, loaded.rerollsUsed);
        assertEquals(QuestType.CHOP, loaded.slot(1).orElseThrow().type);
        assertEquals(fresh.uid, loaded.slot(1).orElseThrow().uid);
        // The old quest instance can't be paid any more.
        assertEquals(Payouts.Status.CHANGED, pay(set, old));
        // A finished quest can't be swapped.
        Quest first = set.quests.getFirst();
        assertEquals(Payouts.Status.OK, pay(set, first));
        Quest other = new Quest(UUID.randomUUID(), 0, QuestType.KILL, 1, 75, 1, 1, 1, 0, 0);
        assertFalse(db.<Boolean>blockingTransaction(c -> QuestStore.swap(c, loaded, 1, first, other, 7L)));
    }

    @Test
    void islandHelpersArePaidOnce() {
        UUID island = UUID.randomUUID();
        QuestSet set = stored(QuestKind.ISLAND, island, 3);
        Quest q = set.quests.getFirst();
        UUID helperA = UUID.randomUUID();
        UUID helperB = UUID.randomUUID();
        UUID tiny = UUID.randomUUID();
        Map<UUID, Long> contributions = new LinkedHashMap<>();
        contributions.put(helperA, 70L);
        contributions.put(helperB, 28L);
        contributions.put(tiny, 2L);
        Map<UUID, UUID> helpers = new LinkedHashMap<>();
        for (Map.Entry<UUID, Long> e : contributions.entrySet()) {
            if (QuestMath.helped(e.getValue(), q.target, 5)) {
                helpers.put(e.getKey(), UUID.randomUUID());
            }
        }
        assertEquals(List.of(helperA, helperB), new ArrayList<>(helpers.keySet()), "a 2% share is not a helper");
        Payouts.Plan plan = Payouts.island(set, q, helpers, contributions, true, Actor.SYSTEM, 3L);
        assertEquals(Payouts.Status.OK, Payouts.commit(runner, plan).join());
        assertEquals(2, paid.size(), "crystals for each helper");
        assertTrue(paid.stream().allMatch(t -> t.currency() == Currency.CRYSTALS && t.amount() == 20));
        assertEquals(2, grants(QuestStore.PASS_XP).size());
        Map<UUID, long[]> rows = db.blocking(c -> QuestStore.helpers(c, q.uid));
        assertEquals(3, rows.size(), "every contribution is stored");
        assertTrue(rows.get(helperA)[1] > 0 && rows.get(helperB)[1] > 0);
        assertEquals(0, rows.get(tiny)[1]);
        Payouts.Plan again = Payouts.island(set, q, helpers, contributions, true, Actor.SYSTEM, 4L);
        assertEquals(Payouts.Status.CHANGED, Payouts.commit(runner, again).join());
        assertEquals(2, paid.size());
    }

    @Test
    void deletingAProfileRemovesItsQuests() {
        QuestSet set = stored(QuestKind.DAILY, profile, 2);
        assertEquals(Payouts.Status.OK, pay(set, set.quests.getFirst()));
        db.blockingTransaction(c -> {
            QuestStore.deleteOwner(c, profile);
            return true;
        });
        assertTrue(db.blocking(c -> QuestStore.load(c, QuestKind.DAILY, profile, DAY)).isEmpty());
    }

    @Test
    void grantsMoveOnlyFromTheExpectedState() {
        QuestSet set = stored(QuestKind.DAILY, profile, 1);
        assertEquals(Payouts.Status.OK, pay(set, set.quests.getFirst()));
        QuestStore.GrantRow row = grants(QuestStore.PASS_XP).getFirst();
        assertTrue(db.<Boolean>blockingTransaction(c -> QuestStore.move(c, row.id(), QuestStore.PENDING, QuestStore.SENDING, 1L)));
        assertFalse(db.<Boolean>blockingTransaction(c -> QuestStore.move(c, row.id(), QuestStore.PENDING, QuestStore.SENDING, 1L)),
                "never handed out twice");
    }
}
