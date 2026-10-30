package net.skybornmc.skyblock.tutorial;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PERF4: compiled mine-block rules and the once-computed marker distances behave like the old per-block code. */
class MarkerScanTest {

    /** The old rule test, kept here as the reference. */
    private static boolean oldRule(String key, List<String> rules) {
        for (String rule : rules) {
            String r = rule.strip().toLowerCase(java.util.Locale.ROOT);
            if (r.startsWith("*") ? key.endsWith(r.substring(1)) : key.equals(r)) {
                return true;
            }
        }
        return false;
    }

    @Test
    void matcherAgreesWithTheOldRule() {
        List<String> rules = List.of("*_ore", " Stone ", "cobblestone", "deepslate", "");
        MarkerScan.Matcher m = MarkerScan.Matcher.compile(rules);
        for (String key : List.of("iron_ore", "deepslate_iron_ore", "stone", "cobblestone", "deepslate", "dirt", "ore",
                "stone_bricks", "", "andesite")) {
            assertEquals(oldRule(key, rules), m.matches(key), key);
        }
        assertTrue(m.matches("iron_ore"));
        assertFalse(m.matches("dirt"));
        assertTrue(MarkerScan.Matcher.compile(List.of("*")).matches("anything"), "a bare * matches every block");
        assertFalse(MarkerScan.Matcher.compile(List.of()).matches("stone"));
    }

    @Test
    void nearestFirstIsStableAndUsesTheBlockCorner() {
        // centre 0.5/64/0.5 (a player standing in the middle of a block); blocks measured from their corner
        List<MarkerScan.Hit> hits = List.of(
                MarkerScan.Hit.of(4, 64, 0, 0.5, 64, 0.5),
                MarkerScan.Hit.of(0, 64, 0, 0.5, 64, 0.5),
                MarkerScan.Hit.of(1, 64, 1, 0.5, 64, 0.5),
                MarkerScan.Hit.of(-2, 64, 0, 0.5, 64, 0.5));
        List<MarkerScan.Hit> sorted = MarkerScan.nearestFirst(hits);
        assertEquals(0.5, sorted.get(0).distanceSquared(), 1e-9);
        assertEquals(0, sorted.get(0).x());
        assertEquals(1, sorted.get(1).x(), "(1,1) ties with (0,0) at 0.5 but came later: stable order");
        assertEquals(-2, sorted.get(2).x());
        assertEquals(4, sorted.get(3).x());
        assertEquals(4, hits.size(), "input untouched");
    }
}
