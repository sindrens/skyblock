package net.skybornmc.skyblock.achievements;

import java.util.Locale;
import java.util.Optional;

/**
 * What an achievement counts. Stored per <b>profile</b> by {@link #key()} (never rename a key: it is the database value).
 * Every value only goes up:
 * <ul>
 *   <li>{@link Kind#COUNTER}: events add to it (sales, trades, stacked kills, minutes played …). Saved as a delta.</li>
 *   <li>{@link Kind#GAUGE}: the best value seen in a service (skill level, island stat total, best set level …). Saved as
 *       the highest value ({@code GREATEST}), so a season reset of skills never takes progress away.</li>
 * </ul>
 * Where each value comes from: {@link Tracker}.
 */
enum Metric {
    // ------------------------------------------------------------------ counters (events)
    /** Minutes online on this profile (counted once a minute). */
    PLAYTIME_MINUTES("playtime_minutes", Kind.COUNTER, Unit.MINUTES),
    /** Mobs killed from stacks in one batch ({@code StackKillEvent#killed}). */
    STACKED_KILLS("stacked_kills", Kind.COUNTER, Unit.COUNT),
    /** Items sold with {@code /sell}, the satchel or a sell wand (audit row {@code economy/sell}). */
    ITEMS_SOLD("items_sold", Kind.COUNTER, Unit.COUNT),
    /** Money earned by selling (audit row {@code economy/sell}). */
    MONEY_EARNED("money_earned", Kind.COUNTER, Unit.MONEY),
    /** Enchant books applied at the enchanter (audit row {@code item/enchant_apply}). */
    ENCHANTS_APPLIED("enchants_applied", Kind.COUNTER, Unit.COUNT),
    /** Finished secure trades, both sides (audit row {@code trade/trade_complete}). */
    TRADES_COMPLETED("trades_completed", Kind.COUNTER, Unit.COUNT),
    /** Island visits, one per island per day (audit row {@code island/island_visit}). */
    ISLANDS_VISITED("islands_visited", Kind.COUNTER, Unit.COUNT),

    // ------------------------------------------------------------------ gauges (services)
    SKILL_MINING("skill_mining", Kind.GAUGE, Unit.LEVEL),
    SKILL_FORAGING("skill_foraging", Kind.GAUGE, Unit.LEVEL),
    SKILL_COMBAT("skill_combat", Kind.GAUGE, Unit.LEVEL),
    SKILL_FARMING("skill_farming", Kind.GAUGE, Unit.LEVEL),
    SKILL_FISHING("skill_fishing", Kind.GAUGE, Unit.LEVEL),
    /** Sum of the levels of every skill that gives XP now. */
    SKILL_TOTAL("skill_total", Kind.GAUGE, Unit.LEVEL),
    /** Highest single skill level. */
    SKILL_BEST("skill_best", Kind.GAUGE, Unit.LEVEL),
    /** This member's all-time island stats ({@code IslandStatsService}, member key = profile key). */
    ISLAND_BLOCKS_MINED("island_blocks_mined", Kind.GAUGE, Unit.COUNT),
    ISLAND_LOGS_CHOPPED("island_logs_chopped", Kind.GAUGE, Unit.COUNT),
    ISLAND_CROPS_HARVESTED("island_crops_harvested", Kind.GAUGE, Unit.COUNT),
    ISLAND_BLOCKS_PLACED("island_blocks_placed", Kind.GAUGE, Unit.COUNT),
    ISLAND_MOBS_KILLED("island_mobs_killed", Kind.GAUGE, Unit.COUNT),
    /** Unique visitors of the profile's island, all time (island-wide). */
    ISLAND_VISITORS("island_visitors", Kind.GAUGE, Unit.COUNT),
    /** Sets owned on this profile ({@code WardrobeService#ownedSets}). */
    WARDROBE_SETS_OWNED("wardrobe_sets_owned", Kind.GAUGE, Unit.COUNT),
    /** Highest set level on this profile. */
    WARDROBE_BEST_LEVEL("wardrobe_best_level", Kind.GAUGE, Unit.LEVEL),
    /** Highest custom tool level seen in the hotbar (or on a tool level-up). */
    TOOL_BEST_LEVEL("tool_best_level", Kind.GAUGE, Unit.LEVEL),
    /** Highest tool prestige seen. */
    TOOL_BEST_PRESTIGE("tool_best_prestige", Kind.GAUGE, Unit.COUNT),
    /** Highest money balance seen at once ({@code EconomyService#cached}). */
    BALANCE_PEAK("balance_peak", Kind.GAUGE, Unit.MONEY),
    /** Biggest single sale. */
    BEST_SALE("best_sale", Kind.GAUGE, Unit.MONEY),
    /** Most mobs killed from a stack in one batch. */
    BEST_STACK_KILL("best_stack_kill", Kind.GAUGE, Unit.COUNT);

    enum Kind { COUNTER, GAUGE }

    /** How a value is shown in the menu. */
    enum Unit { COUNT, MONEY, MINUTES, LEVEL }

    private final String key;
    private final Kind kind;
    private final Unit unit;

    Metric(String key, Kind kind, Unit unit) {
        this.key = key;
        this.kind = kind;
        this.unit = unit;
    }

    String key() {
        return key;
    }

    Kind kind() {
        return kind;
    }

    Unit unit() {
        return unit;
    }

    boolean counter() {
        return kind == Kind.COUNTER;
    }

    static Optional<Metric> byKey(String key) {
        if (key == null) {
            return Optional.empty();
        }
        String k = key.strip().toLowerCase(Locale.ROOT).replace('-', '_');
        for (Metric m : values()) {
            if (m.key.equals(k)) {
                return Optional.of(m);
            }
        }
        return Optional.empty();
    }
}
