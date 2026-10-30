package net.skybornmc.skyblock.achievements;

import net.skybornmc.skyblock.common.achievements.IslandBoost;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.Map;

/**
 * Pure boost math of AC2/AC3 (RANKS_TITLES_ACHIEVEMENTS §C2-§C4, unit-tested): every finished tier gives boost points
 * (default 1/2/3/5/8 for tiers I-V, tiers above V give the tier V value), points add up per boost, and each point adds a
 * fixed step up to the boost's own cap ("x / cap" in the menu). The bonus family cap applies on top in {@code BonusService}.
 */
final class BoostMath {

    private BoostMath() {
    }

    /** Points of one tier (1-based); tiers past the configured list give the last value; never negative. */
    static int tierPoints(int[] perTier, int tier) {
        if (perTier.length == 0 || tier < 1) {
            return 0;
        }
        return Math.max(0, perTier[Math.min(tier, perTier.length) - 1]);
    }

    /** Points of the first {@code unlocked} tiers of an achievement (capped at its tier count). */
    static int points(Tiered def, int unlocked, int[] perTier) {
        int total = 0;
        for (int tier = 1; tier <= Math.min(unlocked, def.tiers()); tier++) {
            total += tierPoints(perTier, tier);
        }
        return total;
    }

    /** {@code points × step}, at most {@code cap}, never below 0. */
    static double percent(int points, double step, double cap) {
        if (points <= 0 || step <= 0 || cap <= 0) {
            return 0;
        }
        return Math.min(cap, points * step);
    }

    /** Points at which the boost is full: the first point count whose step reaches the cap. */
    static int pointsToCap(double step, double cap) {
        if (step <= 0 || cap <= 0) {
            return 0;
        }
        return (int) Math.ceil(cap / step - 1e-9);
    }

    /** Extra auction slots: +1 from {@code first} points, +2 from {@code second} (0 = that step is off). */
    static int slots(int points, int first, int second) {
        int out = 0;
        if (first > 0 && points >= first) {
            out++;
        }
        if (second > 0 && points >= second && second >= first) {
            out++;
        }
        return out;
    }

    /** Points per boost of one profile: the unlocked tiers of every achievement, by its category's boost. */
    static Map<PersonalBoost, Integer> personalPoints(AchievementRegistry registry, Map<String, Integer> unlocked, int[] perTier) {
        Map<PersonalBoost, Integer> out = new EnumMap<>(PersonalBoost.class);
        for (AchievementDef def : registry.all()) {
            int u = unlocked.getOrDefault(def.id(), 0);
            if (u <= 0) {
                continue;
            }
            out.merge(registry.boostOf(def), points(def, u, perTier), Integer::sum);
        }
        return out;
    }

    /** Points per category id of one profile (the menu shows them per category). */
    static Map<String, Integer> categoryPoints(AchievementRegistry registry, Map<String, Integer> unlocked, int[] perTier) {
        Map<String, Integer> out = new java.util.HashMap<>();
        for (AchievementDef def : registry.all()) {
            int u = unlocked.getOrDefault(def.id(), 0);
            if (u > 0) {
                out.merge(def.category(), points(def, u, perTier), Integer::sum);
            }
        }
        return out;
    }

    /** Points of every finished tier of an island's achievements (one pool for all four island boosts). */
    static int islandPoints(AchievementRegistry registry, Map<String, Integer> unlocked, int[] perTier) {
        int total = 0;
        for (IslandAchievementDef def : registry.island()) {
            total += points(def, unlocked.getOrDefault(def.id(), 0), perTier);
        }
        return total;
    }

    /** Most points a profile can collect for one boost (every tier of every achievement of its categories). */
    static int maxPersonalPoints(AchievementRegistry registry, PersonalBoost boost, int[] perTier) {
        int total = 0;
        for (AchievementDef def : registry.all()) {
            if (registry.boostOf(def) == boost) {
                total += points(def, def.tiers(), perTier);
            }
        }
        return total;
    }

    /** One island boost's percent from the island's points. */
    static double islandPercent(int points, IslandBoost boost, AchievementsConfig config) {
        return percent(points, config.islandStep(boost), config.islandCap(boost));
    }

    /** "4.5", "10", "0.25": at most 2 decimals, trailing zeros dropped. */
    static String text(double percent) {
        if (!Double.isFinite(percent) || percent <= 0) {
            return "0";
        }
        return BigDecimal.valueOf(percent).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
}
