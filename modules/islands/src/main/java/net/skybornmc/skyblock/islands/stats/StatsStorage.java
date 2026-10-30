package net.skybornmc.skyblock.islands.stats;

import net.skybornmc.skyblock.common.island.IslandStat;
import net.skybornmc.skyblock.common.island.IslandStatsService.Board;
import net.skybornmc.skyblock.common.island.IslandStatsService.Period;
import net.skybornmc.skyblock.common.island.IslandStatsService.TopEntry;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Where island stats are kept (M2.6). {@link StatsStore} = the database; tests use their own. */
public interface StatsStorage {

    /** The stored rows of one island for a snapshot. */
    record IslandRows(List<SnapshotBuilder.Row> totals, List<SnapshotBuilder.Row> daily, List<SnapshotBuilder.Row> weekly) {
    }

    /** Adds a batch in ONE transaction (all or nothing). Completes with the rows written. */
    CompletableFuture<Integer> write(StatsBatch batch);

    /** {@link #write}, blocking (shutdown). */
    int writeBlocking(StatsBatch batch) throws Exception;

    /** Deletes every counter of an island (admin reset). Completes with the rows removed. */
    CompletableFuture<Integer> reset(UUID island);

    /** Deletes day rows before {@code minDay} and week rows before {@code minWeek}. Completes with the rows removed. */
    CompletableFuture<Integer> purge(int minDay, int minWeek);

    /** All-time rows, day rows of {@code today} and week rows of this and last week of one island. */
    CompletableFuture<IslandRows> load(UUID island, int today);

    CompletableFuture<List<TopEntry>> top(IslandStat stat, Period period, Board board, int today, int limit);
}
