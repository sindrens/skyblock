package net.skybornmc.skyblock.production;

/**
 * Pure farming rules (PHASE-7 M7.3), unit-tested: who gets Farming XP for a harvest, the growth multiplier, extra growth
 * stages and Harvest Yield copies.
 */
final class FarmingRules {

    private FarmingRules() {
    }

    /**
     * The facts of one harvest.
     *
     * @param ripe       the crop was ripe (full age, or a grown melon/pumpkin/cane/cactus/torchflower; placed blocks never)
     * @param member     the player is a stored member on their own island in survival (never coop players or visitors)
     * @param withHoe    broken or picked with a hoe
     * @param boneMealed the crop was grown with bone meal
     */
    record Harvest(boolean ripe, boolean member, boolean withHoe, boolean boneMealed) {
    }

    /** PD11: Farming skill XP for {@code crops} ripe blocks: ripe, own island member, a hoe (when needed), no bone meal. */
    static double skillXp(Harvest h, int crops, double xpPerCrop, boolean needHoe) {
        if (h == null || !h.ripe() || !h.member() || h.boneMealed() || (needHoe && !h.withHoe()) || crops <= 0 || !(xpPerCrop > 0)) {
            return 0;
        }
        return crops * xpPerCrop;
    }

    /** Harvest quest progress (no hoe needed): ripe, own island member, no bone meal. */
    static boolean countsForQuests(Harvest h) {
        return h != null && h.ripe() && h.member() && !h.boneMealed();
    }

    /** The planting gate: true when the player's Farming level is high enough (or the crop has no gate). */
    static boolean mayPlant(int required, int level) {
        return required <= 0 || level >= required;
    }

    /**
     * Growth speed of an island (M7.3: vanilla × Crop Growth upgrade × crop growth family): {@code (1 + upgrade%) × (1 +
     * min(family%, cap))}. Negative inputs count as 0, so it is never below 1.
     */
    static double growthMultiplier(double upgradePercent, double familyPercent, double familyCap) {
        double u = upgradePercent > 0 && Double.isFinite(upgradePercent) ? upgradePercent : 0;
        double cap = familyCap > 0 && Double.isFinite(familyCap) ? familyCap : 0;
        double f = familyPercent > 0 && Double.isFinite(familyPercent) ? Math.min(familyPercent, cap) : 0;
        return (1 + u / 100.0) * (1 + f / 100.0);
    }

    /**
     * Extra growth stages for one natural growth step: a multiplier of 1.36 adds one more stage 36% of the time, 2.5 adds one
     * always and a second half the time. {@code roll} is uniform in [0, 1).
     */
    static int extraStages(double multiplier, double roll) {
        return copies(multiplier - 1, roll);
    }

    /** Harvest Yield: extra copies of the crop items for a {@code percent}% chance (120% = 1 sure + 20% for a second). */
    static int extraCopies(double percent, double roll) {
        return copies(percent / 100.0, roll);
    }

    private static int copies(double expected, double roll) {
        if (!(expected > 0) || !Double.isFinite(expected)) {
            return 0;
        }
        double whole = Math.floor(expected);
        int n = (int) Math.min(64, whole);
        return roll < expected - whole ? n + 1 : n;
    }

    /** New age after a growth step plus extra stages, never above the maximum. */
    static int boostedAge(int newAge, int extra, int maxAge) {
        return Math.min(maxAge, newAge + Math.max(0, extra));
    }
}
