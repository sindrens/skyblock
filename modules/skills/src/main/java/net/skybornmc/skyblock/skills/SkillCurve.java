package net.skybornmc.skyblock.skills;

/**
 * The level curve (docs/ECONOMY_BALANCE.md §1.2): XP to go from level L to L+1 is
 * {@code base × early^min(L, break) × late^max(0, L − break)}; the total to reach a level is the sum of the steps.
 * With the defaults (600, 1.2, 1.13, 35) level 5 needs 4,465 XP, level 10 15,575 and level 66 about 119.5M.
 *
 * <p>Immutable; cumulative totals are precomputed so lookups are a binary search.</p>
 */
public final class SkillCurve {

    private final double base;
    private final double early;
    private final double late;
    private final int breakLevel;
    private final int maxLevel;
    /** totals[L] = total XP needed to reach level L (totals[0] = 0). */
    private final double[] totals;

    public SkillCurve(double base, double early, double late, int breakLevel, int maxLevel) {
        if (!(base > 0) || !(early >= 1) || !(late >= 1) || breakLevel < 0 || maxLevel < 1) {
            throw new IllegalArgumentException("Invalid curve: base > 0, growth >= 1, break >= 0, max >= 1");
        }
        this.base = base;
        this.early = early;
        this.late = late;
        this.breakLevel = breakLevel;
        this.maxLevel = maxLevel;
        this.totals = new double[maxLevel + 1];
        for (int level = 1; level <= maxLevel; level++) {
            totals[level] = totals[level - 1] + step(level - 1);
        }
    }

    /** XP needed to go from {@code level} to {@code level + 1}. */
    public double step(int level) {
        int l = Math.max(0, level);
        return base * Math.pow(early, Math.min(l, breakLevel)) * Math.pow(late, Math.max(0, l - breakLevel));
    }

    /** Total XP needed to reach {@code level} (clamped to 0..max). */
    public double totalFor(int level) {
        return totals[Math.clamp(level, 0, maxLevel)];
    }

    /** The level a total XP amount gives (0..max). */
    public int levelFor(double xp) {
        if (!(xp > 0)) {
            return 0;
        }
        int lo = 0;
        int hi = maxLevel;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (totals[mid] <= xp) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return lo;
    }

    /** Progress inside the current level, 0.0 to 1.0 (1.0 at the max level). */
    public double progress(double xp) {
        int level = levelFor(xp);
        if (level >= maxLevel) {
            return 1.0;
        }
        double into = Math.max(0, xp) - totals[level];
        return Math.clamp(into / step(level), 0.0, 1.0);
    }

    /** XP earned inside the current level. */
    public double intoLevel(double xp) {
        int level = levelFor(xp);
        return Math.max(0, xp) - totals[level];
    }

    public int maxLevel() {
        return maxLevel;
    }
}
