package net.skybornmc.skyblock.common.mob;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EliteRulesTest {

    private static final EliteRules RULES = new EliteRules(2, 1, 3, 2, 4, true,
            List.of(AbilityId.ENRAGE, AbilityId.SHIELD, AbilityId.SLAM));

    /** Fails the test if a random number is drawn. */
    private static final RandomGenerator NO_DRAW = new RandomGenerator() {
        @Override
        public long nextLong() {
            throw new AssertionError("no random number may be drawn");
        }
    };

    @Test
    void onlyEligibleMobsRollAndTheOthersDrawNothing() {
        assertFalse(RULES.roll(0, 10, false, NO_DRAW), "chance 0: never, no draw (old configs stay identical)");
        assertFalse(RULES.roll(0.5, 3, false, NO_DRAW), "below the min tier");
        assertFalse(RULES.roll(0.5, 10, true, NO_DRAW), "mini-bosses are never elites");
        assertTrue(RULES.roll(1.0, 4, false, new SplittableRandom(1)));
    }

    @Test
    void rollMatchesTheChance() {
        RandomGenerator rng = new SplittableRandom(42);
        int elites = 0;
        int n = 200_000;
        for (int i = 0; i < n; i++) {
            if (RULES.roll(0.02, 6, false, rng)) {
                elites++;
            }
        }
        assertEquals(0.02, elites / (double) n, 0.002);
    }

    /** Budget-neutral: the average loot factor over normal and elite kills is exactly 1. */
    @Test
    void lootFactorKeepsTheAverage() {
        for (double p : new double[]{0.01, 0.02, 0.1, 0.5, 1.0}) {
            double normal = RULES.lootFactor(p, 5, false, false);
            double elite = RULES.lootFactor(p, 5, false, true);
            assertEquals(1.0, (1 - p) * normal + p * elite, 1e-12, "p=" + p);
            assertEquals(3.0, elite / normal, 1e-12, "an elite drops 3x a normal kill");
        }
        assertEquals(1.0, RULES.lootFactor(0, 5, false, false), "no elites: unchanged");
        assertEquals(1.0, RULES.lootFactor(0.02, 2, false, false), "tier too low: unchanged");
        assertEquals(1.0, RULES.lootFactor(0.02, 5, true, false), "bosses: unchanged");
        EliteRules generous = new EliteRules(2, 1, 3, 2, 4, false, List.of());
        assertEquals(1.0, generous.lootFactor(0.02, 5, false, false));
        assertEquals(3.0, generous.lootFactor(0.02, 5, false, true));
    }

    @Test
    void extraAbilityIsTheFirstOneTheMobLacks() {
        List<AbilitySpec> own = List.of(AbilitySpec.of(AbilityId.ENRAGE), AbilitySpec.of(AbilityId.VOLLEY));
        assertSame(own, RULES.abilities(own, false));
        List<AbilitySpec> elite = RULES.abilities(own, true);
        assertEquals(List.of(AbilityId.ENRAGE, AbilityId.VOLLEY, AbilityId.SHIELD), elite.stream().map(AbilitySpec::id).toList());
        assertEquals(List.of(AbilityId.ENRAGE), RULES.abilities(List.of(), true).stream().map(AbilitySpec::id).toList());
        List<AbilitySpec> full = List.of(AbilitySpec.of(AbilityId.SLAM), AbilitySpec.of(AbilityId.LEAP), AbilitySpec.of(AbilityId.CHARGE),
                AbilitySpec.of(AbilityId.VOLLEY), AbilitySpec.of(AbilityId.PULL), AbilitySpec.of(AbilityId.SUMMON));
        assertEquals(6, RULES.abilities(full, true).size(), "never above the ability cap");
        assertEquals(1, new EliteRules(2, 1, 3, 2, 4, true, List.of()).abilities(List.of(AbilitySpec.of(AbilityId.SLAM)), true).size());
    }

    @Test
    void badMultipliersBecomeOne() {
        EliteRules r = new EliteRules(0, -1, Double.NaN, 0, 4, true, null);
        assertEquals(1, r.healthMultiplier());
        assertEquals(1, r.damageMultiplier());
        assertEquals(1, r.dropMultiplier());
        assertEquals(1, r.xpMultiplier());
        assertTrue(r.extraAbilities().isEmpty());
    }
}
