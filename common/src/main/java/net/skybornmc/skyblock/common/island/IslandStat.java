package net.skybornmc.skyblock.common.island;

import java.util.Locale;
import java.util.Optional;

/**
 * Island stat categories (M2.6, DESIGN §3 "/is stats"). Stored by {@link #key()} (never rename a key: it is the database
 * value). A category shows "—" in {@code /is stats} until some module feeds it ({@link IslandStatsService#source}).
 */
public enum IslandStat {
    /** Blocks broken on the island that are not logs or crops (generator stone, ores …). */
    BLOCKS_MINED("blocks_mined", Unit.COUNT, true),
    /** Logs broken on the island. */
    LOGS_CHOPPED("logs_chopped", Unit.COUNT, true),
    /** Ripe crops and harvest blocks (sugar cane, melons …) broken on the island. */
    CROPS_HARVESTED("crops_harvested", Unit.COUNT, true),
    /** Blocks placed on the island. */
    BLOCKS_PLACED("blocks_placed", Unit.COUNT, true),
    /** Blocks made by cobblestone/stone/basalt generators on the island (island-wide, nobody made them). */
    GENERATOR_OUTPUT("generator_output", Unit.COUNT, false),
    /** Mobs killed on the island, stacked batch kills included. */
    MOBS_KILLED("mobs_killed", Unit.COUNT, true),
    /** Items sold by members (fed by the sell system). */
    ITEMS_SOLD("items_sold", Unit.COUNT, true),
    /** Money members earned by selling (fed by the sell system), whole dollars. */
    MONEY_EARNED("money_earned", Unit.MONEY, true),
    /** Unique visitors per day (island-wide). */
    VISITORS("visitors", Unit.COUNT, false),
    /** Seconds members spent on their own island. */
    PLAYTIME("playtime", Unit.SECONDS, true),
    /** Seconds members held a KOTH hill (Phase 8). */
    KOTH_TIME("koth_time", Unit.SECONDS, true),
    /** Bosses killed by members (Phase 7). */
    BOSSES_KILLED("bosses_killed", Unit.COUNT, true);

    /** How a value is shown. */
    public enum Unit { COUNT, MONEY, SECONDS }

    private final String key;
    private final Unit unit;
    private final boolean perMember;

    IslandStat(String key, Unit unit, boolean perMember) {
        this.key = key;
        this.unit = unit;
        this.perMember = perMember;
    }

    /** Database and config key, e.g. {@code blocks_mined}. */
    public String key() {
        return key;
    }

    public Unit unit() {
        return unit;
    }

    /** False for island-wide categories that nobody in particular earns (generator output, visitors). */
    public boolean perMember() {
        return perMember;
    }

    /** The category with this key (case-insensitive, {@code -} accepted for {@code _}). */
    public static Optional<IslandStat> byKey(String key) {
        if (key == null) {
            return Optional.empty();
        }
        String k = key.strip().toLowerCase(Locale.ROOT).replace('-', '_');
        for (IslandStat stat : values()) {
            if (stat.key.equals(k)) {
                return Optional.of(stat);
            }
        }
        return Optional.empty();
    }
}
