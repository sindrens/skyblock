package net.skybornmc.skyblock.islands.stats;

import net.skybornmc.skyblock.common.island.IslandStat;

import java.util.UUID;

/**
 * One buffered counter: island, member key ({@link net.skybornmc.skyblock.common.island.IslandStatsService#ISLAND_WIDE}
 * for island-wide counters), category and the day bucket it was earned in (fixed when recorded, so increments just
 * before midnight stay in the old day even if they are written after it).
 */
public record StatKey(UUID island, UUID member, IslandStat stat, int day) {
}
