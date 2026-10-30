package net.skybornmc.skyblock.islands.stats;

import net.skybornmc.skyblock.common.island.IslandStat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * One flush, merged per table (M2.6): the drained day counters, the same counters summed per week bucket and per
 * all-time counter. Pure; written by {@link StatsStore#write} in one transaction.
 */
public record StatsBatch(Map<StatKey, Long> daily, Map<WeekKey, Long> weekly, Map<TotalKey, Long> totals) {

    /** A weekly counter (week = epoch day of the Monday). */
    public record WeekKey(UUID island, UUID member, IslandStat stat, int week) {
    }

    /** An all-time counter. */
    public record TotalKey(UUID island, UUID member, IslandStat stat) {
    }

    public static StatsBatch of(Map<StatKey, Long> drained) {
        Map<StatKey, Long> daily = new HashMap<>();
        Map<WeekKey, Long> weekly = new HashMap<>();
        Map<TotalKey, Long> totals = new HashMap<>();
        drained.forEach((key, amount) -> {
            if (amount == null || amount <= 0) {
                return;
            }
            daily.merge(key, amount, StatsBuffer::saturatedAdd);
            weekly.merge(new WeekKey(key.island(), key.member(), key.stat(), StatsClock.weekStart(key.day())), amount,
                    StatsBuffer::saturatedAdd);
            totals.merge(new TotalKey(key.island(), key.member(), key.stat()), amount, StatsBuffer::saturatedAdd);
        });
        return new StatsBatch(Map.copyOf(daily), Map.copyOf(weekly), Map.copyOf(totals));
    }

    public boolean isEmpty() {
        return daily.isEmpty();
    }

    /** Rows written by this batch (all three tables). */
    public int rows() {
        return daily.size() + weekly.size() + totals.size();
    }
}
