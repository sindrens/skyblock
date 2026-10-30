package net.skybornmc.skyblock.achievements;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** AC3: shared island progress, unlock once per island, team-only rules, title days, storage merge. */
class IslandProgressionTest {

    private final AchievementRegistry registry = Fixtures.registry();
    private final int[] perTier = new AchievementsConfig().perTier();

    private IslandProgress loaded() {
        IslandProgress p = new IslandProgress(UUID.randomUUID());
        p.load(Map.of(), Map.of());
        return p;
    }

    private static List<String> ids(List<IslandProgression.Unlock> unlocks) {
        return unlocks.stream().map(u -> u.def().id() + ":" + u.tier()).toList();
    }

    @Test
    void bundledIslandAchievementsLoad() {
        assertEquals(List.of("rising_island", "deep_roots", "stone_heart", "open_doors", "loved_isle", "team_flame"),
                registry.island().stream().map(IslandAchievementDef::id).toList());
        assertTrue(registry.islandAchievement("team_flame").orElseThrow().teamOnly());
        assertTrue(registry.island().stream().allMatch(IslandAchievementDef::hasTitle), "every island achievement has a member title");
    }

    @Test
    void sharedMemberSumsUnlockEachTierOnceForTheWholeIsland() {
        IslandProgress p = loaded();
        // Member A mined 6,000, member B 5,000: the island has 11,000 (gauge = the sum of every member key).
        assertEquals(List.of("stone_heart:1"), ids(IslandProgression.apply(p, registry, IslandMetric.BLOCKS_MINED, 11_000, false)));
        assertEquals(List.of(), ids(IslandProgression.apply(p, registry, IslandMetric.BLOCKS_MINED, 12_000, false)));
        // A member leaves: the sum read next time is lower, but the island keeps its progress.
        assertEquals(List.of(), ids(IslandProgression.apply(p, registry, IslandMetric.BLOCKS_MINED, 5_000, false)));
        assertEquals(12_000, p.value(IslandMetric.BLOCKS_MINED));
        assertEquals(1, p.unlocked("stone_heart"));
    }

    @Test
    void lastTierCarriesTheTitle() {
        IslandProgress p = loaded();
        List<IslandProgression.Unlock> unlocks = IslandProgression.apply(p, registry, IslandMetric.ISLAND_LEVEL, 100, false);
        assertEquals(List.of("rising_island:1", "rising_island:2", "rising_island:3", "rising_island:4", "rising_island:5"), ids(unlocks));
        assertNull(unlocks.get(3).title());
        assertTrue(unlocks.getLast().last());
        assertEquals("island_ascendant", unlocks.getLast().title());
        p.points(registry, perTier);
        assertEquals(19, p.points());
    }

    @Test
    void likesAreACounter() {
        IslandProgress p = loaded();
        for (int i = 0; i < 9; i++) {
            assertEquals(List.of(), ids(IslandProgression.apply(p, registry, IslandMetric.LIKES, 1, false)));
        }
        assertEquals(List.of("loved_isle:1"), ids(IslandProgression.apply(p, registry, IslandMetric.LIKES, 1, false)));
        assertEquals(10, p.value(IslandMetric.LIKES));
    }

    @Test
    void teamOnlyAchievementsNeedATeamIsland() {
        IslandProgress solo = loaded();
        assertEquals(List.of(), ids(IslandProgression.apply(solo, registry, IslandMetric.TEAM_ACTIVE, 5, false)));
        IslandProgress team = loaded();
        assertEquals(List.of("team_flame:1", "team_flame:2", "team_flame:3", "team_flame:4"),
                ids(IslandProgression.apply(team, registry, IslandMetric.TEAM_ACTIVE, 5, true)));
        int[] soloCounts = IslandProgression.tierCounts(solo, registry, false);
        int[] teamCounts = IslandProgression.tierCounts(team, registry, true);
        assertEquals(teamCounts[1] - 4, soloCounts[1], "solo islands don't count Team Flame's tiers");
    }

    @Test
    void nothingUnlocksBeforeTheLoadAndStoredValuesMerge() {
        IslandProgress p = new IslandProgress(UUID.randomUUID());
        assertEquals(List.of(), ids(IslandProgression.apply(p, registry, IslandMetric.LIKES, 4, false)));
        p.load(Map.of(IslandMetric.LIKES, 7L, IslandMetric.ISLAND_LEVEL, 30L), Map.of("rising_island", 2));
        assertEquals(11, p.value(IslandMetric.LIKES), "counter: stored + earned while loading");
        assertEquals(List.of("loved_isle:1"), ids(IslandProgression.evaluateAll(p, registry, false)));
        assertEquals(List.of(), ids(IslandProgression.evaluateAll(p, registry, false)), "stored tiers never unlock again");
        List<IslandProgress.Row> rows = p.drain();
        assertEquals(List.of(new IslandProgress.Row(IslandMetric.LIKES, 4)), rows, "only the delta is written");
    }

    @Test
    void titlesOnlyForMembersWithEnoughDays() {
        long now = 10L * 86_400_000L;
        assertFalse(IslandProgression.titleEligible(OptionalLong.empty(), now, 3), "coop players and visitors have no join time");
        assertFalse(IslandProgression.titleEligible(OptionalLong.of(now - 2L * 86_400_000L), now, 3));
        assertTrue(IslandProgression.titleEligible(OptionalLong.of(now - 3L * 86_400_000L), now, 3));
        assertTrue(IslandProgression.titleEligible(OptionalLong.of(now), now, 0));
    }
}
