package net.skybornmc.skyblock.islands.stats;

import net.skybornmc.skyblock.common.island.IslandStat;
import net.skybornmc.skyblock.common.island.IslandStatsService;
import net.skybornmc.skyblock.common.island.IslandStatsService.Snapshot;
import net.skybornmc.skyblock.common.island.IslandStatsService.Totals;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builds a {@link Snapshot} from the stored rows of one island plus its counters that were not written yet (M2.6). Pure.
 * Totals per member are kept apart; island totals add every member key (current, former and island-wide).
 */
public final class SnapshotBuilder {

    /** One stored counter: member key, category, bucket (day, week Monday, or 0 for all-time) and amount. */
    public record Row(UUID member, IslandStat stat, int bucket, long amount) {
    }

    private SnapshotBuilder() {
    }

    /**
     * @param totals  all-time rows
     * @param daily   day rows of {@code today}
     * @param weekly  week rows of this and last week
     * @param pending unwritten counters of the island
     */
    public static Snapshot build(UUID island, int today, List<Row> totals, List<Row> daily, List<Row> weekly,
                                 Map<StatKey, Long> pending) {
        int thisWeek = StatsClock.weekStart(today);
        int lastWeek = thisWeek - 7;
        Map<UUID, Map<IslandStat, long[]>> acc = new HashMap<>();
        for (Row row : totals) {
            cell(acc, row.member(), row.stat())[0] += row.amount();
        }
        for (Row row : daily) {
            if (row.bucket() == today) {
                cell(acc, row.member(), row.stat())[1] += row.amount();
            }
        }
        for (Row row : weekly) {
            if (row.bucket() == thisWeek) {
                cell(acc, row.member(), row.stat())[2] += row.amount();
            } else if (row.bucket() == lastWeek) {
                cell(acc, row.member(), row.stat())[3] += row.amount();
            }
        }
        pending.forEach((key, amount) -> {
            if (!key.island().equals(island) || amount == null || amount <= 0) {
                return;
            }
            long[] c = cell(acc, key.member(), key.stat());
            c[0] += amount;
            if (key.day() == today) {
                c[1] += amount;
            }
            int week = StatsClock.weekStart(key.day());
            if (week == thisWeek) {
                c[2] += amount;
            } else if (week == lastWeek) {
                c[3] += amount;
            }
        });
        Map<IslandStat, Totals> islandTotals = new EnumMap<>(IslandStat.class);
        Map<UUID, Map<IslandStat, Totals>> members = new HashMap<>();
        acc.forEach((member, stats) -> {
            Map<IslandStat, Totals> mine = new EnumMap<>(IslandStat.class);
            stats.forEach((stat, c) -> {
                Totals t = new Totals(c[0], c[1], c[2], c[3]);
                islandTotals.merge(stat, t, Totals::plus);
                mine.put(stat, t);
            });
            if (!IslandStatsService.ISLAND_WIDE.equals(member)) {
                members.put(member, Map.copyOf(mine));
            }
        });
        return new Snapshot(island, today, thisWeek, islandTotals, members);
    }

    private static long[] cell(Map<UUID, Map<IslandStat, long[]>> acc, UUID member, IslandStat stat) {
        return acc.computeIfAbsent(member, k -> new EnumMap<>(IslandStat.class)).computeIfAbsent(stat, k -> new long[4]);
    }
}
