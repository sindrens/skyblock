package net.skybornmc.skyblock.enchants;

import net.skybornmc.skyblock.common.item.Rarity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Stacked-mob enchant math (ENCHANTS §6.6, §7, §13.3): kill counts, Bloodlust, sweeps, Wrath, family caps, book cap. */
class StackKillMathTest {

    private static final int MAX = 96;

    // ------------------------------------------------------------------ Slaughter

    @Test
    void slaughterCountsFollowThePlan() {
        EnchantDef slaughter = Fixtures.def("slaughter");
        int[] plan = {2, 3, 4, 6, 8, 12, 16, 24, 32, 48};
        assertEquals(10, slaughter.maxLevel());
        for (int level = 1; level <= 10; level++) {
            int base = (int) slaughter.value("count", level);
            assertEquals(plan[level - 1], base, "Slaughter " + level);
            assertEquals(plan[level - 1], StackKillMath.kills(base, 0, MAX), "no Bloodlust: exactly the Slaughter count");
        }
    }

    @Test
    void withoutSlaughterOneMobDies() {
        assertEquals(1, StackKillMath.kills(1, 0, MAX));
        assertEquals(1, StackKillMath.kills(0, 0, MAX), "a broken base still kills one");
        assertEquals(1, StackKillMath.kills(-5, Double.NaN, MAX));
    }

    @Test
    void bloodlustAddsAtLeastOneMob() {
        assertEquals(2, StackKillMath.kills(1, 10, MAX), "10% of 1 rounds down to 0, min +1 mob");
        assertEquals(9, StackKillMath.kills(8, 10, MAX), "Slaughter V + 10%");
        assertEquals(72, StackKillMath.kills(48, 50, MAX), "Slaughter X + Bloodlust V max = 48 + 24");
        assertEquals(12, StackKillMath.kills(8, 50, MAX));
    }

    @Test
    void killsAreClampedPerHit() {
        assertEquals(50, StackKillMath.kills(48, 50, 50));
        assertEquals(1, StackKillMath.kills(48, 50, 0), "a broken clamp still kills one");
        assertEquals(1024, StackKillMath.kills(1000, 100, 1024));
    }

    @Test
    void bloodlustPercentGrowsPerHitUpToTheLevelMax() {
        assertEquals(0, StackKillMath.bloodlustPercent(0, 10, 50));
        assertEquals(10, StackKillMath.bloodlustPercent(1, 10, 50));
        assertEquals(30, StackKillMath.bloodlustPercent(3, 10, 50));
        assertEquals(50, StackKillMath.bloodlustPercent(9, 10, 50));
        assertEquals(10, StackKillMath.bloodlustPercent(9, 10, 10), "Bloodlust I caps at +10%");
        assertEquals(0, StackKillMath.bloodlustPercent(3, 0, 50));
    }

    @Test
    void streakResetsAfterTheWindow() {
        long window = 2_000;
        StackKillMath.Streak s = StackKillMath.Streak.NONE;
        assertEquals(0, s.prior(1_000, window), "first hit has no streak");
        s = s.hit(1_000, window);
        assertEquals(1, s.prior(2_500, window));
        s = s.hit(2_500, window);
        assertEquals(2, s.prior(4_500, window), "exactly at the window still counts");
        s = s.hit(4_500, window);
        assertEquals(3, s.hits());
        assertEquals(0, s.prior(6_501, window), "more than 2 s idle resets");
        s = s.hit(6_501, window);
        assertEquals(1, s.hits(), "a new streak starts with this hit");
        assertEquals(0, s.prior(100, window), "clock going back never counts");
    }

    @Test
    void streakWithLevelValuesFromTheRegistry() {
        EnchantDef bloodlust = Fixtures.def("bloodlust");
        assertEquals(2.0, bloodlust.value("window", 1));
        assertEquals(50.0, bloodlust.value("percent", 5));
        long window = (long) (bloodlust.value("window", 5) * 1000);
        StackKillMath.Streak s = StackKillMath.Streak.NONE;
        int[] expected = {32, 35, 38, 41, 44, 48, 48};
        long now = 0;
        for (int hit = 0; hit < expected.length; hit++) {
            double bonus = StackKillMath.bloodlustPercent(s.prior(now, window), 10, bloodlust.value("percent", 5));
            assertEquals(expected[hit], StackKillMath.kills(32, bonus, MAX), "hit " + hit);
            s = s.hit(now, window);
            now += 500;
        }
    }

    // ------------------------------------------------------------------ Reaper's Sweep, Phoenix Wrath

    @Test
    void sweepKillsAShareOfTheCount() {
        EnchantDef sweep = Fixtures.def("reapers_sweep");
        assertEquals(1, StackKillMath.sweepKills(1, sweep.value("percent", 1), MAX), "min 1");
        assertEquals(12, StackKillMath.sweepKills(48, sweep.value("percent", 1), MAX));
        assertEquals(24, StackKillMath.sweepKills(48, sweep.value("percent", 2), MAX));
        assertEquals(36, StackKillMath.sweepKills(48, sweep.value("percent", 3), MAX));
        assertEquals(10, StackKillMath.sweepKills(48, 75, 10), "clamped per hit");
        assertEquals(0, StackKillMath.sweepKills(48, 0, MAX));
        assertEquals(List.of(2.0, 4.0, 6.0), sweep.values().get("stacks"));
        assertEquals(4.0, sweep.value("radius", 2));
    }

    @Test
    void targetsAreNearestFirstFilteredAndLimited() {
        List<String> nearest = List.of("hit", "a", "other-island", "b", "c", "d");
        List<String> picked = StackKillMath.targets(nearest, "hit", s -> !s.equals("other-island"), 2);
        assertEquals(List.of("a", "b"), picked);
        assertEquals(List.of("a", "b", "c", "d"), StackKillMath.targets(nearest, "hit", s -> !s.startsWith("other"), 10));
        assertEquals(List.of(), StackKillMath.targets(nearest, "hit", s -> true, 0));
        assertFalse(StackKillMath.targets(nearest, "hit", s -> true, 6).contains("hit"), "never the hit stack itself");
    }

    @Test
    void wrathFiresEveryTwentiethHit() {
        int counter = 0;
        int fired = 0;
        for (int hit = 1; hit <= 60; hit++) {
            StackKillMath.Counter c = StackKillMath.wrathHit(counter, 20);
            counter = c.value();
            if (c.fires()) {
                fired++;
                assertEquals(0, hit % 20, "fires on hit " + hit);
            }
        }
        assertEquals(3, fired);
        assertFalse(StackKillMath.wrathHit(5, 0).fires(), "every 0 = off");
        assertEquals(20.0, Fixtures.def("phoenix_wrath").value("every", 1));
    }

    // ------------------------------------------------------------------ families

    @Test
    void plunderJoinsTheExtraDropsFamilyAndItsCap() {
        assertEquals(20, StackKillMath.extraLootPercent(0, 20, 200));
        assertEquals(70, StackKillMath.extraLootPercent(50, 20, 200), "other sources add up");
        assertEquals(30, StackKillMath.extraLootPercent(50, 20, 30), "family cap applies once");
        assertEquals(100, StackKillMath.extraLootPercent(150, 20, 200), "never more than one extra roll per mob");
        assertEquals(0, StackKillMath.extraLootPercent(0, Double.NaN, 200));
        assertEquals(0, StackKillMath.extraLootPercent(-500, 20, 200), "never negative");
    }

    @Test
    void soulReaperJoinsTheSkillXpFamilyAndItsCap() {
        assertEquals(1.30, StackKillMath.soulReaperMultiplier(0, 30, 300), 1e-9);
        assertEquals(1.55, StackKillMath.soulReaperMultiplier(25, 30, 300), 1e-9, "Scholar's Insight + Soul Reaper");
        assertEquals(4.0, StackKillMath.soulReaperMultiplier(290, 30, 300), 1e-9, "capped at +300%");
        EnchantDef soul = Fixtures.def("soul_reaper");
        assertEquals(30.0, soul.value("skill", 5));
        assertEquals(50.0, soul.value("xp", 5));
        assertEquals(0, soul.passiveValue(5), "Soul Reaper is not a passive bonus (only stacked kills)");
        assertEquals(0, Fixtures.def("plunder").passiveValue(4), "Plunder is not a passive bonus");
    }

    // ------------------------------------------------------------------ island stack books

    @Test
    void booksAreCappedByWhatIsLeftToday() {
        RandomGenerator always = new Random(1);
        assertEquals(5, StackKillMath.books(1_000, 1, 5, always), "every mob would drop one: the cap holds");
        assertEquals(3, StackKillMath.books(3, 1, 5, always));
        assertEquals(0, StackKillMath.books(1_000, 1, 0, always), "cap used up");
        assertEquals(0, StackKillMath.books(1_000, 0, 5, always), "chance 0 = off");
        assertEquals(0, StackKillMath.books(0, 1, 5, always));
    }

    @Test
    void bookChanceMatchesOneInN() {
        Random random = new Random(42);
        long found = 0;
        int batches = 2_000;
        for (int i = 0; i < batches; i++) {
            found += StackKillMath.books(1_000, 1_000, 1_000, random);
        }
        // expectation 1 per batch of 1,000 mobs at 1 in 1,000
        assertTrue(found > batches * 0.9 && found < batches * 1.1, "found " + found);
    }

    @Test
    void bookLevelsByWeightNeverAboveMax() {
        Random random = new Random(7);
        int[] counts = new int[4];
        for (int i = 0; i < 10_000; i++) {
            counts[StackKillMath.bookLevel(List.of("1:85", "2:12", "3:3"), 5, random)]++;
        }
        assertTrue(counts[1] > 8_000 && counts[2] > 900 && counts[3] > 150, java.util.Arrays.toString(counts));
        for (int i = 0; i < 100; i++) {
            assertTrue(StackKillMath.bookLevel(List.of("3:1"), 2, random) <= 2);
        }
        assertEquals(1, StackKillMath.bookLevel(List.of("bad", "0:5", "11:3"), 5, random), "nothing valid = level 1");
        assertNull(StackKillMath.parseLevelOdd("x:1"));
        assertNull(StackKillMath.parseLevelOdd("2:-1"));
        assertEquals(2, StackKillMath.parseLevelOdd(" 2 : 12 ").getKey());
    }

    @Test
    void stackPoolHoldsOnlyTheStackEnchantsAndPlunder() {
        EnchantRegistry registry = Fixtures.registry();
        Set<String> ids = new java.util.TreeSet<>();
        for (Rarity r : Rarity.values()) {
            Rolls.candidates(registry, Pool.STACKS, r, 99).forEach(d -> ids.add(d.id()));
        }
        assertEquals(new java.util.TreeSet<>(Set.of("slaughter", "soul_reaper", "bloodlust", "plunder")), ids);
        Random random = new Random(3);
        for (int i = 0; i < 200; i++) {
            assertTrue(Rolls.roll(registry, Pool.STACKS, Rolls.parseOdds(List.of("uncommon:60", "rare:40")), 1, random).isPresent(),
                    "tier 1 always finds a stack book (falls back to Uncommon)");
        }
    }
}
