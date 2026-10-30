package net.skybornmc.skyblock.depths.combat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.random.RandomGenerator;

/** Rolls what one kill of a Depths mob gives (M4.8). Pure: a few random numbers per kill. */
public final class MobRewards {

    /**
     * The result of one roll.
     *
     * @param items    satchel items, id → amount (only positive amounts)
     * @param crystals crystals found
     * @param xp       Combat XP
     */
    public record Roll(Map<String, Integer> items, long crystals, double xp) {
    }

    private MobRewards() {
    }

    /**
     * @param dropMultiplier    looting/fortune as an expected value (1.0 = none), capped at {@code maxDropMultiplier}
     * @param crystalMultiplier crystal bonus family (1.0 = none)
     */
    public static Roll roll(MobDef mob, double dropMultiplier, double crystalMultiplier, double maxDropMultiplier,
                            RandomGenerator rng) {
        Map<String, Integer> items = new LinkedHashMap<>();
        double mult = Math.max(1.0, Math.min(Double.isNaN(dropMultiplier) ? 1.0 : dropMultiplier, Math.max(1.0, maxDropMultiplier)));
        for (MobDef.Drop d : mob.drops()) {
            if (d.chance() < 1.0 && rng.nextDouble() >= d.chance()) {
                continue;
            }
            int base = d.max() > d.min() ? d.min() + rng.nextInt(d.max() - d.min() + 1) : d.min();
            int amount = expected(base * mult, rng);
            if (amount > 0) {
                items.merge(d.item(), amount, Integer::sum);
            }
        }
        long hits = expected(mob.crystalChance() * Math.max(0.0, crystalMultiplier), rng);
        return new Roll(items, hits * mob.crystalAmount(), mob.xp());
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
