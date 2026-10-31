package net.skybornmc.skyblock.common.mob;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Elite mobs (plan M8.1): a mob with {@code elite-chance} above 0 spawns as an elite that often, but only in tiers from
 * {@code minTier} and never as a (mini-)boss. Elites get more health, one extra ability, more drops and a star before the
 * name. With {@code budgetNeutral} the normal kills of that mob drop a little less so the average money per kill
 * (elites included) stays the same: elites add excitement, not income.
 *
 * @param healthMultiplier max health of an elite (2 = twice)
 * @param damageMultiplier damage per hit of an elite (1 = unchanged)
 * @param dropMultiplier   drops and crystals of an elite kill (3 = three times)
 * @param xpMultiplier     Combat XP of an elite kill
 * @param minTier          lowest zone tier that rolls elites
 * @param budgetNeutral    scale normal kills down so the average stays the same
 * @param extraAbilities   candidates for the extra ability, the first one the mob doesn't have yet is used
 */
public record EliteRules(double healthMultiplier, double damageMultiplier, double dropMultiplier, double xpMultiplier,
                         int minTier, boolean budgetNeutral, List<AbilityId> extraAbilities) {

    public EliteRules {
        healthMultiplier = positive(healthMultiplier);
        damageMultiplier = positive(damageMultiplier);
        dropMultiplier = positive(dropMultiplier);
        xpMultiplier = positive(xpMultiplier);
        extraAbilities = extraAbilities == null ? List.of() : List.copyOf(extraAbilities);
    }

    private static double positive(double v) {
        return Double.isNaN(v) || v <= 0 ? 1.0 : v;
    }

    /** True if elites can appear for this mob at this spot (chance above 0, tier high enough, not a boss). */
    public boolean eligible(double chance, int tier, boolean boss) {
        return !boss && tier >= minTier && chance > 0;
    }

    /** Rolls whether a new spawn is an elite. Uses one random number only when the mob is eligible. */
    public boolean roll(double chance, int tier, boolean boss, RandomGenerator rng) {
        return eligible(chance, tier, boss) && rng.nextDouble() < chance;
    }

    /**
     * Drop (and crystal) factor of one kill. Not eligible = 1. Eligible with {@code budgetNeutral}: normal kills
     * {@code 1 / (1 + p (m - 1))}, elites {@code m} times that, so {@code (1 - p) normal + p elite = 1}.
     */
    public double lootFactor(double chance, int tier, boolean boss, boolean elite) {
        if (!eligible(chance, tier, boss)) {
            return 1.0;
        }
        double p = Math.min(1, chance);
        double base = budgetNeutral ? 1.0 / (1.0 + p * (dropMultiplier - 1.0)) : 1.0;
        return elite ? dropMultiplier * base : base;
    }

    /** The mob's abilities, plus the first extra ability it doesn't have when it is an elite (unset values = defaults). */
    public List<AbilitySpec> abilities(List<AbilitySpec> own, boolean elite) {
        if (!elite) {
            return own;
        }
        List<AbilitySpec> out = new ArrayList<>(own);
        if (out.size() >= MobStats.MAX_ABILITIES) {
            return out;
        }
        for (AbilityId extra : extraAbilities) {
            boolean has = false;
            for (AbilitySpec a : own) {
                has |= a.id() == extra;
            }
            if (!has) {
                out.add(AbilitySpec.of(extra));
                break;
            }
        }
        return out;
    }
}
