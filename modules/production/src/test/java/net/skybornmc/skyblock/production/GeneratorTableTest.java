package net.skybornmc.skyblock.production;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PHASE-7 M7.2: the generator level table (PD6) parses from the defaults and rolls by weight. */
class GeneratorTableTest {

    private static GeneratorTable defaults(List<String> problems) {
        GeneratorSettings s = new GeneratorSettings();
        return GeneratorTable.parse(s.ores, s.levels, problems::add);
    }

    @Test
    void defaultsParseWithoutProblemsAndMatchThePlan() {
        List<String> problems = new ArrayList<>();
        GeneratorTable t = defaults(problems);
        assertEquals(List.of(), problems);
        assertEquals(10, t.maxLevel());
        assertEquals(11, t.ores().size());
        assertEquals(1.0, t.chance(0, "cobble"), 1e-9);
        assertFalse(t.rolls(0), "level 0 is pure cobble: no roll at all");
        assertEquals(0.10, t.chance(1, "coal"), 1e-9);
        assertEquals(0.05, t.chance(9, "ashstone"), 1e-9);
        assertEquals(0.07, t.chance(10, "ashstone"), 1e-9);
        assertEquals(0.05, t.chance(10, "emberite"), 1e-9);
        assertEquals(0.40, t.chance(10, "cobble"), 1e-9);
        assertTrue(t.ores().get("ashstone").custom());
        assertEquals("flint", t.ores().get("ashstone").item());
        assertFalse(t.ores().get("coal").custom(), "vanilla ores drop vanilla items");
        assertTrue(t.ores().get("cobble").keep());
    }

    @Test
    void everyDefaultLevelAddsUpToOneHundred() {
        GeneratorSettings s = new GeneratorSettings();
        for (String line : s.levels) {
            int sum = 0;
            for (String part : line.substring(line.indexOf(':') + 1).trim().split("\\s+")) {
                sum += Integer.parseInt(part.substring(part.indexOf('=') + 1));
            }
            assertEquals(100, sum, line);
        }
    }

    @Test
    void newestOreAppearsAtFivePercentPerLevel() {
        GeneratorTable t = defaults(new ArrayList<>());
        String[] newest = {"coal", "copper", "iron", "gold", "redstone", "lapis", "diamond", "emerald", "ashstone", "emberite"};
        for (int level = 1; level <= 10; level++) {
            String ore = newest[level - 1];
            assertEquals(0, t.chance(level - 1, ore), 1e-9, ore + " not before level " + level);
            assertTrue(t.chance(level, ore) >= 0.05 - 1e-9, ore + " at level " + level);
        }
    }

    @Test
    void rollPicksByCumulativeWeight() {
        GeneratorTable t = defaults(new ArrayList<>());
        assertEquals("cobble", t.roll(1, 0.0).id());
        assertEquals("cobble", t.roll(1, 0.8999).id());
        assertEquals("coal", t.roll(1, 0.90).id());
        assertEquals("coal", t.roll(1, 0.99999).id());
        assertEquals("coal", t.roll(1, 1.0).id(), "1.0 is clamped inside the table");
        assertEquals("cobble", t.roll(1, -3).id(), "negative is clamped to the start");
        assertEquals("emberite", t.roll(10, 0.9999).id());
    }

    @Test
    void rollFrequenciesFollowTheWeights() {
        GeneratorTable t = defaults(new ArrayList<>());
        SplittableRandom rng = new SplittableRandom(7);
        Map<String, Integer> counts = new HashMap<>();
        int n = 200_000;
        for (int i = 0; i < n; i++) {
            counts.merge(t.roll(10, rng.nextDouble()).id(), 1, Integer::sum);
        }
        for (String ore : t.ores().keySet()) {
            double expected = t.chance(10, ore);
            double got = counts.getOrDefault(ore, 0) / (double) n;
            assertEquals(expected, got, 0.005, ore);
        }
    }

    @Test
    void levelsAboveTheTableUseTheHighestAndGapsUseTheLevelBelow() {
        GeneratorTable t = GeneratorTable.parse(List.of("cobble: block=keep", "coal: block=coal_ore tier=1 depths-xp=1"),
                List.of("0: cobble=1", "3: cobble=1 coal=1"), p -> { });
        assertEquals(0, t.chance(2, "coal"), 1e-9, "level 2 falls back to level 0");
        assertEquals(0.5, t.chance(3, "coal"), 1e-9);
        assertEquals(0.5, t.chance(25, "coal"), 1e-9, "level 25 uses level 3");
        assertEquals(3, t.maxLevel());
        assertTrue(t.rolls(99));
    }

    @Test
    void brokenLinesAreReportedAndSkipped() {
        List<String> problems = new ArrayList<>();
        GeneratorTable t = GeneratorTable.parse(
                List.of("coal: block=coal_ore tier=x", "nonsense", "coal: block=iron_ore", "bad: tier=1", "odd: block=keep item=flint"),
                List.of("1: coal=10 ghost=5", "x: coal=1", "2: coal=0", "1: coal=3"), problems::add);
        assertEquals("odd", t.keep().id(), "the listed keep entry is the plain block");
        assertTrue(GeneratorTable.parse(List.of(), List.of(), p -> { }).keep().keep(), "a keep entry is always there");
        assertTrue(t.ores().containsKey("coal"));
        assertFalse(t.ores().containsKey("bad"));
        assertNull(t.ores().get("odd").item(), "keep can't be a custom ore");
        assertEquals(1.0, t.chance(1, "coal"), 1e-9, "unknown ore skipped, the first level 1 wins");
        assertNull(t.levelFor(0));
        assertEquals("odd", t.roll(0, 0.5).id(), "no level = the plain block");
        assertTrue(problems.size() >= 8, problems.toString());
    }
}
