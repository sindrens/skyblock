package net.skybornmc.skyblock.pass;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reward tables: the default season parses cleanly and the premium track is cosmetics only (R3-44). */
class PassTableTest {

    private static PassTable defaults(List<String> problems) {
        PassSettings s = new PassSettings();
        return PassTable.parse(s.freeEveryLevel, s.freeMilestones, s.freeOverflow, s.premiumMilestones, s.maxLevel, problems::add);
    }

    @Test
    void defaultSeasonParsesWithoutProblems() {
        List<String> problems = new ArrayList<>();
        PassTable t = defaults(problems);
        assertEquals(List.of(), problems);
        assertEquals(20, t.premiumTiers().size(), "a cosmetic every 5 levels");
        assertTrue(t.hasOverflow());
    }

    @Test
    void freeTrackGivesSomethingEveryLevelAndKeysEveryFive() {
        PassTable t = defaults(new ArrayList<>());
        for (int level = 1; level <= 100; level++) {
            List<PassTable.Reward> r = t.free(level, 100);
            assertFalse(r.isEmpty(), "level " + level);
            boolean key = r.stream().anyMatch(x -> x.kind() == PassTable.Kind.KEY);
            assertEquals(level % 5 == 0, key, "keys exactly every 5 levels (level " + level + ")");
        }
        assertEquals("phoenix", t.free(100, 100).stream().filter(x -> x.kind() == PassTable.Kind.KEY).findFirst().orElseThrow().ref());
        assertTrue(t.free(50, 100).stream().anyMatch(x -> x.kind() == PassTable.Kind.TITLE && x.ref().equals("pass_climber")));
        assertEquals(PassTable.Kind.MONEY, t.free(1, 100).getLast().kind(), "level 1 = the first small reward");
        assertEquals(PassTable.Kind.CRYSTALS, t.free(2, 100).getLast().kind());
        assertEquals(2, t.free(5, 100).getFirst().amount(), "Ember x2 at 5");
    }

    @Test
    void overflowTiersGiveTheOverflowReward() {
        PassTable t = defaults(new ArrayList<>());
        List<PassTable.Reward> over = t.free(101, 100);
        assertEquals(List.of(PassTable.Kind.CRYSTALS, PassTable.Kind.KEY), over.stream().map(PassTable.Reward::kind).toList());
        assertEquals(over, t.free(250, 100));
        assertEquals(List.of(), t.free(0, 100));
    }

    @Test
    void premiumIsCosmeticsOnly() {
        List<String> problems = new ArrayList<>();
        PassTable t = PassTable.parse(List.of(), List.of(), List.of(),
                List.of("5: title pass_spark rare", "10: key phoenix", "15: money 60", "20: crystals 500", "25: booster sell 100 60",
                        "30: tag phoenix"), 100, problems::add);
        assertEquals(List.of(5, 30), t.premiumTiers(), "money, crystals, keys and boosters are refused on premium");
        assertEquals(4, problems.size());
        assertTrue(problems.stream().allMatch(p -> p.contains("cosmetics only")));
        for (int tier : t.premiumTiers()) {
            assertTrue(PassRules.cosmeticOnly(t.premium(tier)));
        }
        PassTable defaults = defaults(new ArrayList<>());
        for (int tier : defaults.premiumTiers()) {
            assertTrue(PassRules.cosmeticOnly(defaults.premium(tier)), "default premium tier " + tier);
        }
    }

    @Test
    void badLinesAreSkippedWithAReason() {
        List<String> problems = new ArrayList<>();
        PassTable t = PassTable.parse(List.of("money", "gems 5", "crystals -1", "money 2"),
                List.of("x: key ember", "0: key ember", "101: key ember", "7 key ember", "7: key Ember!", "8: booster speed 25 30",
                        "9: title Bad-Id", "9: title ok_id shiny", "10: key cinder 2"), List.of(), List.of(), 100, problems::add);
        assertEquals(11, problems.size(), String.join("\n", problems));
        assertEquals(PassTable.Kind.MONEY, t.free(1, 100).getLast().kind(), "only the valid small reward is kept");
        assertEquals(2, t.free(10, 100).getFirst().amount());
        assertEquals(1, t.free(9, 100).size(), "only the small reward");
    }
}
