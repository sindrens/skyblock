package net.skybornmc.skyblock.enchants;

import net.skybornmc.skyblock.common.item.Rarity;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Book drop math (ENCHANTS §3.2–§3.4): bands, chances, pity and first finds, with the plan's default numbers. */
class DropMathTest {

    private final DropMath.Rules rules = new EnchanterConfig().dropRules();

    @Test
    void bandsFollowTheTierTable() {
        assertEquals(0, rules.band(1));
        assertEquals(0, rules.band(3));
        assertEquals(1, rules.band(4));
        assertEquals(1, rules.band(6));
        assertEquals(2, rules.band(7));
        assertEquals(3, rules.band(10));
        assertEquals(3, rules.band(12));
        assertEquals(0, rules.band(0));
    }

    @Test
    void defaultChancesAndPityMatchThePlan() {
        assertEquals(List.of(2000, 3000, 4000, 5000), List.of(rules.oneIn(Pool.MINING, 0), rules.oneIn(Pool.MINING, 1),
                rules.oneIn(Pool.MINING, 2), rules.oneIn(Pool.MINING, 3)));
        assertEquals(5000, rules.oneIn(Pool.FORAGING, 3));
        // E4 (TUNE): combat 1 in 300/350/400/450 (~1.2-1.4 books per played hour, ECONOMY_BALANCE §9.7)
        assertEquals(List.of(300, 350, 400, 450), List.of(rules.oneIn(Pool.COMBAT, 0), rules.oneIn(Pool.COMBAT, 1),
                rules.oneIn(Pool.COMBAT, 2), rules.oneIn(Pool.COMBAT, 3)));
        // pity = 2.5x the expected gap
        for (Pool pool : List.of(Pool.MINING, Pool.FORAGING, Pool.COMBAT)) {
            for (int band = 0; band < 4; band++) {
                assertEquals(rules.oneIn(pool, band) * 2.5, rules.pityGap(pool, band), 0.001, pool + " band " + band);
            }
        }
        assertEquals(List.of(new DropMath.Milestone(100, Rarity.COMMON), new DropMath.Milestone(1000, Rarity.UNCOMMON)), rules.firstFinds());
        assertTrue(rules.drops(Pool.MINING));
        assertFalse(rules.drops(Pool.FARMING), "farming/fishing pools come with Phase 7");
    }

    @Test
    void bandOddsSumTo100AndHaveNoMythics() {
        for (int band = 0; band < 4; band++) {
            Map<Rarity, Double> odds = rules.odds(band);
            assertEquals(100, odds.values().stream().mapToDouble(Double::doubleValue).sum(), 0.001);
            assertFalse(odds.containsKey(Rarity.MYTHIC));
        }
        assertFalse(rules.odds(0).containsKey(Rarity.EPIC));
        assertEquals(8, rules.odds(3).get(Rarity.LEGENDARY), 0.001);
        // mini-boss: one band better, band D stays D
        assertEquals(rules.odds(1), DropMath.shifted(rules, 0, 1));
        assertEquals(rules.odds(3), DropMath.shifted(rules, 3, 1));
    }

    @Test
    void chanceOverFractionalActions() {
        assertEquals(1.0 / 2000, DropMath.chance(2000, 1), 1e-12);
        assertEquals(0, DropMath.chance(0, 1));
        assertEquals(0, DropMath.chance(2000, 0));
        assertEquals(1, DropMath.chance(1, 0.5));
        // 4 area blocks at 50% yield = 2 actions ~ 2/2000
        assertEquals(1 - Math.pow(1 - 1.0 / 2000, 2), DropMath.chance(2000, 2), 1e-12);
        assertTrue(DropMath.chance(2000, 0.5) < 1.0 / 2000);
    }

    @Test
    void firstFindsComeFirstThenPityThenChance() {
        List<DropMath.Milestone> ms = rules.firstFinds();
        assertEquals(DropMath.Kind.NONE, DropMath.decide(true, 99, 0, ms, true, 99, 5000, 0, 0.5).kind());
        DropMath.Decision first = DropMath.decide(true, 100, 0, ms, true, 100, 5000, 0, 0.5);
        assertEquals(DropMath.Kind.FIRST, first.kind());
        assertEquals(0, first.milestone());
        assertEquals(1, DropMath.decide(true, 1000, 1, ms, true, 900, 5000, 0, 0.5).milestone());
        // all first finds given: nothing more from them
        assertEquals(DropMath.Kind.NONE, DropMath.decide(true, 99_999, 2, ms, true, 10, 5000, 0, 0.5).kind());
        // not loaded or a grant in flight: first finds wait
        assertEquals(DropMath.Kind.NONE, DropMath.decide(false, 100, 0, ms, true, 100, 5000, 0, 0.5).kind());
        // pity
        assertEquals(DropMath.Kind.PITY, DropMath.decide(true, 99_999, 2, ms, true, 5000, 5000, 0, 0.5).kind());
        assertEquals(DropMath.Kind.NONE, DropMath.decide(true, 99_999, 2, ms, false, 5000, 5000, 0, 0.5).kind(), "pity waits for its counter");
        assertEquals(DropMath.Kind.NONE, DropMath.decide(true, 99_999, 2, ms, true, 1e9, 0, 0, 0.5).kind(), "0 = no pity");
        // chance
        assertEquals(DropMath.Kind.CHANCE, DropMath.decide(true, 99_999, 2, ms, true, 10, 5000, 0.001, 0.0005).kind());
        assertEquals(DropMath.Kind.NONE, DropMath.decide(true, 99_999, 2, ms, true, 10, 5000, 0.001, 0.002).kind());
    }

    @Test
    void firstFindDueSkipsNothing() {
        List<DropMath.Milestone> ms = rules.firstFinds();
        assertEquals(0, DropMath.firstFindDue(1500, 0, ms), "100 still pending comes before 1000");
        assertEquals(1, DropMath.firstFindDue(1500, 1, ms));
        assertEquals(-1, DropMath.firstFindDue(1500, 2, ms));
        assertEquals(-1, DropMath.firstFindDue(500, 1, ms));
    }

    @Test
    void nextGuaranteedIsTheNearerOfFirstFindAndPity() {
        List<DropMath.Milestone> ms = rules.firstFinds();
        assertEquals(60, DropMath.nextGuaranteed(40, 5000, 40, 0, ms));
        assertEquals(3760, DropMath.nextGuaranteed(1240, 5000, 2000, 2, ms));
        assertEquals(1, DropMath.nextGuaranteed(6000, 5000, 2000, 2, ms));
        assertEquals(Long.MAX_VALUE, DropMath.nextGuaranteed(10, 0, 2000, 2, ms));
        assertEquals(2, DropMath.nextGuaranteed(4998.5, 5000, 2000, 2, ms));
    }

    @Test
    void parsingSkipsBrokenEntries() {
        assertEquals(List.of(new DropMath.Milestone(10, Rarity.RARE), new DropMath.Milestone(50, Rarity.COMMON)),
                DropMath.parseMilestones(List.of("50:common", "x", "10:rare", "5:mythic", "0:common", "7:nope")));
        assertEquals(List.of(1, 0, 7), DropMath.parseInts(List.of("1", "a", "0", "-3", " 7 ")));
        assertEquals(3, DropMath.band(List.of(1, 4, 7, 10), 99));
        assertEquals("x", DropMath.byBand(List.of("a", "x"), 9, "f"));
        assertEquals("f", DropMath.byBand(List.<String>of(), 0, "f"));
    }

    @Test
    void bandRarityRollsFollowTheOdds() {
        EnchantRegistry registry = Fixtures.registry();
        SplittableRandom random = new SplittableRandom(42);
        Map<Rarity, Integer> counts = new EnumMap<>(Rarity.class);
        int n = 20_000;
        for (int i = 0; i < n; i++) {
            Rolls.roll(registry, Pool.MINING, rules.odds(0), 3, random).ifPresent(d -> counts.merge(d.rarity(), 1, Integer::sum));
        }
        assertEquals(0.70, counts.getOrDefault(Rarity.COMMON, 0) / (double) n, 0.02);
        assertEquals(0.26, counts.getOrDefault(Rarity.UNCOMMON, 0) / (double) n, 0.02);
        assertEquals(0.04, counts.getOrDefault(Rarity.RARE, 0) / (double) n, 0.01);
        // min tier: Vein Rend (Epic, mining tier 5) never comes from tier 4 even with Epic odds
        for (int i = 0; i < 2_000; i++) {
            Rolls.roll(registry, Pool.MINING, Map.of(Rarity.EPIC, 1.0), 4, random)
                    .ifPresent(d -> assertFalse(d.id().equals("vein_rend"), "vein_rend needs mining tier 5"));
        }
    }

    @Test
    void bookLevelsFollowBandOdds() {
        SplittableRandom random = new SplittableRandom(7);
        int ones = 0;
        int n = 20_000;
        for (int i = 0; i < n; i++) {
            int level = StackKillMath.bookLevel(rules.levelOdds(3), 5, random);
            assertTrue(level >= 1 && level <= 3);
            if (level == 1) {
                ones++;
            }
        }
        assertEquals(0.70, ones / (double) n, 0.02);
        assertEquals(1, StackKillMath.bookLevel(rules.levelOdds(0), 1, random), "never above the enchant's max");
    }
}
