package net.skybornmc.skyblock.skills;

/**
 * The level curve (docs/ECONOMY_BALANCE.md §1.2): XP to go from level L to L+1 is
 * {@code base × early^min(L, break) × late^clamp(L − break, 0, endBreak − break) × end^max(0, L − endBreak)};
 * the total to reach a level is the sum of the steps. With the defaults (600, 1.2, 1.13, 35) level 5 needs 4,465 XP,
 * level 10 15,575 and level 66 about 119.5M. Owner #65 raised the max level to 150: after level 66 (the last tier
 * unlock) each level costs only 2% more than the one before ({@code end}), so levels 67–150 stay a long goal
 * without exploding.
 *
 * <p>Immutable; cumulative totals are precomputed so lookups are a binary search.</p>
 */
public final class SkillCurve {

    private final double base;
    private final double early;
    private final double late;
    private final int breakLevel;
    private final double end;
    private final int endBreak;
    private final int maxLevel;
    /** totals[L] = total XP needed to reach level L (totals[0] = 0). */
    private final double[] totals;

    /** Two segments only (the late growth runs to the max level). */
    public SkillCurve(double base, double early, double late, int breakLevel, int maxLevel) {
        this(base, early, late, breakLevel, late, Math.max(breakLevel, maxLevel), maxLevel);
    }

    /** Three segments: early growth up to {@code breakLevel}, late growth up to {@code endBreak}, then {@code end}. */
    public SkillCurve(double base, double early, double late, int breakLevel, double end, int endBreak, int maxLevel) {
        if (!(base > 0) || !(early >= 1) || !(late >= 1) || !(end >= 1) || breakLevel < 0 || endBreak < breakLevel
                || maxLevel < 1) {
            throw new IllegalArgumentException("Invalid curve: base > 0, growth >= 1, 0 <= break <= end break, max >= 1");
        }
        this.base = base;
        this.early = early;
        this.late = late;
        this.breakLevel = breakLevel;
        this.end = end;
        this.endBreak = endBreak;
        this.maxLevel = maxLevel;
        this.totals = new double[maxLevel + 1];
        for (int level = 1; level <= maxLevel; level++) {
            totals[level] = totals[level - 1] + step(level - 1);
        }
    }

    /** XP needed to go from {@code level} to {@code level + 1}. */
    public double step(int level) {
        int l = Math.max(0, level);
        return base * Math.pow(early, Math.min(l, breakLevel))
                * Math.pow(late, Math.clamp(l - breakLevel, 0, endBreak - breakLevel))
                * Math.pow(end, Math.max(0, l - endBreak));
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
