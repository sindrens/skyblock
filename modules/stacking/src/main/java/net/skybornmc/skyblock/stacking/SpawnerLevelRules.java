package net.skybornmc.skyblock.stacking;

import java.util.List;

/**
 * Spawners v2 (owner plan 5 Nov 2026, FULL_PLAN.md section 5), pure: a spawner level per island and mob type from kills.
 * Level 1 is free; level n needs {@code base x n^2} kills in this prestige cycle (base 100 / 250 / 600 / 1500 for tier
 * 1-4; tier 1 level 2 = 400 kills). The placement limit per mob type is {@code perLevel x level} (max {@code limitMax});
 * after a first prestige it stays at the top (a prestige never takes placed spawners away). Premium tier 5 has no levels.
 */
final class SpawnerLevelRules {

    private SpawnerLevelRules() {
    }

    /** Kills per level step of a tier (index 0 = tier 1); 0 = no levels (tier 5 or unknown). */
    static long base(List<Long> bases, int tier) {
        return tier >= 1 && tier <= bases.size() ? Math.max(0, bases.get(tier - 1)) : 0;
    }

    /** The level for {@code kills} (1 .. max). */
    static int level(long kills, long base, int max) {
        if (base <= 0) {
            return 1;
        }
        int lv = (int) Math.floor(Math.sqrt(Math.max(0, kills) / (double) base));
        return Math.max(1, Math.min(max, lv));
    }

    /** Kills needed in this cycle to reach {@code level} ({@code base x level^2}; 0 for level 1). */
    static long killsFor(int level, long base) {
        return level <= 1 ? 0 : base * (long) level * level;
    }

    /** 0..1 from this level to the next (1 at the top). */
    static double progress(long kills, long base, int max) {
        int lv = level(kills, base, max);
        if (lv >= max || base <= 0) {
            return 1;
        }
        long from = killsFor(lv, base);
        long to = killsFor(lv + 1, base);
        return Math.max(0, Math.min(1, (kills - from) / (double) Math.max(1, to - from)));
    }

    /**
     * The most spawners of one mob type on the island from its level (owner 7 Nov 2026): {@code base} at level 1,
     * {@code perLevel} more each level after it (64, 96, 128 ...), at most {@code limitMax}; after a prestige the top.
     */
    static int limit(int level, int prestige, boolean premium, int base, int perLevel, int limitMax, int premiumLimit, int maxLevel) {
        if (premium) {
            return premiumLimit;
        }
        int lv = prestige > 0 ? maxLevel : Math.max(1, level);
        long n = (long) base + (long) perLevel * (lv - 1);
        return (int) Math.max(1, Math.min(limitMax, n));
    }

    /** True when the type can prestige now (top level, below the prestige cap). */
    static boolean canPrestige(long kills, long base, int maxLevel, int prestige, int maxPrestige) {
        return base > 0 && level(kills, base, maxLevel) >= maxLevel && prestige < maxPrestige;
    }

    /** Drops multiplier of a type's prestige: {@code 1 + percent x prestige / 100}. */
    static double dropMultiplier(int prestige, double percent) {
        return 1 + Math.max(0, prestige) * Math.max(0, percent) / 100.0;
    }
}
