package net.skybornmc.skyblock.islands;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.DatabaseConfig;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.db.Sql;
import net.skybornmc.skyblock.common.island.IslandStat;
import net.skybornmc.skyblock.common.island.IslandStatsService;
import net.skybornmc.skyblock.common.island.IslandStatsService.Board;
import net.skybornmc.skyblock.common.island.IslandStatsService.Period;
import net.skybornmc.skyblock.common.island.IslandStatsService.TopEntry;
import net.skybornmc.skyblock.islands.stats.StatKey;
import net.skybornmc.skyblock.islands.stats.StatsBatch;
import net.skybornmc.skyblock.islands.stats.StatsClock;
import net.skybornmc.skyblock.islands.stats.StatsStore;
import net.skybornmc.skyblock.islands.stats.StatsTracker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Island stats against a real H2 database (MySQL mode): upserts add up, snapshot reads, boards, purge and reset. */
class IslandStatsStoreTest {

    private static final ZoneId OSLO = ZoneId.of("Europe/Oslo");
    private static final int FRIDAY = (int) LocalDate.parse("2026-10-30").toEpochDay();
    private static final int MONDAY = FRIDAY - 4;

    @TempDir
    Path dir;
    private Database db;
    private StatsStore store;
    private UUID solo;
    private UUID team;
    private UUID alex;
    private UUID sam;

    @BeforeEach
    void start() throws Exception {
        DatabaseConfig config = new DatabaseConfig();
        config.type = DatabaseConfig.Type.H2;
        config.h2File = "stats-test";
        config.poolSize = 4;
        db = new Database(config, dir.toFile(), Logger.getLogger("test-db"));
        db.start();
        new Migrations(db, Logger.getLogger("test")).migrate("islands", getClass().getClassLoader());
        store = new StatsStore(db);
        alex = UUID.randomUUID();
        sam = UUID.randomUUID();
        solo = create(alex);
        team = create(sam);
        db.blocking(c -> Sql.update(c, "UPDATE islands SET type = 'team' WHERE id = ?", team));
    }

    private UUID create(UUID owner) {
        IslandStore.CreateResult result = db.blocking(c -> IslandStore.createWithRetry(c, owner, 1, 3, 0, "islands", 1000L));
        return assertInstanceOf(IslandStore.CreateResult.Created.class, result).island().id();
    }

    @AfterEach
    void stop() {
        db.close();
    }

    @Test
    void upsertsAddUpAcrossFlushes() {
        StatKey key = new StatKey(solo, alex, IslandStat.BLOCKS_MINED, FRIDAY);
        store.write(StatsBatch.of(Map.of(key, 5L))).join();
        store.write(StatsBatch.of(Map.of(key, 7L, new StatKey(solo, alex, IslandStat.BLOCKS_MINED, MONDAY), 1L))).join();
        var rows = store.load(solo, FRIDAY).join();
        assertEquals(13L, rows.totals().getFirst().amount());
        assertEquals(12L, rows.daily().getFirst().amount());
        assertEquals(1, rows.weekly().size());
        assertEquals(13L, rows.weekly().getFirst().amount());
        assertEquals(MONDAY, rows.weekly().getFirst().bucket());
    }

    @Test
    void trackerSnapshotAndBoards() {
        AtomicLong now = new AtomicLong(LocalDate.parse("2026-10-30").atTime(12, 0).atZone(OSLO).toInstant().toEpochMilli());
        StatsTracker tracker = new StatsTracker(store, new StatsClock(OSLO, now::get), Logger.getLogger("test"), key -> null);
        tracker.record(solo, alex, IslandStat.MOBS_KILLED, 40);
        tracker.record(team, sam, IslandStat.MOBS_KILLED, 30);
        tracker.record(team, UUID.randomUUID(), IslandStat.MOBS_KILLED, 20);
        tracker.recordIsland(team, IslandStat.VISITORS, 3);
        tracker.flush().join();
        tracker.record(solo, alex, IslandStat.MOBS_KILLED, 2); // unwritten
        IslandStatsService.Snapshot snapshot = tracker.snapshot(solo).join();
        assertEquals(new IslandStatsService.Totals(42, 42, 42, 0), snapshot.totals(IslandStat.MOBS_KILLED));
        IslandStatsService.Snapshot teamSnap = tracker.snapshot(team).join();
        assertEquals(50, teamSnap.totals(IslandStat.MOBS_KILLED).allTime());
        assertEquals(2, teamSnap.members().size()); // the island-wide visitors key is not a member
        assertEquals(3, teamSnap.totals(IslandStat.VISITORS).allTime());

        List<TopEntry> all = tracker.top(IslandStat.MOBS_KILLED, Period.ALL_TIME, Board.ALL, 10).join();
        assertEquals(List.of(new TopEntry(team, 50), new TopEntry(solo, 40)), all);
        assertEquals(List.of(new TopEntry(solo, 40)), tracker.top(IslandStat.MOBS_KILLED, Period.THIS_WEEK, Board.SOLO, 10).join());
        assertEquals(List.of(new TopEntry(team, 50)), tracker.top(IslandStat.MOBS_KILLED, Period.TODAY, Board.TEAM, 10).join());
        assertTrue(tracker.top(IslandStat.MOBS_KILLED, Period.LAST_WEEK, Board.ALL, 10).join().isEmpty());

        // deleted islands leave the boards
        db.blocking(c -> Sql.update(c, "UPDATE islands SET deleted_at = 1 WHERE id = ?", team));
        assertEquals(List.of(new TopEntry(solo, 40)), tracker.top(IslandStat.MOBS_KILLED, Period.ALL_TIME, Board.ALL, 10).join());
        assertEquals(0, tracker.close(5_000));
        assertEquals(42, store.load(solo, FRIDAY).join().totals().getFirst().amount());
    }

    @Test
    void purgeKeepsTotalsAndResetRemovesEverything() {
        store.write(StatsBatch.of(Map.of(
                new StatKey(solo, alex, IslandStat.LOGS_CHOPPED, FRIDAY - 100), 9L,
                new StatKey(solo, alex, IslandStat.LOGS_CHOPPED, FRIDAY), 1L,
                new StatKey(team, sam, IslandStat.LOGS_CHOPPED, FRIDAY), 4L))).join();
        int removed = store.purge(StatsTracker.minDay(FRIDAY, 35), StatsTracker.minWeek(FRIDAY, 4)).join();
        assertEquals(2, removed); // one old day row + one old week row
        assertEquals(10L, store.load(solo, FRIDAY).join().totals().getFirst().amount());
        long dayRows = db.blocking(c -> Sql.queryOne(c, "SELECT COUNT(*) FROM island_stats_daily", rs -> rs.getLong(1)).orElse(0L));
        assertEquals(2, dayRows);

        assertEquals(3, store.reset(solo).join()); // day + week + total row of the solo island
        assertTrue(store.load(solo, FRIDAY).join().totals().isEmpty());
        assertEquals(4L, store.load(team, FRIDAY).join().totals().getFirst().amount());
    }
}
