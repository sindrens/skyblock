package net.skybornmc.skyblock.islands.stats;

import net.skybornmc.skyblock.common.island.IslandStat;
import net.skybornmc.skyblock.common.island.IslandStatsService;
import net.skybornmc.skyblock.common.island.IslandStatsService.Snapshot;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Buffer, batch merging and snapshot building (pure logic of the flush path). */
class StatsBufferTest {

    private static final UUID ISLAND = UUID.randomUUID();
    private static final UUID OTHER = UUID.randomUUID();
    private static final UUID ALEX = UUID.randomUUID();
    private static final UUID SAM = UUID.randomUUID();
    private static final int MONDAY = (int) LocalDate.parse("2026-10-26").toEpochDay();

    @Test
    void addMergesAndIgnoresNonPositive() {
        StatsBuffer buffer = new StatsBuffer();
        StatKey key = new StatKey(ISLAND, ALEX, IslandStat.BLOCKS_MINED, MONDAY);
        buffer.add(key, 3);
        buffer.add(key, 4);
        buffer.add(key, 0);
        buffer.add(key, -10);
        assertEquals(Map.of(key, 7L), buffer.drain());
        assertTrue(buffer.isEmpty());
        assertEquals(Map.of(), buffer.drain());
    }

    @Test
    void failedFlushMergesBackOntoNewRecords() {
        StatsBuffer buffer = new StatsBuffer();
        StatKey key = new StatKey(ISLAND, ALEX, IslandStat.MOBS_KILLED, MONDAY);
        buffer.add(key, 10);
        Map<StatKey, Long> drained = buffer.drain();
        buffer.add(key, 5); // recorded while the flush ran
        buffer.mergeBack(drained); // the flush failed
        assertEquals(Map.of(key, 15L), buffer.drain());
    }

    @Test
    void saturatesInsteadOfOverflowing() {
        StatsBuffer buffer = new StatsBuffer();
        StatKey key = new StatKey(ISLAND, ALEX, IslandStat.MONEY_EARNED, MONDAY);
        buffer.add(key, Long.MAX_VALUE - 1);
        buffer.add(key, 5);
        assertEquals(Long.MAX_VALUE, buffer.drain().get(key));
    }

    @Test
    void discardAndCopyAreperIsland() {
        StatsBuffer buffer = new StatsBuffer();
        StatKey mine = new StatKey(ISLAND, ALEX, IslandStat.BLOCKS_PLACED, MONDAY);
        StatKey theirs = new StatKey(OTHER, SAM, IslandStat.BLOCKS_PLACED, MONDAY);
        buffer.add(mine, 1);
        buffer.add(theirs, 2);
        assertEquals(Map.of(mine, 1L), buffer.copyOf(ISLAND));
        assertEquals(1, buffer.discard(ISLAND));
        assertEquals(Map.of(theirs, 2L), buffer.drain());
    }

    @Test
    void batchSumsWeeksAndTotals() {
        Map<StatKey, Long> drained = Map.of(
                new StatKey(ISLAND, ALEX, IslandStat.BLOCKS_MINED, MONDAY), 5L,
                new StatKey(ISLAND, ALEX, IslandStat.BLOCKS_MINED, MONDAY + 6), 7L,   // Sunday, same week
                new StatKey(ISLAND, ALEX, IslandStat.BLOCKS_MINED, MONDAY - 1), 1L,   // Sunday before: last week
                new StatKey(ISLAND, SAM, IslandStat.BLOCKS_MINED, MONDAY), 2L);
        StatsBatch batch = StatsBatch.of(drained);
        assertEquals(4, batch.daily().size());
        assertEquals(12L, batch.weekly().get(new StatsBatch.WeekKey(ISLAND, ALEX, IslandStat.BLOCKS_MINED, MONDAY)));
        assertEquals(1L, batch.weekly().get(new StatsBatch.WeekKey(ISLAND, ALEX, IslandStat.BLOCKS_MINED, MONDAY - 7)));
        assertEquals(13L, batch.totals().get(new StatsBatch.TotalKey(ISLAND, ALEX, IslandStat.BLOCKS_MINED)));
        assertEquals(2L, batch.totals().get(new StatsBatch.TotalKey(ISLAND, SAM, IslandStat.BLOCKS_MINED)));
        assertEquals(4 + 3 + 2, batch.rows());
        assertTrue(StatsBatch.of(Map.of()).isEmpty());
    }

    @Test
    void snapshotAddsPendingToStoredRows() {
        int today = MONDAY + 2; // Wednesday
        List<SnapshotBuilder.Row> totals = List.of(
                new SnapshotBuilder.Row(ALEX, IslandStat.BLOCKS_MINED, 0, 100),
                new SnapshotBuilder.Row(IslandStatsService.ISLAND_WIDE, IslandStat.VISITORS, 0, 9));
        List<SnapshotBuilder.Row> daily = List.of(new SnapshotBuilder.Row(ALEX, IslandStat.BLOCKS_MINED, today, 10));
        List<SnapshotBuilder.Row> weekly = List.of(
                new SnapshotBuilder.Row(ALEX, IslandStat.BLOCKS_MINED, MONDAY, 30),
                new SnapshotBuilder.Row(ALEX, IslandStat.BLOCKS_MINED, MONDAY - 7, 70));
        Map<StatKey, Long> pending = Map.of(
                new StatKey(ISLAND, ALEX, IslandStat.BLOCKS_MINED, today), 4L,
                new StatKey(ISLAND, SAM, IslandStat.BLOCKS_MINED, MONDAY - 1), 6L,   // Sunday before, not flushed yet
                new StatKey(OTHER, SAM, IslandStat.BLOCKS_MINED, today), 1000L);   // other island: ignored
        Snapshot s = SnapshotBuilder.build(ISLAND, today, totals, daily, weekly, pending);
        assertEquals(MONDAY, s.weekStart());
        assertEquals(new IslandStatsService.Totals(104, 14, 34, 70), s.member(ALEX, IslandStat.BLOCKS_MINED));
        assertEquals(new IslandStatsService.Totals(6, 0, 0, 6), s.member(SAM, IslandStat.BLOCKS_MINED));
        assertEquals(new IslandStatsService.Totals(110, 14, 34, 76), s.totals(IslandStat.BLOCKS_MINED));
        assertEquals(9, s.totals(IslandStat.VISITORS).allTime());
        assertFalse(s.members().containsKey(IslandStatsService.ISLAND_WIDE));
        assertTrue(s.totals(IslandStat.KOTH_TIME).isZero());
    }
}
