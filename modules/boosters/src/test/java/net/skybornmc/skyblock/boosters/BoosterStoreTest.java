package net.skybornmc.skyblock.boosters;

import net.skybornmc.skyblock.common.booster.BoosterType;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.DatabaseConfig;
import net.skybornmc.skyblock.common.db.Migrations;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static net.skybornmc.skyblock.boosters.BoosterRules.MINUTE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The boosters tables on H2 in MySQL mode (same SQL as MariaDB). */
class BoosterStoreTest {

    private static final long MAX = 180 * MINUTE;

    @TempDir
    Path dir;
    private Database db;
    private final UUID profile = UUID.randomUUID();

    @BeforeEach
    void start() throws Exception {
        DatabaseConfig config = new DatabaseConfig();
        config.type = DatabaseConfig.Type.H2;
        config.h2File = "boosters-test";
        config.poolSize = 2;
        db = new Database(config, dir.toFile(), Logger.getLogger("boosters-test"));
        db.start();
        new Migrations(db, Logger.getLogger("boosters-test")).migrate("boosters", getClass().getClassLoader());
    }

    @AfterEach
    void stop() {
        db.close();
    }

    private BoosterRules.Decision activate(UUID item, BoosterType type, int percent, long add, Map<BoosterType, Long> use) {
        return db.blockingTransaction(c -> BoosterStore.activate(c, profile, item, type, percent, add, MAX, use, 1L));
    }

    private Map<BoosterType, BoosterRules.Run> runs() {
        return db.blocking(c -> BoosterStore.runs(c, profile));
    }

    @Test
    void anItemStartsOnceAndTheSameTypeAddsTime() {
        UUID a = UUID.randomUUID();
        assertEquals(BoosterRules.Outcome.STARTED, activate(a, BoosterType.SELL, 25, 10 * MINUTE, Map.of()).outcome());
        assertEquals(BoosterRules.Outcome.ALREADY_USED, activate(a, BoosterType.SELL, 25, 10 * MINUTE, Map.of()).outcome(),
                "a copy of the same item never starts twice");
        BoosterRules.Decision more = activate(UUID.randomUUID(), BoosterType.SELL, 25, 10 * MINUTE, Map.of(BoosterType.SELL, 3 * MINUTE));
        assertEquals(BoosterRules.Outcome.EXTENDED, more.outcome());
        assertEquals(17 * MINUTE, more.remaining(), "used time is subtracted first, then the new time added");
        assertEquals(17 * MINUTE, runs().get(BoosterType.SELL).remaining());
    }

    @Test
    void refusalsChangeNothingAndKeepTheItemUnused() {
        activate(UUID.randomUUID(), BoosterType.SELL, 25, 10 * MINUTE, Map.of());
        UUID other = UUID.randomUUID();
        assertEquals(BoosterRules.Outcome.OTHER_STRENGTH, activate(other, BoosterType.SELL, 50, 10 * MINUTE, Map.of()).outcome());
        assertEquals(false, db.blocking(c -> BoosterStore.used(c, other)), "a refused item can be started later");
        assertEquals(BoosterRules.Outcome.FULL, activate(UUID.randomUUID(), BoosterType.SELL, 25, 175 * MINUTE, Map.of()).outcome());
        assertEquals(10 * MINUTE, runs().get(BoosterType.SELL).remaining());
        assertEquals(BoosterRules.Outcome.STARTED, activate(other, BoosterType.SKILL_XP, 50, 10 * MINUTE, Map.of()).outcome(),
                "other types run side by side");
    }

    @Test
    void usedTimeIsRelativeAndFinishedRunsAreRemoved() {
        activate(UUID.randomUUID(), BoosterType.CRYSTALS, 50, 10 * MINUTE, Map.of());
        db.blockingTransaction(c -> {
            BoosterStore.applyUse(c, profile, Map.of(BoosterType.CRYSTALS, 4 * MINUTE), 2L);
            return null;
        });
        assertEquals(6 * MINUTE, runs().get(BoosterType.CRYSTALS).remaining());
        db.blockingTransaction(c -> {
            BoosterStore.applyUse(c, profile, Map.of(BoosterType.CRYSTALS, 6 * MINUTE + 500), 3L);
            return null;
        });
        assertTrue(runs().isEmpty(), "at or below 0 the run is gone");
        assertEquals(BoosterRules.Outcome.STARTED, activate(UUID.randomUUID(), BoosterType.CRYSTALS, 100, MINUTE, Map.of()).outcome(),
                "after it ended another strength can start");
    }

    @Test
    void bankedTimeIsCappedAndStartsWhenTheTypeIsFree() {
        db.blockingTransaction(c -> BoosterStore.bank(c, profile, BoosterType.SELL, 50, 10 * MINUTE, 25 * MINUTE, "afk_pool", 1L));
        db.blockingTransaction(c -> BoosterStore.bank(c, profile, BoosterType.SELL, 50, 10 * MINUTE, 25 * MINUTE, "afk_pool", 2L));
        long banked = db.blockingTransaction(c -> BoosterStore.bank(c, profile, BoosterType.SELL, 50, 10 * MINUTE, 25 * MINUTE, "afk_pool", 3L));
        assertEquals(25 * MINUTE, banked, "capped at the giver's limit");
        activate(UUID.randomUUID(), BoosterType.SELL, 100, 5 * MINUTE, Map.of());
        BoosterStore.BankStart blocked = db.blockingTransaction(c -> BoosterStore.startBank(c, profile, BoosterType.SELL, MAX, Map.of(), 4L));
        assertEquals(0, blocked.moved(), "waits while a +100% booster runs");
        BoosterStore.BankStart started = db.blockingTransaction(c -> BoosterStore.startBank(c, profile, BoosterType.SELL, MAX,
                Map.of(BoosterType.SELL, 5 * MINUTE), 5L));
        assertEquals(25 * MINUTE, started.moved(), "the +100% run ended with the used time, so the bank starts");
        assertEquals(50, started.percent());
        assertEquals(0, started.bankLeft());
        assertEquals(new BoosterRules.Run(50, 25 * MINUTE), runs().get(BoosterType.SELL));
        assertTrue(db.blocking(c -> BoosterStore.banks(c, profile)).isEmpty());
    }

    @Test
    void bankStartsOnlyWhatFitsUnderTheMax() {
        activate(UUID.randomUUID(), BoosterType.SELL, 50, 170 * MINUTE, Map.of());
        db.blockingTransaction(c -> BoosterStore.bank(c, profile, BoosterType.SELL, 50, 30 * MINUTE, 0, "afk_pool", 1L));
        BoosterStore.BankStart s = db.blockingTransaction(c -> BoosterStore.startBank(c, profile, BoosterType.SELL, MAX, Map.of(), 2L));
        assertEquals(10 * MINUTE, s.moved());
        assertEquals(20 * MINUTE, s.bankLeft());
        assertEquals(MAX, s.remainingAfter());
        assertEquals(20 * MINUTE, db.blocking(c -> BoosterStore.banks(c, profile)).get(BoosterType.SELL).banked());
    }

    @Test
    void globalBoostersQueueAndRefuseCopies() {
        UUID starter = UUID.randomUUID();
        UUID item = UUID.randomUUID();
        BoosterStore.GlobalStart first = db.blockingTransaction(c -> BoosterStore.startGlobal(c, item, BoosterType.SELL, 50, 30 * MINUTE,
                starter, profile, "Steve", 1, 1L));
        assertEquals(BoosterStore.STATE_ACTIVE, first.row().state());
        assertEquals(BoosterRules.Outcome.ALREADY_USED, db.blockingTransaction(c -> BoosterStore.startGlobal(c, item, BoosterType.SELL, 50,
                30 * MINUTE, starter, profile, "Steve", 1, 2L)).outcome());
        BoosterStore.GlobalStart second = db.blockingTransaction(c -> BoosterStore.startGlobal(c, UUID.randomUUID(), BoosterType.SELL, 50,
                30 * MINUTE, starter, profile, "Steve", 1, 3L));
        assertEquals(BoosterStore.STATE_QUEUED, second.row().state());
        BoosterStore.GlobalStart full = db.blockingTransaction(c -> BoosterStore.startGlobal(c, UUID.randomUUID(), BoosterType.SELL, 50,
                30 * MINUTE, starter, profile, "Steve", 1, 4L));
        assertEquals(BoosterRules.Outcome.FULL, full.outcome());
        assertNull(full.row());
        db.blockingTransaction(c -> {
            BoosterStore.saveGlobal(c, first.row().id(), 5 * MINUTE);
            return null;
        });
        List<BoosterStore.GlobalRow> rows = db.blocking(BoosterStore::globals);
        assertEquals(2, rows.size());
        assertEquals(5 * MINUTE, rows.getFirst().remaining());
        db.blockingTransaction(c -> {
            BoosterStore.endGlobal(c, first.row().id(), 5L);
            BoosterStore.activateGlobal(c, second.row().id(), 5L);
            return null;
        });
        rows = db.blocking(BoosterStore::globals);
        assertEquals(1, rows.size());
        assertEquals(BoosterStore.STATE_ACTIVE, rows.getFirst().state());
    }

    @Test
    void thanksOncePerPlayerWithAGemLimitAndRetry() {
        UUID starter = UUID.randomUUID();
        long id = db.blockingTransaction(c -> BoosterStore.startGlobal(c, UUID.randomUUID(), BoosterType.SELL, 50, 30 * MINUTE, starter,
                profile, "Steve", 5, 1L)).row().id();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID d = UUID.randomUUID();
        assertEquals(BoosterStore.GEM_PENDING, (int) db.blockingTransaction(c -> BoosterStore.thank(c, id, a, true, 2, 2L)));
        assertEquals(-1, (int) db.blockingTransaction(c -> BoosterStore.thank(c, id, a, true, 2, 3L)), "once per booster per player");
        assertEquals(BoosterStore.GEM_NONE, (int) db.blockingTransaction(c -> BoosterStore.thank(c, id, b, false, 2, 4L)), "same IP: no gem");
        assertEquals(BoosterStore.GEM_PENDING, (int) db.blockingTransaction(c -> BoosterStore.thank(c, id, d, true, 2, 5L)));
        assertEquals(BoosterStore.GEM_NONE, (int) db.blockingTransaction(c -> BoosterStore.thank(c, id, UUID.randomUUID(), true, 2, 6L)),
                "the gem limit of the booster is reached");
        assertEquals(2, db.blocking(c -> BoosterStore.unpaid(c, 10)).size());
        db.blockingTransaction(c -> {
            BoosterStore.markPaid(c, id, a);
            return null;
        });
        List<BoosterStore.UnpaidThanks> unpaid = db.blocking(c -> BoosterStore.unpaid(c, 10));
        assertEquals(1, unpaid.size());
        assertEquals(d, unpaid.getFirst().thanker());
        assertEquals(profile, unpaid.getFirst().starterProfile());
        assertEquals(4, db.blocking(c -> BoosterStore.thanked(c, id)).size());
    }

    @Test
    void deletedProfileLosesItsBoosters() {
        activate(UUID.randomUUID(), BoosterType.SELL, 25, 10 * MINUTE, Map.of());
        db.blockingTransaction(c -> BoosterStore.bank(c, profile, BoosterType.SELL, 50, 10 * MINUTE, 0, "afk_pool", 1L));
        db.blockingTransaction(c -> {
            BoosterStore.deleteProfile(c, profile);
            return null;
        });
        assertTrue(runs().isEmpty());
        assertTrue(db.blocking(c -> BoosterStore.banks(c, profile)).isEmpty());
    }
}
