package net.skybornmc.skyblock.wardrobe;

/**
 * The set level curve (WARDROBE §3.2, WD14/WD15). Pure and unit-tested.
 * <ul>
 *   <li>XP for level {@code L → L+1} = {@code baseXp × growth^L} (level 1 costs 200, level 50 costs 21,344).</li>
 *   <li>Merged sets need {@code mergedFactor} times the XP for every level (1.5×).</li>
 *   <li>Entries store their <b>total</b> XP; the level follows from it, so XP can be added from anywhere (flushes, tomes,
 *       merges) without ever losing or doubling a level.</li>
 * </ul>
 *
 * @param baseXp       XP for level 0 → 1
 * @param growth       factor per level ({@code 1.10})
 * @param maxLevel     highest level ({@code 50})
 * @param mergedFactor XP factor for merged sets ({@code 1.5})
 */
record SetLevels(double baseXp, double growth, int maxLevel, double mergedFactor) {

    SetLevels {
        baseXp = Double.isFinite(baseXp) && baseXp > 0 ? baseXp : 1;
        growth = Double.isFinite(growth) && growth >= 1 ? growth : 1;
        maxLevel = Math.max(1, maxLevel);
        mergedFactor = Double.isFinite(mergedFactor) && mergedFactor > 0 ? mergedFactor : 1;
    }

    private double factor(boolean merged) {
        return merged ? mergedFactor : 1;
    }

    /** XP needed for {@code level → level + 1} (0 at or above the max level). */
    double step(int level, boolean merged) {
        if (level < 0 || level >= maxLevel) {
            return 0;
        }
        return baseXp * Math.pow(growth, level) * factor(merged);
    }

    /** Total XP from 0 to reach {@code level} (clamped to 0 … max). */
    double total(int level, boolean merged) {
        int l = Math.max(0, Math.min(level, maxLevel));
        double sum = 0;
        for (int i = 0; i < l; i++) {
            sum += step(i, merged);
        }
        return sum;
    }

    /** The level a total XP amount gives (0 … max). */
    int level(double xp, boolean merged) {
        if (!(xp > 0)) {
            return 0;
        }
        double need = 0;
        for (int l = 0; l < maxLevel; l++) {
            need += step(l, merged);
            // A hair of tolerance so a total stored as a rounded double still counts as reaching the level.
            if (xp + 1e-6 < need) {
                return l;
            }
        }
        return maxLevel;
    }

    /** XP clamped to 0 … the total of the max level (XP past the max is not kept). */
    double clamp(double xp, boolean merged) {
        if (!(xp > 0)) {
            return 0;
        }
        return Math.min(xp, total(maxLevel, merged));
    }

    /** Progress inside the current level, 0.0 … 1.0 (1.0 at the max level). */
    double progress(double xp, boolean merged) {
        int l = level(xp, merged);
        if (l >= maxLevel) {
            return 1;
        }
        double into = clamp(xp, merged) - total(l, merged);
        double step = step(l, merged);
        return step <= 0 ? 1 : Math.max(0, Math.min(1, into / step));
    }

    /** XP still missing for the next level (0 at the max level). */
    double toNext(double xp, boolean merged) {
        int l = level(xp, merged);
        if (l >= maxLevel) {
            return 0;
        }
        return Math.max(0, total(l + 1, merged) - clamp(xp, merged));
    }

    /**
     * XP after a Wardrobe Tome (WD13): {@code amount} more, but never past the start of {@code maxTomeLevel} (the last
     * levels are always played). Never lowers the XP.
     */
    double tome(double xp, double amount, int maxTomeLevel, boolean merged) {
        double current = clamp(xp, merged);
        double ceiling = total(Math.min(maxTomeLevel, maxLevel), merged);
        if (!(amount > 0) || current >= ceiling) {
            return current;
        }
        return Math.min(ceiling, current + amount);
    }

    /** Whether a tome can still add anything ({@code level < maxTomeLevel}). */
    boolean tomeUsable(double xp, int maxTomeLevel, boolean merged) {
        return clamp(xp, merged) < total(Math.min(maxTomeLevel, maxLevel), merged);
    }
}
