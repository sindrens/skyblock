package net.skybornmc.skyblock.tools;

import net.skybornmc.skyblock.common.tools.ToolPerk;
import net.skybornmc.skyblock.common.tools.ToolSlots;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The rules of living tools (M4.2), free of Bukkit so they are unit tested:
 * <ul>
 *   <li>XP per block/log/kill raises the level (1-50) along {@link ToolCurve}; at the max level XP stops counting
 *       (lifetime XP keeps counting in the {@code xp} stat).</li>
 *   <li>Prestige at the max level: back to level 1, keeps perks and stats, opens the prestige slot, adds a star;
 *       at most {@code perSeason} times per tool per season.</li>
 *   <li>Upgrading at the Forge keeps level, XP, perks, stats, skin and ability; only the tier changes (and a starter kit
 *       piece stops being one, because money and materials were paid for it).</li>
 * </ul>
 */
final class ToolProgress {

    private ToolProgress() {
    }

    /** The result of adding XP. */
    record Gain(ToolData tool, int fromLevel, int toLevel, double added) {
        boolean levelledUp() {
            return toLevel > fromLevel;
        }
    }

    /** Adds XP (and the lifetime {@code xp} stat). Non-positive amounts change nothing. */
    static Gain addXp(ToolData tool, double amount, ToolCurve curve) {
        int from = curve.levelFor(tool.xp());
        if (!(amount > 0) || Double.isInfinite(amount)) {
            return new Gain(tool, from, from, 0);
        }
        double cap = curve.maxXp();
        double xp = Math.min(cap, tool.xp() + amount);
        double added = xp - tool.xp();
        int to = curve.levelFor(xp);
        ToolData next = tool.withProgress(to, xp).addStat(ToolData.STAT_XP, Math.round(amount));
        return new Gain(next, from, to, Math.max(0, added));
    }

    /** Re-aligns the stored level with the curve (after the curve changed in tools.yml). */
    static ToolData normalize(ToolData tool, ToolCurve curve) {
        double xp = Math.min(tool.xp(), curve.maxXp());
        int level = curve.levelFor(xp);
        return level == tool.level() && xp == tool.xp() ? tool : tool.withProgress(level, xp);
    }

    enum PrestigeCheck {
        OK, NOT_MAX_LEVEL, ALREADY_THIS_SEASON, DISABLED
    }

    static PrestigeCheck canPrestige(ToolData tool, ToolCurve curve, int season, int perSeason, boolean enabled) {
        if (!enabled || perSeason <= 0) {
            return PrestigeCheck.DISABLED;
        }
        if (curve.levelFor(tool.xp()) < curve.maxLevel()) {
            return PrestigeCheck.NOT_MAX_LEVEL;
        }
        if (tool.prestige() > 0 && tool.prestigeSeason() == season && prestigesThisSeason(tool, season) >= perSeason) {
            return PrestigeCheck.ALREADY_THIS_SEASON;
        }
        return PrestigeCheck.OK;
    }

    /**
     * Prestiges done in {@code season}. Only the season of the last prestige is stored, so with the default limit of 1
     * per season this is 1 when the last prestige was this season.
     */
    static int prestigesThisSeason(ToolData tool, int season) {
        return tool.prestige() > 0 && tool.prestigeSeason() == season ? 1 : 0;
    }

    /** Prestiges the tool (call {@link #canPrestige} first): level 1, XP 0, prestige +1, perks and stats kept. */
    static ToolData prestige(ToolData tool, int season) {
        return tool.withPrestige(tool.prestige() + 1, season, 1, 0);
    }

    /** Forge upgrade to {@code newTier}: everything but the tier is kept; the starter flag is cleared. */
    static ToolData upgrade(ToolData tool, int newTier) {
        return tool.withTier(newTier, false).addStat(ToolData.STAT_FORGED, 1);
    }

    /**
     * Checks a perk list for this tool: every slot unlocked, no overlaps, no duplicate enchant ids.
     *
     * @return null if fine, otherwise a short reason (for logs)
     */
    static String perkProblem(ToolData tool, List<ToolPerk> perks, List<Integer> slotLevels) {
        int unlocked = ToolSlots.unlocked(tool.level(), tool.prestige(), slotLevels);
        Set<Integer> used = new HashSet<>();
        Set<String> ids = new HashSet<>();
        for (ToolPerk perk : perks) {
            if (perk.lastSlot() > unlocked) {
                return "slot " + perk.lastSlot() + " is locked (" + unlocked + " unlocked)";
            }
            for (int s = perk.slot(); s <= perk.lastSlot(); s++) {
                if (!used.add(s)) {
                    return "slot " + s + " is used twice";
                }
            }
            if (!ids.add(perk.id())) {
                return "perk " + perk.id() + " is on the tool twice";
            }
        }
        return null;
    }
}
