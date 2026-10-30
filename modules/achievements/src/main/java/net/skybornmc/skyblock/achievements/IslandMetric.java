package net.skybornmc.skyblock.achievements;

import java.util.Locale;
import java.util.Optional;

/**
 * What an island achievement counts (RANKS_TITLES_ACHIEVEMENTS §C4). Stored per <b>island</b> by {@link #key()} (never
 * rename a key). Every value only goes up within a season: gauges keep the best value seen, counters add.
 * <ul>
 *   <li>{@link #ISLAND_LEVEL}: the dynamic island level ({@code ProgressionService#islandLevel}).</li>
 *   <li>{@link #CROPS_HARVESTED}, {@link #BLOCKS_MINED}, {@link #LOGS_CHOPPED}, {@link #MOBS_KILLED}: the sum of every
 *       member's all-time island stats (members and former members only, never coop or visitors: the island stats keep
 *       a row per member key).</li>
 *   <li>{@link #VISITORS}: unique visitors, all time (island stats).</li>
 *   <li>{@link #LIKES}: likes received (audit row {@code island/island_like}; the islands module already allows one per
 *       player per day and limits accounts per IP).</li>
 *   <li>{@link #TEAM_ACTIVE}: members active on the same day for at least {@code island.team-active-minutes} each, alts on
 *       the same IP counted once (best day of the season).</li>
 * </ul>
 */
enum IslandMetric {
    ISLAND_LEVEL("island_level", Metric.Kind.GAUGE, Metric.Unit.LEVEL),
    CROPS_HARVESTED("crops_harvested", Metric.Kind.GAUGE, Metric.Unit.COUNT),
    BLOCKS_MINED("blocks_mined", Metric.Kind.GAUGE, Metric.Unit.COUNT),
    LOGS_CHOPPED("logs_chopped", Metric.Kind.GAUGE, Metric.Unit.COUNT),
    MOBS_KILLED("mobs_killed", Metric.Kind.GAUGE, Metric.Unit.COUNT),
    VISITORS("visitors", Metric.Kind.GAUGE, Metric.Unit.COUNT),
    LIKES("likes", Metric.Kind.COUNTER, Metric.Unit.COUNT),
    TEAM_ACTIVE("team_active", Metric.Kind.GAUGE, Metric.Unit.COUNT);

    private final String key;
    private final Metric.Kind kind;
    private final Metric.Unit unit;

    IslandMetric(String key, Metric.Kind kind, Metric.Unit unit) {
        this.key = key;
        this.kind = kind;
        this.unit = unit;
    }

    String key() {
        return key;
    }

    Metric.Unit unit() {
        return unit;
    }

    boolean counter() {
        return kind == Metric.Kind.COUNTER;
    }

    static Optional<IslandMetric> byKey(String key) {
        if (key == null) {
            return Optional.empty();
        }
        String k = key.strip().toLowerCase(Locale.ROOT).replace('-', '_');
        for (IslandMetric m : values()) {
            if (m.key.equals(k)) {
                return Optional.of(m);
            }
        }
        return Optional.empty();
    }
}
