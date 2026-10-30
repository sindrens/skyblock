package net.skybornmc.skyblock.achievements;

import net.skybornmc.skyblock.common.achievements.IslandBoost;
import net.skybornmc.skyblock.common.bonus.BonusFamily;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** AC2/AC3 tier points, boost caps and the category mapping (RANKS_TITLES_ACHIEVEMENTS §C2-§C4). */
class BoostMathTest {

    private final AchievementsConfig config = new AchievementsConfig();
    private final int[] perTier = config.perTier();
    private final AchievementRegistry registry = Fixtures.registry();

    @Test
    void tierPointsAreOneTwoThreeFiveEightAndTiersAboveFiveGiveEight() {
        assertEquals(List.of(1, 2, 3, 5, 8, 8, 8), List.of(1, 2, 3, 4, 5, 6, 7).stream().map(t -> BoostMath.tierPoints(perTier, t)).toList());
        assertEquals(0, BoostMath.tierPoints(perTier, 0));
        assertEquals(0, BoostMath.tierPoints(new int[0], 1));
    }

    @Test
    void achievementPointsSumTheUnlockedTiersOnly() {
        AchievementDef warrior = registry.get("warrior").orElseThrow(); // 5 tiers
        assertEquals(0, BoostMath.points(warrior, 0, perTier));
        assertEquals(6, BoostMath.points(warrior, 3, perTier));
        assertEquals(19, BoostMath.points(warrior, 5, perTier));
        assertEquals(19, BoostMath.points(warrior, 9, perTier), "never more than its tiers");
        AchievementDef overkill = registry.get("overkill").orElseThrow(); // one-step secret
        assertEquals(1, BoostMath.points(overkill, 1, perTier));
    }

    @Test
    void percentIsStepTimesPointsUpToTheCap() {
        assertEquals(0, BoostMath.percent(0, 0.3, 10));
        assertEquals(3.0, BoostMath.percent(10, 0.3, 10), 1e-9);
        assertEquals(10.0, BoostMath.percent(34, 0.3, 10), 1e-9, "34 x 0.3 = 10.2 is capped at 10");
        assertEquals(10.0, BoostMath.percent(1_000, 0.3, 10), 1e-9);
        assertEquals(0, BoostMath.percent(-5, 0.3, 10));
        assertEquals(0, BoostMath.percent(10, 0.3, 0), "cap 0 turns the boost off");
        assertEquals(34, BoostMath.pointsToCap(0.3, 10));
        assertEquals(25, BoostMath.pointsToCap(0.4, 10));
        assertEquals(24, BoostMath.pointsToCap(0.25, 6));
    }

    @Test
    void auctionSlotsAtTwelveAndThirtyPoints() {
        assertEquals(0, BoostMath.slots(11, 12, 30));
        assertEquals(1, BoostMath.slots(12, 12, 30));
        assertEquals(1, BoostMath.slots(29, 12, 30));
        assertEquals(2, BoostMath.slots(30, 12, 30));
        assertEquals(2, BoostMath.slots(500, 12, 30), "never more than +2");
        assertEquals(1, BoostMath.slots(500, 12, 0), "second step off");
        assertEquals(0, BoostMath.slots(500, 0, 0));
    }

    @Test
    void defaultCapsAreThePlanValues() {
        assertEquals(10, config.cap(PersonalBoost.SKILL_XP));
        assertEquals(10, config.cap(PersonalBoost.TOOL_XP));
        assertEquals(8, config.cap(PersonalBoost.MOB_DAMAGE));
        assertEquals(8, config.cap(PersonalBoost.RARE_FINDS));
        assertEquals(6, config.cap(PersonalBoost.CRYSTALS));
        assertEquals(2, config.cap(PersonalBoost.AH_SLOTS));
        assertEquals(8, config.islandCap(IslandBoost.PRODUCTION));
        assertEquals(6, config.islandCap(IslandBoost.CRYSTALS));
        assertEquals(5, config.islandCap(IslandBoost.ISLAND_XP));
        assertEquals(10, config.islandCap(IslandBoost.FUEL));
    }

    @Test
    void neverSellOrMoney() {
        assertNull(PersonalBoost.byFamily(BonusFamily.SELL), "personal achievements never boost sell (RTA-39)");
        for (PersonalBoost b : PersonalBoost.values()) {
            assertTrue(b.family() != BonusFamily.SELL);
        }
        assertEquals(PersonalBoost.SKILL_XP, PersonalBoost.byFamily(BonusFamily.SKILL_XP));
        assertEquals(PersonalBoost.MOB_DAMAGE, PersonalBoost.byFamily(BonusFamily.MOB_DAMAGE));
    }

    @Test
    void bundledCategoriesFeedThePlannedBoosts() {
        assertEquals(PersonalBoost.SKILL_XP, registry.category("skills").orElseThrow().boost());
        assertEquals(PersonalBoost.TOOL_XP, registry.category("gear").orElseThrow().boost());
        assertEquals(PersonalBoost.MOB_DAMAGE, registry.category("combat").orElseThrow().boost());
        assertEquals(PersonalBoost.RARE_FINDS, registry.category("social").orElseThrow().boost());
        assertEquals(PersonalBoost.AH_SLOTS, registry.category("economy").orElseThrow().boost());
        assertEquals(PersonalBoost.CRYSTALS, registry.category("island").orElseThrow().boost());
    }

    @Test
    void personalPointsAddUpPerBoostAcrossAchievements() {
        Map<String, Integer> unlocked = new HashMap<>();
        unlocked.put("prospector", 2); // skills: 1 + 2
        unlocked.put("warrior", 1);    // skills: 1
        unlocked.put("merchant", 4);   // economy: 1 + 2 + 3 + 5
        unlocked.put("first_sale", 1); // economy: 1
        unlocked.put("removed_one", 5); // not in the catalog: ignored
        Map<PersonalBoost, Integer> points = BoostMath.personalPoints(registry, unlocked, perTier);
        assertEquals(4, points.get(PersonalBoost.SKILL_XP));
        assertEquals(12, points.get(PersonalBoost.AH_SLOTS));
        assertEquals(1, BoostMath.slots(points.get(PersonalBoost.AH_SLOTS), config.ahSlotFirst, config.ahSlotSecond));
        assertEquals(Map.of("skills", 4, "economy", 12), BoostMath.categoryPoints(registry, unlocked, perTier));
    }

    @Test
    void profileProgressCachesItsPoints() {
        ProfileProgress p = new ProfileProgress(java.util.UUID.randomUUID(), java.util.UUID.randomUUID());
        p.load(Map.of(), Map.of("prospector", 5, "runesmith", 4));
        assertEquals(0, p.boostPoints(PersonalBoost.SKILL_XP), "nothing before the first recompute");
        p.points(registry, perTier);
        assertEquals(19, p.boostPoints(PersonalBoost.SKILL_XP));
        assertEquals(11, p.boostPoints(PersonalBoost.TOOL_XP));
        assertEquals(30, p.totalPoints());
        assertEquals(5.7, BoostMath.percent(p.boostPoints(PersonalBoost.SKILL_XP), config.step(PersonalBoost.SKILL_XP),
                config.cap(PersonalBoost.SKILL_XP)), 1e-9);
    }

    @Test
    void islandPointsAndBoosts() {
        Map<String, Integer> unlocked = Map.of("rising_island", 5, "deep_roots", 2, "gone", 3);
        int points = BoostMath.islandPoints(registry, unlocked, perTier);
        assertEquals(19 + 3, points);
        assertEquals(5.5, BoostMath.islandPercent(points, IslandBoost.PRODUCTION, config), 1e-9);
        assertEquals(4.4, BoostMath.islandPercent(points, IslandBoost.ISLAND_XP, config), 1e-9);
        assertEquals(10, BoostMath.islandPercent(points, IslandBoost.FUEL, config), 1e-9, "22 x 0.5 = 11, capped at 10");
        assertEquals(5, BoostMath.islandPercent(1_000, IslandBoost.ISLAND_XP, config), 1e-9);
    }

    @Test
    void percentText() {
        assertEquals("0", BoostMath.text(0));
        assertEquals("4.5", BoostMath.text(4.5));
        assertEquals("10", BoostMath.text(10.0));
        assertEquals("0.25", BoostMath.text(0.25));
        assertEquals("5.7", BoostMath.text(19 * 0.3));
    }
}
