package net.skybornmc.skyblock.tools;

import net.skybornmc.skyblock.common.tools.ToolInfo;
import net.skybornmc.skyblock.common.tools.ToolKind;
import net.skybornmc.skyblock.common.tools.ToolPerk;
import net.skybornmc.skyblock.common.tools.ToolSlots;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * The full state of one custom tool, as stored on the item (persistent data) and mirrored in the {@code tools} table.
 * Immutable: every change returns a copy. All progress rules (XP, prestige, upgrades) are in {@link ToolProgress}.
 *
 * @param prestigeSeason season of the last prestige (only meaningful when {@code prestige > 0})
 * @param stats          lifetime counters, see {@link #STAT_BLOCKS} etc.
 */
record ToolData(UUID id, ToolKind kind, int tier, int level, double xp, int prestige, int prestigeSeason,
                List<ToolPerk> perks, String skin, String ability, boolean starter, Map<String, Long> stats) {

    static final String STAT_BLOCKS = "blocks";
    static final String STAT_LOGS = "logs";
    static final String STAT_KILLS = "kills";
    static final String STAT_CROPS = "crops";
    static final String STAT_XP = "xp";
    static final String STAT_FORGED = "forged";

    ToolData {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        tier = Math.max(1, tier);
        level = Math.max(1, level);
        xp = Double.isNaN(xp) ? 0 : Math.max(0, xp);
        prestige = Math.max(0, prestige);
        List<ToolPerk> sorted = new ArrayList<>(perks == null ? List.of() : perks);
        sorted.sort(Comparator.comparingInt(ToolPerk::slot));
        perks = List.copyOf(sorted);
        skin = blankToNull(skin);
        ability = blankToNull(ability);
        stats = Map.copyOf(stats == null ? Map.of() : stats);
    }

    /** A brand new tool at level 1. */
    static ToolData fresh(UUID id, ToolKind kind, int tier, boolean starter, String ability) {
        return new ToolData(id, kind, tier, 1, 0, 0, 0, List.of(), null, ability, starter, Map.of());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    long stat(String key) {
        return stats.getOrDefault(key, 0L);
    }

    ToolData withProgress(int level, double xp) {
        return new ToolData(id, kind, tier, level, xp, prestige, prestigeSeason, perks, skin, ability, starter, stats);
    }

    ToolData withTier(int tier, boolean starter) {
        return new ToolData(id, kind, tier, level, xp, prestige, prestigeSeason, perks, skin, ability, starter, stats);
    }

    ToolData withPrestige(int prestige, int season, int level, double xp) {
        return new ToolData(id, kind, tier, level, xp, prestige, season, perks, skin, ability, starter, stats);
    }

    ToolData withPerks(List<ToolPerk> perks) {
        return new ToolData(id, kind, tier, level, xp, prestige, prestigeSeason, perks, skin, ability, starter, stats);
    }

    ToolData withSkin(String skin) {
        return new ToolData(id, kind, tier, level, xp, prestige, prestigeSeason, perks, skin, ability, starter, stats);
    }

    ToolData withStats(Map<String, Long> stats) {
        return new ToolData(id, kind, tier, level, xp, prestige, prestigeSeason, perks, skin, ability, starter, stats);
    }

    /** Adds to a counter (ignores 0 and negative amounts). */
    ToolData addStat(String key, long amount) {
        if (amount <= 0) {
            return this;
        }
        Map<String, Long> copy = new LinkedHashMap<>(stats);
        copy.merge(key, amount, Long::sum);
        return withStats(copy);
    }

    /** Unlocked perk slots for these slot levels. */
    int slots(List<Integer> slotLevels) {
        return ToolSlots.unlocked(level, prestige, slotLevels);
    }

    ToolInfo info(List<Integer> slotLevels) {
        return new ToolInfo(id, kind, tier, level, xp, prestige, slots(slotLevels), perks, skin, ability, starter, stats);
    }
}
