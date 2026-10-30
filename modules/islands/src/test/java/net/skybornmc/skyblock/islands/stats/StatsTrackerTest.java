package net.skybornmc.skyblock.islands.stats;

import net.skybornmc.skyblock.common.island.IslandStat;
import net.skybornmc.skyblock.common.island.IslandStatsService;
import net.skybornmc.skyblock.common.island.IslandStatsService.Board;
import net.skybornmc.skyblock.common.island.IslandStatsService.Period;
import net.skybornmc.skyblock.common.island.IslandStatsService.TopEntry;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Flush/merge logic of the tracker against an in-memory storage that can fail or hang on purpose. */
class StatsTrackerTest {

    private static final ZoneId OSLO = ZoneId.of("Europe/Oslo");
    private static final UUID ISLAND = UUID.randomUUID();
    private static final UUID ALEX = UUID.randomUUID();

    /** Totals per (island, member, stat) as the database would hold them; writes can be failed or held. */
    static final class FakeStorage implements StatsStorage {
        final Map<StatsBatch.TotalKey, Long> totals = new HashMap<>();
        final Map<StatKey, Long> daily = new HashMap<>();
        final List<String> calls = new ArrayList<>();
        int failNext;
        CompletableFuture<Void> hold;

        @Override
        public synchronized CompletableFuture<Integer> write(StatsBatch batch) {
            calls.add("write");
            if (failNext > 0) {
                failNext--;
                return CompletableFuture.failedFuture(new java.sql.SQLException("database down"));
            }
            CompletableFuture<Void> gate = hold == null ? CompletableFuture.completedFuture(null) : hold;
            return gate.thenApply(v -> {
                synchronized (this) {
                    batch.totals().forEach((k, v2) -> totals.merge(k, v2, Long::sum));
                    batch.daily().forEach((k, v2) -> daily.merge(k, v2, Long::sum));
                    return batch.rows();
                }
            });
        }

        @Override
        public synchronized int writeBlocking(StatsBatch batch) throws Exception {
            calls.add("writeBlocking");
            if (failNext > 0) {
                failNext--;
                throw new java.sql.SQLException("database down");
            }
            batch.totals().forEach((k, v) -> totals.merge(k, v, Long::sum));
            return batch.rows();
        }

        @Override
        public synchronized CompletableFuture<Integer> reset(UUID island) {
            calls.add("reset");
            totals.keySet().removeIf(k -> k.island().equals(island));
            daily.keySet().removeIf(k -> k.island().equals(island));
            return CompletableFuture.completedFuture(1);
        }

        @Override
        public CompletableFuture<Integer> purge(int minDay, int minWeek) {
            calls.add("purge " + minDay + " " + minWeek);
            return CompletableFuture.completedFuture(0);
        }

        @Override
        public synchronized CompletableFuture<IslandRows> load(UUID island, int today) {
            calls.add("load");
            List<SnapshotBuilder.Row> rows = new ArrayList<>();
            totals.forEach((k, v) -> {
                if (k.island().equals(island)) {
                    rows.add(new SnapshotBuilder.Row(k.member(), k.stat(), 0, v));
                }
            });
            List<SnapshotBuilder.Row> days = new ArrayList<>();
            daily.forEach((k, v) -> {
                if (k.island().equals(island) && k.day() == today) {
                    days.add(new SnapshotBuilder.Row(k.member(), k.stat(), k.day(), v));
                }
            });
            return CompletableFuture.completedFuture(new IslandRows(rows, days, List.of()));
        }

        @Override
        public CompletableFuture<List<TopEntry>> top(IslandStat stat, Period period, Board board, int today, int limit) {
            calls.add("top");
            return CompletableFuture.completedFuture(List.of());
        }

        synchronized long total(IslandStat stat) {
            return totals.getOrDefault(new StatsBatch.TotalKey(ISLAND, ALEX, stat), 0L);
        }
    }

    private final AtomicLong now = new AtomicLong(LocalDate.parse("2026-10-30").atTime(12, 0).atZone(OSLO).toInstant().toEpochMilli());
    private final FakeStorage storage = new FakeStorage();
    private final StatsTracker tracker = new StatsTracker(storage, new StatsClock(OSLO, now::get), Logger.getLogger("test"),
            key -> key.equals(ALEX) ? new StatsTracker.Target(ISLAND, ALEX) : null);

    @Test
    void flushWritesOnceAndEmptiesTheBuffer() {
        tracker.record(ISLAND, ALEX, IslandStat.BLOCKS_MINED, 3);
        tracker.record(ISLAND, ALEX, IslandStat.BLOCKS_MINED, 2);
        tracker.flush().join();
        assertEquals(5, storage.total(IslandStat.BLOCKS_MINED));
        assertEquals(0, tracker.pendingCounters());
        tracker.flush().join(); // nothing new: no write
        assertEquals(1, storage.calls.stream().filter("write"::equals).count());
        assertEquals(5, storage.total(IslandStat.BLOCKS_MINED));
    }

    @Test
    void failedFlushIsRetriedWithoutDoubleCounting() {
        tracker.record(ISLAND, ALEX, IslandStat.MOBS_KILLED, 10);
        storage.failNext = 1;
        tracker.flush().handle((r, e) -> null).join();
        assertEquals(0, storage.total(IslandStat.MOBS_KILLED));
        assertEquals(1, tracker.pendingCounters()); // back in the buffer
        tracker.record(ISLAND, ALEX, IslandStat.MOBS_KILLED, 1);
        tracker.flush().join();
        assertEquals(11, storage.total(IslandStat.MOBS_KILLED));
        tracker.flush().join();
        assertEquals(11, storage.total(IslandStat.MOBS_KILLED));
    }

    @Test
    void onlyOneFlushAtATime() {
        storage.hold = new CompletableFuture<>();
        tracker.record(ISLAND, ALEX, IslandStat.BLOCKS_PLACED, 1);
        CompletableFuture<Integer> first = tracker.flush();
        tracker.record(ISLAND, ALEX, IslandStat.BLOCKS_PLACED, 1);
        assertEquals(0, tracker.flush().join()); // skipped: the first is still running
        assertEquals(1, tracker.pendingCounters());
        storage.hold.complete(null);
        first.join();
        storage.hold = null;
        tracker.flush().join();
        assertEquals(2, storage.total(IslandStat.BLOCKS_PLACED));
    }

    @Test
    void snapshotCountsUnwrittenAndInFlightCountersExactlyOnce() {
        storage.hold = new CompletableFuture<>();
        tracker.record(ISLAND, ALEX, IslandStat.LOGS_CHOPPED, 4);
        tracker.flush(); // in flight (held)
        tracker.record(ISLAND, ALEX, IslandStat.LOGS_CHOPPED, 1); // still in the buffer
        CompletableFuture<IslandStatsService.Snapshot> snapshot = tracker.snapshot(ISLAND);
        assertFalse(snapshot.isDone()); // waits for the in-flight flush
        storage.hold.complete(null);
        assertEquals(5, snapshot.join().member(ALEX, IslandStat.LOGS_CHOPPED).allTime());
        assertEquals(5, snapshot.join().totals(IslandStat.LOGS_CHOPPED).today());
    }

    @Test
    void recordedDayIsKeptAcrossMidnight() {
        now.set(LocalDate.parse("2026-10-30").atTime(23, 59, 59).atZone(OSLO).toInstant().toEpochMilli());
        tracker.record(ISLAND, ALEX, IslandStat.CROPS_HARVESTED, 7);
        now.addAndGet(2_000); // past midnight before the flush
        tracker.flush().join();
        int friday = (int) LocalDate.parse("2026-10-30").toEpochDay();
        assertEquals(7L, storage.daily.get(new StatKey(ISLAND, ALEX, IslandStat.CROPS_HARVESTED, friday)));
        IslandStatsService.Snapshot saturday = tracker.snapshot(ISLAND).join();
        assertEquals(0, saturday.totals(IslandStat.CROPS_HARVESTED).today()); // it's Saturday now
        assertEquals(7, saturday.totals(IslandStat.CROPS_HARVESTED).allTime());
    }

    @Test
    void resetDropsPendingAndRunsAfterEarlierFlushes() {
        tracker.record(ISLAND, ALEX, IslandStat.BLOCKS_MINED, 5);
        tracker.flush().join();
        tracker.record(ISLAND, ALEX, IslandStat.BLOCKS_MINED, 3); // unwritten
        tracker.reset(ISLAND).join();
        tracker.flush().join();
        assertEquals(0, storage.total(IslandStat.BLOCKS_MINED));
        assertEquals(0, tracker.pendingCounters());
    }

    @Test
    void recordForOnlyCountsMembersAndIslandWideStatsGoToTheIsland() {
        assertTrue(tracker.recordFor(ALEX, IslandStat.MONEY_EARNED, 250));
        assertFalse(tracker.recordFor(UUID.randomUUID(), IslandStat.MONEY_EARNED, 250));
        assertTrue(tracker.recordFor(ALEX, IslandStat.VISITORS, 1));
        tracker.flush().join();
        assertEquals(250, storage.total(IslandStat.MONEY_EARNED));
        assertEquals(1L, storage.totals.get(new StatsBatch.TotalKey(ISLAND, IslandStatsService.ISLAND_WIDE, IslandStat.VISITORS)));
    }

    @Test
    void disabledOrInvalidRecordsAreIgnored() {
        tracker.record(ISLAND, ALEX, IslandStat.BLOCKS_MINED, 0);
        tracker.record(null, ALEX, IslandStat.BLOCKS_MINED, 1);
        tracker.record(ISLAND, null, IslandStat.BLOCKS_MINED, 1);
        tracker.enabled(false);
        tracker.record(ISLAND, ALEX, IslandStat.BLOCKS_MINED, 1);
        assertEquals(0, tracker.pendingCounters());
    }

    @Test
    void closeWritesTheRestBlockingAndIgnoresLaterRecords() {
        tracker.record(ISLAND, ALEX, IslandStat.PLAYTIME, 60);
        assertEquals(0, tracker.close(1_000));
        assertEquals(60, storage.total(IslandStat.PLAYTIME));
        assertTrue(storage.calls.contains("writeBlocking"));
        tracker.record(ISLAND, ALEX, IslandStat.PLAYTIME, 60);
        assertEquals(0, tracker.pendingCounters());
    }

    @Test
    void closeReportsCountersItCouldNotSave() {
        tracker.record(ISLAND, ALEX, IslandStat.PLAYTIME, 60);
        storage.failNext = 1;
        assertEquals(1, tracker.close(1_000));
    }

    @Test
    void sourcesAndPurgeBounds() {
        assertFalse(tracker.hasSource(IslandStat.KOTH_TIME));
        tracker.source(IslandStat.KOTH_TIME);
        assertTrue(tracker.hasSource(IslandStat.KOTH_TIME));
        tracker.purge(35, 104).join();
        int today = (int) LocalDate.parse("2026-10-30").toEpochDay();
        assertTrue(storage.calls.contains("purge " + (today - 34) + " " + (StatsClock.weekStart(today) - 7 * 103)));
    }
}
