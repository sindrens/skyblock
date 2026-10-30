package net.skybornmc.skyblock.common.island;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Island stats (M2.6), published by the islands module: {@code core.services().optional(IslandStatsService.class)}.
 * Counters per island and per member (profile key) and category, kept in daily and weekly buckets (days and Monday-based
 * weeks in the server time zone, Europe/Oslo) plus all-time totals. Foundation for the island top (Phase 5), missions
 * and achievements.
 *
 * <p>Recording is O(1) in memory and never touches the database: counters are written in batches every few seconds
 * and on shutdown (a crash loses at most one flush interval, nothing is ever counted twice). Reads include the counters
 * that were not written yet ({@link #snapshot}); leaderboards ({@link #top}) read the database only and may lag by one
 * flush interval.</p>
 */
public interface IslandStatsService {

    /** Member key for island-wide counters that no member earned (generator output, visitors). */
    UUID ISLAND_WIDE = new UUID(0L, 0L);

    /** A time window. */
    enum Period { TODAY, THIS_WEEK, LAST_WEEK, ALL_TIME }

    /** Which islands a leaderboard ranks (solo and team islands have separate boards, DESIGN §3.2). */
    enum Board { ALL, SOLO, TEAM }

    /** One counter in every window. */
    record Totals(long allTime, long today, long thisWeek, long lastWeek) {

        public static final Totals ZERO = new Totals(0, 0, 0, 0);

        public long get(Period period) {
            return switch (period) {
                case TODAY -> today;
                case THIS_WEEK -> thisWeek;
                case LAST_WEEK -> lastWeek;
                case ALL_TIME -> allTime;
            };
        }

        public Totals plus(Totals other) {
            return new Totals(allTime + other.allTime, today + other.today, thisWeek + other.thisWeek, lastWeek + other.lastWeek);
        }

        public boolean isZero() {
            return allTime == 0 && today == 0 && thisWeek == 0 && lastWeek == 0;
        }
    }

    /**
     * All stats of one island at one moment.
     *
     * @param today     the day bucket (epoch day in the server zone) the values were read for
     * @param weekStart the week bucket (epoch day of that week's Monday)
     * @param island    island totals per category (members, former members and island-wide counters together)
     * @param members   per member key (current and former members; never {@link #ISLAND_WIDE}) and category
     */
    record Snapshot(UUID islandId, int today, int weekStart, Map<IslandStat, Totals> island,
                    Map<UUID, Map<IslandStat, Totals>> members) {

        public Snapshot {
            island = Map.copyOf(island);
            members = Map.copyOf(members);
        }

        public static Snapshot empty(UUID islandId, int today, int weekStart) {
            return new Snapshot(islandId, today, weekStart, new EnumMap<>(IslandStat.class), Map.of());
        }

        public Totals totals(IslandStat stat) {
            return island.getOrDefault(stat, Totals.ZERO);
        }

        public Totals member(UUID memberKey, IslandStat stat) {
            Map<IslandStat, Totals> of = members.get(memberKey);
            return of == null ? Totals.ZERO : of.getOrDefault(stat, Totals.ZERO);
        }
    }

    /** One leaderboard row. */
    record TopEntry(UUID islandId, long amount) {
    }

    /**
     * Adds {@code amount} to a counter of the island (any thread, O(1), no database call). Ignored when the amount is not
     * positive or an argument is null. The caller decides who counts: only stored members (never coop players or
     * visitors, REVIEW A4) — {@link #recordFor} checks that for you.
     *
     * @param memberKey the member's profile key, or {@link #ISLAND_WIDE}
     */
    void record(UUID islandId, UUID memberKey, IslandStat stat, long amount);

    /** {@link #record} for an island-wide counter. */
    default void recordIsland(UUID islandId, IslandStat stat, long amount) {
        record(islandId, ISLAND_WIDE, stat, amount);
    }

    /**
     * Adds {@code amount} for a player on their own island: {@code playerOrProfileKey} is an online player's UUID (their
     * active profile) or a profile key. Does nothing (false) when that profile has no island or is not a stored member.
     * Main thread. Example (sell system): {@code stats.recordFor(player.getUniqueId(), IslandStat.MONEY_EARNED, paid)}.
     */
    boolean recordFor(UUID playerOrProfileKey, IslandStat stat, long amount);

    /** Declares that a module feeds {@code stat}; until then {@code /is stats} shows "—" for it. */
    void source(IslandStat stat);

    boolean hasSource(IslandStat stat);

    /** The current day bucket (epoch day in the server zone). */
    int today();

    /** The current week bucket (epoch day of this week's Monday in the server zone). */
    int weekStart();

    /** Everything about one island, unwritten counters included. Completes off the main thread. */
    CompletableFuture<Snapshot> snapshot(UUID islandId);

    /**
     * Islands ranked by one category in one window, highest first; deleted islands are left out. Reads the database only
     * (lags by up to one flush interval). Completes off the main thread.
     */
    CompletableFuture<List<TopEntry>> top(IslandStat stat, Period period, Board board, int limit);
}
