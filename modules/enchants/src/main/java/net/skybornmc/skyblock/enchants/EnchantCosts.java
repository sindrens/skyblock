package net.skybornmc.skyblock.enchants;

import net.skybornmc.skyblock.common.item.Rarity;

import java.util.EnumMap;
import java.util.Map;

/**
 * Crystal costs (ENCHANTS §5, EN14), pure and unit-tested against every row of the plan's table.
 * <ul>
 *   <li>Step {@code s} costs {@code base × growth^(s−1)} (100, 180, 324 … 19,836 at step 10).</li>
 *   <li>An enchant with max {@code M > 1} spreads its levels over the steps: level {@code n} is step
 *       {@code round(1 + (n − 1) × (steps − 1) / (M − 1))}, so level I is always step 1 and the max level the last step.
 *       Max-I enchants use {@code single-level-step} (Mythics {@code mythic-step}).</li>
 *   <li>Cost = step cost × rarity factor, rounded to whole crystals (half up).</li>
 *   <li>Apply fee (EN10) = max(level I cost, apply% of the book level's cost); combine fee (EN11) = combine% of the new
 *       level's cost; shatter refund (EN13) = refund% of the crystals put in, rounded down.</li>
 * </ul>
 */
record EnchantCosts(double stepBase, double stepGrowth, int steps, int singleLevelStep, int mythicStep,
                    Map<Rarity, Double> rarityFactors, double applyPercent, double combinePercent, double refundPercent) {

    EnchantCosts {
        rarityFactors = new EnumMap<>(rarityFactors);
    }

    /** The plan's defaults. */
    static EnchantCosts defaults() {
        return new EnchanterConfig().costs();
    }

    /** The step of a level (1-based). */
    int step(EnchantDef def, int level) {
        int max = def.maxLevel();
        if (max <= 1) {
            return Math.max(1, Math.min(steps, def.mythic() ? mythicStep : singleLevelStep));
        }
        int n = Math.max(1, Math.min(level, max));
        return (int) Math.round(1 + (n - 1) * (double) (steps - 1) / (max - 1));
    }

    /** Crystal cost of a step before the rarity factor (unrounded). */
    double stepCost(int step) {
        return stepBase * Math.pow(stepGrowth, step - 1);
    }

    double factor(Rarity rarity) {
        return rarityFactors.getOrDefault(rarity, 1.0);
    }

    /** Crystals to raise an enchant to {@code level} (the price of that one level). */
    long levelCost(EnchantDef def, int level) {
        return Math.round(stepCost(step(def, level)) * factor(def.rarity()));
    }

    /** Crystals for levels 1..{@code level} together. */
    long totalTo(EnchantDef def, int level) {
        long sum = 0;
        for (int n = 1; n <= Math.min(level, def.maxLevel()); n++) {
            sum += levelCost(def, n);
        }
        return sum;
    }

    /** EN10: putting a book of {@code bookLevel} on a tool. */
    long applyFee(EnchantDef def, int bookLevel) {
        long levelOne = levelCost(def, 1);
        long share = Math.round(levelCost(def, bookLevel) * applyPercent / 100.0);
        return Math.max(levelOne, share);
    }

    /** §4.3: the next level with crystals. */
    long upgradeCost(EnchantDef def, int currentLevel) {
        return levelCost(def, currentLevel + 1);
    }

    /** EN11: two equal books (or a book onto the applied enchant) to {@code newLevel}. */
    long combineFee(EnchantDef def, int newLevel) {
        return Math.round(levelCost(def, newLevel) * combinePercent / 100.0);
    }

    /** EN13: crystals back when an enchant with {@code invested} crystals shatters. */
    long shatterRefund(long invested) {
        if (invested <= 0) {
            return 0;
        }
        return (long) Math.floor(invested * refundPercent / 100.0);
    }
}
