package net.skybornmc.skyblock.progression;

import net.skybornmc.skyblock.common.progression.Pillar;
import net.skybornmc.skyblock.common.skills.SkillType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which XP counts, the ISLAND_XP family, milestones and config parsing (PHASE-5 M5.1). */
class PillarRulesTest {

    private final PillarsConfig defaults = new PillarsConfig();

    private static Map<Pillar, Integer> levels(int mining, int foraging, int farming, int combat, int conquest) {
        Map<Pillar, Integer> m = new EnumMap<>(Pillar.class);
        m.put(Pillar.MINING, mining);
        m.put(Pillar.FORAGING, foraging);
        m.put(Pillar.FARMING, farming);
        m.put(Pillar.COMBAT, combat);
        m.put(Pillar.CONQUEST, conquest);
        return m;
    }

    // ------------------------------------------------------------------ exclusions

    @Test
    void onlyBaseXpCountsNeverBoosts() {
        // Rank/booster/enchant boosts raised 10 → 15: the pillar gets the base 10 (RK20, P5-6).
        assertEquals(10, PillarRules.baseXp(10, 15), 1e-9);
        // A listener lowered the gain: never more than the player really got.
        assertEquals(4, PillarRules.baseXp(10, 4), 1e-9);
        // Zeroed or broken gains give nothing.
        assertEquals(0, PillarRules.baseXp(10, 0), 1e-9);
        assertEquals(0, PillarRules.baseXp(0, 10), 1e-9);
        assertEquals(0, PillarRules.baseXp(Double.POSITIVE_INFINITY, 10), 1e-9);
        assertEquals(0, PillarRules.baseXp(10, Double.NaN), 1e-9);
    }

    @Test
    void stackKillsAreExcluded() {
        Set<String> excluded = PillarRules.lowerSet(defaults.excludedSources);
        assertTrue(PillarRules.excluded("stack_kill", excluded));
        assertTrue(PillarRules.excluded(" STACK_KILL ", excluded));
        assertFalse(PillarRules.excluded("mob_kill", excluded));
        assertFalse(PillarRules.excluded("depths_block", excluded));
        assertFalse(PillarRules.excluded(null, excluded));
    }

    @Test
    void spawnerKillsOnlyByThePlayersOwnHit() {
        Set<String> causes = PillarRules.upperSet(defaults.spawnerKillCauses);
        assertTrue(PillarRules.killCounts("ENTITY_ATTACK", true, causes));
        assertTrue(PillarRules.killCounts("ENTITY_SWEEP_ATTACK", true, causes));
        assertTrue(PillarRules.killCounts("PROJECTILE", true, causes));
        // Fall, lava, fire, suffocation: AFK farms give 0 (P5-7).
        assertFalse(PillarRules.killCounts("FALL", false, causes));
        assertFalse(PillarRules.killCounts("LAVA", false, causes));
        assertFalse(PillarRules.killCounts("FIRE_TICK", false, causes));
        // Hit by another mob or player (not the killer).
        assertFalse(PillarRules.killCounts("ENTITY_ATTACK", false, causes));
        assertFalse(PillarRules.killCounts(null, true, causes));
    }

    // ------------------------------------------------------------------ parsing

    @Test
    void defaultSkillSources() {
        List<String> problems = new ArrayList<>();
        Map<SkillType, PillarRules.SkillSource> sources = PillarRules.skillSources(defaults.skillSources, problems::add);
        assertTrue(problems.isEmpty(), problems.toString());
        assertEquals(Pillar.MINING, sources.get(SkillType.MINING).pillar());
        assertEquals(Pillar.FORAGING, sources.get(SkillType.FORAGING).pillar());
        assertEquals(Pillar.COMBAT, sources.get(SkillType.COMBAT).pillar());
        assertEquals(Pillar.FARMING, sources.get(SkillType.FARMING).pillar());
        assertEquals(Pillar.FARMING, sources.get(SkillType.FISHING).pillar());
        assertEquals(1.0, sources.get(SkillType.MINING).multiplier());
    }

    @Test
    void brokenSkillSourcesAreReported() {
        List<String> problems = new ArrayList<>();
        Map<SkillType, PillarRules.SkillSource> sources = PillarRules.skillSources(
                List.of("mining: mining 0.5", "cooking: farming", "combat: nothing", "foraging: foraging -1", "nonsense", "fishing: farming"),
                problems::add);
        assertEquals(Map.of(SkillType.MINING, new PillarRules.SkillSource(Pillar.MINING, 0.5),
                SkillType.FISHING, new PillarRules.SkillSource(Pillar.FARMING, 1.0)), sources);
        assertEquals(4, problems.size());
    }

    @Test
    void numberListsAndMilestones() {
        List<String> problems = new ArrayList<>();
        Map<String, Double> crops = PillarRules.numbers(List.of("minecraft:wheat: 0.25", "CARROTS: 1", "potatoes: x", "beets: -1"),
                "test", problems::add);
        assertEquals(Map.of("wheat", 0.25, "carrots", 1.0), crops);
        assertEquals(2, problems.size());

        problems.clear();
        assertEquals(List.of(10, 25, 40, 60), PillarRules.levels(defaults.milestoneLevels, problems::add));
        assertEquals(List.of(5, 10), PillarRules.levels(List.of("10", "5", "5", "0", "1.5", "x"), problems::add));
        assertEquals(3, problems.size());

        problems.clear();
        Map<Integer, Long> rewards = PillarRules.rewards(defaults.milestoneCrystals, problems::add);
        assertTrue(problems.isEmpty(), problems.toString());
        assertEquals(Map.of(10, 500L, 25, 1500L, 40, 4000L, 60, 10000L), rewards);
    }

    // ------------------------------------------------------------------ shown pillars, balance bonus, family cap

    @Test
    void conquestHiddenUntilEnabled() {
        assertEquals(List.of(Pillar.MINING, Pillar.FORAGING, Pillar.FARMING, Pillar.COMBAT), PillarRules.shown(false));
        assertEquals(List.of(Pillar.values()), PillarRules.shown(true));
        // "All pillars" ignores the hidden Conquest at 0 (P5-8).
        Map<Pillar, Integer> l = levels(12, 15, 11, 30, 0);
        assertEquals(11, PillarRules.lowest(l, PillarRules.shown(false)));
        assertEquals(0, PillarRules.lowest(l, PillarRules.shown(true)));
        assertEquals(68, PillarRules.total(l, PillarRules.shown(false)));
    }

    @Test
    void balanceBonus() {
        List<Pillar> four = PillarRules.shown(false);
        // Fresh island: all four within 10 of the highest → 4 × 2% = 8%.
        assertEquals(8, PillarRules.balanceBonus(levels(0, 0, 0, 0, 0), four, 10, 2, 10), 1e-9);
        // Combat ran ahead: only Combat itself and Mining (exactly 10 below) count.
        assertEquals(4, PillarRules.balanceBonus(levels(30, 19, 5, 40, 0), four, 10, 2, 10), 1e-9);
        // Five shown pillars: capped at the max 10%.
        assertEquals(10, PillarRules.balanceBonus(levels(20, 20, 20, 20, 20), PillarRules.shown(true), 10, 2, 10), 1e-9);
        assertEquals(10, PillarRules.balanceBonus(levels(20, 20, 20, 20, 20), PillarRules.shown(true), 10, 3, 10), 1e-9);
        assertEquals(0, PillarRules.balanceBonus(levels(1, 1, 1, 1, 1), four, 10, 0, 10), 1e-9);
    }

    @Test
    void islandXpFamilyIsCappedOnce() {
        assertEquals(13, PillarRules.familyPercent(8, 5, 25), 1e-9);
        assertEquals(25, PillarRules.familyPercent(10, 40, 25), 1e-9);
        assertEquals(0, PillarRules.familyPercent(0, -20, 25), 1e-9);
        assertEquals(8, PillarRules.familyPercent(8, Double.NaN, 25), 1e-9);
        assertEquals(1.25, PillarRules.multiplier(25), 1e-9);
        assertEquals(1.0, PillarRules.multiplier(-3), 1e-9);
    }

    // ------------------------------------------------------------------ milestones and weeks

    @Test
    void milestonesReachedOnceInOrder() {
        List<Integer> ms = List.of(10, 25, 40, 60);
        assertEquals(List.of(), PillarRules.newlyReached(ms, 9, Set.of()));
        assertEquals(List.of(10), PillarRules.newlyReached(ms, 10, Set.of()));
        assertEquals(List.of(10, 25), PillarRules.newlyReached(ms, 30, Set.of()));
        assertEquals(List.of(25), PillarRules.newlyReached(ms, 30, Set.of(10)));
        assertEquals(List.of(), PillarRules.newlyReached(ms, 30, Set.of(10, 25)));
    }

    @Test
    void weeksStartOnMonday() {
        LocalDate monday = LocalDate.of(2026, 10, 26);
        int week = (int) monday.toEpochDay();
        assertEquals(week, PillarRules.weekStart(monday));
        assertEquals(week, PillarRules.weekStart(LocalDate.of(2026, 10, 30)));
        assertEquals(week, PillarRules.weekStart(LocalDate.of(2026, 11, 1)));
        assertEquals(week + 7, PillarRules.weekStart(LocalDate.of(2026, 11, 2)));
    }

    @Test
    void notifyHelpers() {
        assertTrue(PillarManager.crossedMultiple(9, 10, 10));
        assertTrue(PillarManager.crossedMultiple(8, 21, 10));
        assertFalse(PillarManager.crossedMultiple(10, 19, 10));
        assertFalse(PillarManager.crossedMultiple(1, 50, 0));
        assertEquals(99, PillarManager.percent(0.999));
        assertEquals(100, PillarManager.percent(1.5));
        assertEquals("8", PillarManager.trim(8.0));
        assertEquals("12.5", PillarManager.trim(12.5));
    }

    @Test
    void idempotencyKeyFitsTheLedgerLimit() {
        String key = PillarManager.idempotencyKey(Integer.MAX_VALUE, java.util.UUID.randomUUID(), 10_000, java.util.UUID.randomUUID());
        assertTrue(key.length() <= 96, key.length() + ": " + key);
        assertTrue(key.matches("pillar_ms:\\d+:[0-9a-f]{32}:\\d+:[0-9a-f]{32}"), key);
    }
}
