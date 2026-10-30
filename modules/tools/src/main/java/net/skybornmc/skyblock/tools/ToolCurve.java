package net.skybornmc.skyblock.tools;

/**
 * Tool level curve (ECONOMY_BALANCE §1.2 and §3.5, owner-approved): the step from level {@code L} to {@code L+1} costs
 * {@code base × growth^(L-1)} XP, so the total to reach level {@code L} is
 * {@code XT(L) = base × (growth^(L-1) - 1) / (growth - 1)}. Defaults: base 400, growth 1.21, max level 50
 * (slot 1 at ~2.2k XP, slot 4 at ~8.4M, level 50 at ~21.7M).
 *
 * <p>XP is counted from level 1 of the current prestige cycle; levels start at 1.</p>
 */
final class ToolCurve {

    private final double base;
    private final double growth;
    private final int maxLevel;
    private final double[] totals;

    ToolCurve(double base, double growth, int maxLevel) {
        if (!(base > 0) || !(growth >= 1.0) || maxLevel < 1) {
            throw new IllegalArgumentException("Invalid tool curve: base " + base + ", growth " + growth + ", max " + maxLevel);
        }
        this.base = base;
        this.growth = growth;
        this.maxLevel = maxLevel;
        this.totals = new double[maxLevel + 1];
        for (int level = 2; level <= maxLevel; level++) {
            totals[level] = totals[level - 1] + step(level - 1);
        }
    }

    int maxLevel() {
        return maxLevel;
    }

    double base() {
        return base;
    }

    double growth() {
        return growth;
    }

    /** XP from {@code level} to {@code level + 1}. */
    double step(int level) {
        return base * Math.pow(growth, Math.max(0, level - 1));
    }

    /** Total XP from level 1 to reach {@code level} (level 1 = 0; above max = the max level's total). */
    double xpForLevel(int level) {
        if (level <= 1) {
            return 0;
        }
        return totals[Math.min(level, maxLevel)];
    }

    /** Total XP needed for the max level (XP stops counting there until the tool prestiges). */
    double maxXp() {
        return totals[maxLevel];
    }

    /** The level a total XP amount gives (1 to max). */
    int levelFor(double xp) {
        if (!(xp > 0)) {
            return 1;
        }
        int lo = 1;
        int hi = maxLevel;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (totals[mid] <= xp + 1e-9) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return lo;
    }

    /** XP collected inside the current level. */
    double intoLevel(double xp) {
        int level = levelFor(xp);
        return Math.max(0, xp - xpForLevel(level));
    }

    /** Progress to the next level, 0.0 to 1.0 (1.0 at the max level). */
    double progress(double xp) {
        int level = levelFor(xp);
        if (level >= maxLevel) {
            return 1.0;
        }
        return Math.max(0, Math.min(1, intoLevel(xp) / step(level)));
    }
}
