package net.skybornmc.skyblock.rewards;

import net.skybornmc.skyblock.common.audit.Actor;
import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.DatabaseConfig;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.economy.Transaction;
import net.skybornmc.skyblock.common.economy.TxReason;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Claims on H2 in MySQL mode (same SQL as MariaDB): idempotent, crash-safe, separate per profile. */
class RewardStoreTest {

    private static final int LEN = 30;

    @TempDir
    Path dir;
    private Database db;
    private final UUID player = UUID.randomUUID();
    private final UUID profileA = UUID.randomUUID();
    private final UUID profileB = UUID.randomUUID();
    /** Ledger transactions that "committed" (the test runner ignores the economy and records them). */
    private final List<Transaction> paid = new CopyOnWriteArrayList<>();
    private ClaimEngine.Runner runner;

    @BeforeEach
    void start() throws Exception {
        DatabaseConfig config = new DatabaseConfig();
        config.type = DatabaseConfig.Type.H2;
        config.h2File = "rewards-test";
        config.poolSize = 4;
        db = new Database(config, dir.toFile(), Logger.getLogger("rewards-test"));
        db.start();
        new Migrations(db, Logger.getLogger("rewards-test")).migrate("rewards", getClass().getClassLoader());
        runner = (txs, guard) -> ClaimEngine.local(db, guard).thenApply(r -> {
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

    private Optional<DailyRules.State> state(UUID profile) {
        return db.blocking(c -> RewardStore.calendar(c, profile));
    }

    private static Payout payout(String line) {
        RewardSpec spec = RewardSpec.parse(line, p -> { throw new AssertionError(p); }).orElseThrow();
        return new Payout().add(spec, new Payout.Context(1, new long[]{100}, 1.3, 30, s -> true, r -> List.of("swiftness"), new Random(1)));
    }

    /** One claim as the module does it: read, plan, commit. */
    private CompletableFuture<ClaimEngine.Status> claim(UUID profile, int today, Optional<DailyRules.State> before, String line) {
        DailyRules.Claim c = DailyRules.claim(before.orElse(DailyRules.State.NONE), today, LEN).orElseThrow();
        Payout payout = payout(line);
        String key = "daily:" + profile + ":" + today;
        List<Transaction> txs = ClaimEngine.transactions(payout, profile, TxReason.DAILY_REWARD, key, Actor.SYSTEM, Map.of("day", c.day()));
        List<RewardStore.GrantRow> rows = ClaimEngine.grantRows(payout, profile, player, "daily", key, 1L);
        return ClaimEngine.commit(runner, txs, ClaimEngine.dailyGuard(profile, player, before, c, rows, 1L));
    }

    @Test
    void aClaimWritesTheCalendarTheClaimRowAndPendingGrants() {
        assertEquals(ClaimEngine.Status.OK, claim(profileA, 100, Optional.empty(), "key ember 2").join());
        DailyRules.State s = state(profileA).orElseThrow();
        assertEquals(new DailyRules.State(1, 100, 1, 1, 0, DailyRules.NEVER, 1), s);
        assertEquals(1, (int) db.blocking(c -> RewardStore.claimCount(c, profileA)));
        List<RewardStore.GrantRow> grants = db.blocking(c -> RewardStore.grants(c, RewardStore.PENDING, 10));
        assertEquals(1, grants.size());
        assertEquals(Payout.KEY, grants.getFirst().kind());
        assertEquals("ember", grants.getFirst().ref());
        assertEquals(2, grants.getFirst().amount());
    }

    @Test
    void currenciesGetAnIdempotencyKeyPerClaimAndCurrency() {
        assertEquals(ClaimEngine.Status.OK, claim(profileA, 100, Optional.empty(), "money 3").join());
        assertEquals(1, paid.size());
        assertEquals("daily:" + profileA + ":100:money", paid.getFirst().idempotencyKey());
        assertEquals(300, paid.getFirst().amount());
    }

    @Test
    void aSecondClaimWithTheSameStateIsRefused() {
        assertEquals(ClaimEngine.Status.OK, claim(profileA, 100, Optional.empty(), "money 3").join());
        // A replay (double click, second server, retry) with the state it read before: nothing happens.
        assertEquals(ClaimEngine.Status.CHANGED, claim(profileA, 100, Optional.empty(), "money 3").join());
        assertEquals(1, paid.size());
        assertEquals(1, state(profileA).orElseThrow().total());
        // And with the state after the claim the rules already say "claimed today".
        assertTrue(DailyRules.claim(state(profileA).orElseThrow(), 100, LEN).isEmpty());
    }

    @Test
    void parallelClaimsPayExactlyOnce() {
        List<CompletableFuture<ClaimEngine.Status>> runs = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            runs.add(claim(profileA, 100, Optional.empty(), "crystals 20"));
        }
        long ok = runs.stream().map(CompletableFuture::join).filter(s -> s == ClaimEngine.Status.OK).count();
        assertEquals(1, ok);
        assertEquals(1, paid.size());
        assertEquals(1, (int) db.blocking(c -> RewardStore.claimCount(c, profileA)));
    }

    @Test
    void theClaimRowBlocksASecondClaimOnTheSameDate() {
        assertEquals(ClaimEngine.Status.OK, claim(profileA, 100, Optional.empty(), "money 3").join());
        // Even if the calendar check were passed (a bug), the claim row's key makes the transaction fail as a whole.
        DailyRules.State s = state(profileA).orElseThrow();
        DailyRules.State forged = new DailyRules.State(s.calendarDay(), 99, s.streak(), s.best(), 0, -1, s.total());
        db.blocking(c -> RewardStore.saveCalendar(c, profileA, Optional.of(s), forged, 2L));
        assertEquals(ClaimEngine.Status.ERROR, claim(profileA, 100, Optional.of(forged), "money 3").join());
        assertEquals(1, paid.size());
        assertEquals(forged, state(profileA).orElseThrow(), "rolled back");
    }

    @Test
    void nextDaysAndProfilesAreSeparate() {
        assertEquals(ClaimEngine.Status.OK, claim(profileA, 100, Optional.empty(), "money 3").join());
        assertEquals(ClaimEngine.Status.OK, claim(profileA, 101, state(profileA), "money 3").join());
        assertEquals(2, state(profileA).orElseThrow().calendarDay());
        // Profile B (same player) has its own calendar: day 1, its own streak.
        assertTrue(state(profileB).isEmpty());
        assertEquals(ClaimEngine.Status.OK, claim(profileB, 101, Optional.empty(), "money 3").join());
        assertEquals(1, state(profileB).orElseThrow().calendarDay());
        assertEquals(2, state(profileA).orElseThrow().streak());
        // Skipping a day on A: calendar continues, streak restarts.
        assertEquals(ClaimEngine.Status.OK, claim(profileA, 103, state(profileA), "money 3").join());
        DailyRules.State a = state(profileA).orElseThrow();
        assertEquals(3, a.calendarDay());
        assertEquals(1, a.streak());
        assertEquals(2, a.lostStreak());
        // Deleting B removes only B.
        db.blocking(c -> {
            RewardStore.deleteProfile(c, profileB);
            return null;
        });
        assertTrue(state(profileB).isEmpty());
        assertTrue(state(profileA).isPresent());
    }

    @Test
    void grantsAreTakenOnlyOnce() {
        assertEquals(ClaimEngine.Status.OK, claim(profileA, 100, Optional.empty(), "scroll").join());
        RewardStore.GrantRow row = db.blocking(c -> RewardStore.grants(c, RewardStore.PENDING, 10)).getFirst();
        assertTrue(db.<Boolean>blocking(c -> RewardStore.move(c, row.id(), RewardStore.PENDING, RewardStore.SENDING, 2L)));
        assertFalse(db.<Boolean>blocking(c -> RewardStore.move(c, row.id(), RewardStore.PENDING, RewardStore.SENDING, 2L)),
                "a second hand-out of the same row is impossible");
        assertTrue(db.<Boolean>blocking(c -> RewardStore.move(c, row.id(), RewardStore.SENDING, RewardStore.DELIVERED, 3L)));
        assertEquals(Optional.of(RewardStore.DELIVERED), db.blocking(c -> RewardStore.grantState(c, row.id())));
        assertEquals(1, (int) db.blocking(c -> RewardStore.pruneDelivered(c, 10L)));
    }

    @Test
    void playtimeAddsRelativelyAndPaysMilestonesOnce() {
        PlaytimeRules.Row row = db.blocking(c -> RewardStore.addSeconds(c, profileA, 3_000, 1L));
        assertEquals(new PlaytimeRules.Row(3_000, 0, 0, 0), row);
        row = db.blocking(c -> RewardStore.addSeconds(c, profileA, 700, 2L));
        assertEquals(3_700, row.activeSeconds());
        PlaytimeRules.Due due = PlaytimeRules.due(row, 10, new java.util.TreeMap<>(), 24);
        assertEquals(1, due.hours());
        PlaytimeRules.Row before = row;
        Payout p = new Payout().crystals(20);
        List<RewardStore.GrantRow> none = List.of();
        String key = "playtime:" + profileA + ":1:0:0";
        List<Transaction> txs = ClaimEngine.transactions(p, profileA, TxReason.PLAYTIME_REWARD, key, Actor.SYSTEM, Map.of());
        assertEquals(ClaimEngine.Status.OK, ClaimEngine.commit(runner, txs, ClaimEngine.playtimeGuard(profileA, before, due.after(), none, 3L)).join());
        assertEquals(ClaimEngine.Status.CHANGED, ClaimEngine.commit(runner, txs, ClaimEngine.playtimeGuard(profileA, before, due.after(), none, 3L)).join());
        assertEquals(1, paid.size());
        assertEquals(new PlaytimeRules.Row(3_700, 1, 0, 0), db.blocking(c -> RewardStore.playtime(c, profileA)));
        // Profile B counts separately.
        assertEquals(PlaytimeRules.Row.NONE, db.blocking(c -> RewardStore.playtime(c, profileB)));
    }
}
