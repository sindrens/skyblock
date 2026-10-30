package net.skybornmc.skyblock.depths.drops;

import java.util.HashMap;
import java.util.Map;
import java.util.random.RandomGenerator;

/**
 * Rolls the drops of one block/log (M4.9). Pure and allocation-light: the per-break path is a few random numbers.
 * Multipliers are expected values: 1.35 drops = one drop plus a 35% chance of a second.
 */
public final class DropRoller {

    /**
     * The result of one roll.
     *
     * @param items    drop items (of the tier's item)
     * @param crystals crystals found
     * @param rare     rare finds, item id → amount (empty map when none)
     */
    public record Roll(int items, long crystals, Map<String, Integer> rare) {
        public static final Roll NOTHING = new Roll(0, 0, Map.of());

        public boolean empty() {
            return items <= 0 && crystals <= 0 && rare.isEmpty();
        }
    }

    private DropRoller() {
    }

    /**
     * @param drops             the tier's table entry
     * @param dropMultiplier    fortune (tool tier, level, extra-drops family), 1.0 = none
     * @param crystalMultiplier crystal bonus family as a multiplier, 1.0 = none
     * @param rareMultiplier    rare-find bonus family as a multiplier, 1.0 = none
     * @param maxDrops          most drops per block (the table's amount is always reached)
     */
    public static Roll roll(DropTable.TierDrops drops, double dropMultiplier, double crystalMultiplier, double rareMultiplier,
                            int maxDrops, RandomGenerator rng) {
        if (drops == null) {
            return Roll.NOTHING;
        }
        int items = expected(drops.amount() * Math.max(1.0, dropMultiplier), rng);
        items = Math.max(drops.amount(), Math.min(items, Math.max(maxDrops, drops.amount())));
        long hits = expected(drops.crystalChance() * Math.max(0.0, crystalMultiplier), rng);
        long crystals = hits * drops.crystalAmount();
        Map<String, Integer> rare = Map.of();
        for (DropTable.RareDrop r : drops.rare()) {
            double p = Math.min(1.0, r.chance() * Math.max(0.0, rareMultiplier));
            if (p > 0 && rng.nextDouble() < p) {
                if (rare.isEmpty()) {
                    rare = new HashMap<>(2);
                }
                rare.merge(r.item(), 1, Integer::sum);
            }
        }
        return new Roll(items, crystals, rare);
    }

    /** An expected value as a whole number: the integer part always, the fraction as a chance of one more. */
    static int expected(double value, RandomGenerator rng) {
        if (!(value > 0)) {
            return 0;
        }
        double capped = Math.min(value, 1_000_000);
        int whole = (int) Math.floor(capped);
        double fraction = capped - whole;
        return fraction > 0 && rng.nextDouble() < fraction ? whole + 1 : whole;
    }
}
