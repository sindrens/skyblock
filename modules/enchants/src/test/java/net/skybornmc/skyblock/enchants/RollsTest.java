package net.skybornmc.skyblock.enchants;

import net.skybornmc.skyblock.common.item.Rarity;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Book shop rolls (ENCHANTS §3.5): odds, min tier filter, fallback, no Mythics (seeded random). */
class RollsTest {

    private final EnchantRegistry registry = Fixtures.registry();

    @Test
    void oddsParse() {
        assertEquals(Map.entry(Rarity.RARE, 50.0), Rolls.parseOdd("rare:50"));
        assertNull(Rolls.parseOdd("mythic:5"), "rolls never give Mythics");
        assertNull(Rolls.parseOdd("rare"));
        assertNull(Rolls.parseOdd("shiny:5"));
        assertNull(Rolls.parseOdd("rare:-1"));
        Map<Rarity, Double> odds = Rolls.parseOdds(List.of("common:70", "uncommon:30", "bad"));
        assertEquals(2, odds.size());
    }

    @Test
    void rarityDistributionFollowsTheOdds() {
        Map<Rarity, Double> odds = Rolls.parseOdds(List.of("uncommon:40", "rare:50", "epic:10"));
        SplittableRandom random = new SplittableRandom(42);
        Map<Rarity, Integer> counts = new EnumMap<>(Rarity.class);
        int n = 100_000;
        for (int i = 0; i < n; i++) {
            counts.merge(Rolls.rarity(odds, random).orElseThrow(), 1, Integer::sum);
        }
        assertEquals(0.40, counts.get(Rarity.UNCOMMON) / (double) n, 0.01);
        assertEquals(0.50, counts.get(Rarity.RARE) / (double) n, 0.01);
        assertEquals(0.10, counts.get(Rarity.EPIC) / (double) n, 0.01);
        assertTrue(Rolls.rarity(Map.of(), random).isEmpty());
    }

    @Test
    void minTierAndPoolFilter() {
        // Mining Epics: phoenix_haste (T4), treasure_hunter (T6), vein_rend (T5)
        assertEquals(List.of(), Rolls.candidates(registry, Pool.MINING, Rarity.EPIC, 3));
        assertEquals(List.of("phoenix_haste"), Rolls.candidates(registry, Pool.MINING, Rarity.EPIC, 4).stream().map(EnchantDef::id).toList());
        assertEquals(3, Rolls.candidates(registry, Pool.MINING, Rarity.EPIC, 6).size());
        assertTrue(Rolls.candidates(registry, Pool.MINING, Rarity.EPIC, 12).stream().allMatch(d -> d.pools().contains(Pool.MINING)));
        assertTrue(Rolls.candidates(registry, Pool.COMBAT, Rarity.MYTHIC, 99).isEmpty());
    }

    @Test
    void fallsBackOneRarityWhenNothingFits() {
        SplittableRandom random = new SplittableRandom(7);
        // Legendary at mining tier 1: nothing allowed → Epic (none at T1) → Rare (geode_sense T1) …
        Optional<EnchantDef> pick = Rolls.pick(registry, Pool.MINING, Rarity.LEGENDARY, 1, random);
        assertTrue(pick.isPresent());
        assertEquals(Rarity.RARE, pick.get().rarity());
        assertEquals("geode_sense", pick.get().id());
    }

    @Test
    void masterRollsNeverGiveMythicsAndRespectTiers() {
        Map<Rarity, Double> odds = Rolls.parseOdds(List.of("rare:40", "epic:50", "legendary:10"));
        SplittableRandom random = new SplittableRandom(1);
        boolean sawLegendary = false;
        for (int i = 0; i < 5_000; i++) {
            EnchantDef d = Rolls.roll(registry, Pool.MINING, odds, 7, random).orElseThrow();
            assertFalse(d.mythic());
            assertTrue(d.minTier() <= 7, d.id());
            assertTrue(d.pools().contains(Pool.MINING), d.id());
            sawLegendary |= d.id().equals("dragonfire_blast");
        }
        assertTrue(sawLegendary);
    }

    @Test
    void tiersFromSkillLevels() {
        List<Integer> mine = new EnchanterConfig().tierLevels(Pool.MINING);
        assertEquals(1, Rolls.tier(mine, 0));
        assertEquals(1, Rolls.tier(mine, 4));
        assertEquals(2, Rolls.tier(mine, 5));
        assertEquals(4, Rolls.tier(mine, 15));
        assertEquals(12, Rolls.tier(mine, 150));
        assertEquals(1, Rolls.tier(List.of(), 50));
        assertNotNull(new EnchanterConfig().roll(RollType.MASTER));
        assertEquals(25_000, new EnchanterConfig().roll(RollType.MASTER).price());
        assertEquals(7, new EnchanterConfig().roll(RollType.MASTER).minTier());
        assertEquals(3, new EnchanterConfig().roll(RollType.MASTER).daily());
    }
}
