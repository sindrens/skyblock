package net.skybornmc.skyblock.progression;

/**
 * The pillar level curve (PHASE-5 M5.1): XP for the next level = {@code base × growth^level}, no level cap. Pure and
 * immutable, unit-tested.
 *
 * <p>Total XP to reach level L is the geometric sum {@code base × (growth^L − 1) / (growth − 1)} (or {@code base × L}
 * for growth 1). Because the cost grows by the same factor every level, k times the XP is always worth the same number
 * of extra levels, {@code ln k / ln growth}: that is how team size is damped (5 members ≈ 14 levels at 1.12).</p>
 */
public final class PillarCurve {

    /** Highest level ever computed (far beyond any real island; keeps the math finite). */
    public static final int MAX_LEVEL = 10_000;

    private final double base;
    private final double growth;

    public PillarCurve(double base, double growth) {
        this.base = base > 0 && Double.isFinite(base) ? base : 1;
        this.growth = growth >= 1 && Double.isFinite(growth) ? growth : 1;
    }

    public double base() {
        return base;
    }

    public double growth() {
        return growth;
    }

    /** XP from {@code level} to {@code level + 1}. */
    public double stepCost(int level) {
        return base * Math.pow(growth, Math.max(0, level));
    }

    /** Total XP to reach {@code level} from 0 (infinite past the double range). */
    public double totalFor(int level) {
        int l = Math.max(0, Math.min(MAX_LEVEL, level));
        if (growth == 1) {
            return base * l;
        }
        return base * (Math.pow(growth, l) - 1) / (growth - 1);
    }

    /** The level a total XP amount gives (0 for nothing, never above {@link #MAX_LEVEL}). */
    public int levelFor(double totalXp) {
        if (!(totalXp > 0)) {
            return 0;
        }
        if (Double.isInfinite(totalXp)) {
            return MAX_LEVEL;
        }
        double estimate = growth == 1 ? totalXp / base
                : Math.log(1 + totalXp * (growth - 1) / base) / Math.log(growth);
        int level = (int) Math.max(0, Math.min(MAX_LEVEL, Math.floor(estimate)));
        // Floating point can put the estimate one off at exact boundaries: correct it against totalFor.
        while (level < MAX_LEVEL && totalFor(level + 1) <= totalXp) {
            level++;
        }
        while (level > 0 && totalFor(level) > totalXp) {
            level--;
        }
        return level;
    }

    /** Progress inside the current level, 0.0 to 1.0. */
    public double progress(double totalXp) {
        int level = levelFor(totalXp);
        if (level >= MAX_LEVEL) {
            return 1.0;
        }
        double from = totalFor(level);
        double step = totalFor(level + 1) - from;
        if (!(step > 0) || Double.isInfinite(step)) {
            return 0.0;
        }
        return Math.max(0, Math.min(1, (Math.max(0, totalXp) - from) / step));
    }

    /**
     * Extra levels {@code multiplier} times the XP is worth (team-size damping): {@code ln multiplier / ln growth}. 0 for
     * a multiplier of 1 or less; infinite for a flat curve (growth 1), which has no damping.
     */
    public double extraLevelsFor(double multiplier) {
        if (!(multiplier > 1)) {
            return 0;
        }
        if (growth == 1) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.log(multiplier) / Math.log(growth);
    }
}
