package net.skybornmc.skyblock.perks;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Ranks V2 rank rewards: week and month keys (Oslo), kit size, kit lines and the monthly pick. */
class RankRewardsRulesTest {

    private static final ZoneId OSLO = ZoneId.of("Europe/Oslo");

    @Test
    void periodKeysFollowOslo() {
        // Sunday 1 Nov 2026 22:59:59 UTC = 23:59:59 Oslo (week 44); one second later is Monday, week 45.
        assertEquals("2026-W44", RankRewardsRules.weekKey(Instant.parse("2026-11-01T22:59:59Z"), OSLO));
        assertEquals("2026-W45", RankRewardsRules.weekKey(Instant.parse("2026-11-01T23:00:00Z"), OSLO));
        assertEquals("2026-11", RankRewardsRules.monthKey(Instant.parse("2026-11-01T00:30:00Z"), OSLO));
        assertEquals("2026-10", RankRewardsRules.monthKey(Instant.parse("2026-10-31T22:59:59Z"), OSLO));
    }

    @Test
    void theHighestSizeNodeWins() {
        assertEquals("XL", RankRewardsRules.size(n -> true));
        assertEquals("M", RankRewardsRules.size(n -> n <= 2));
        assertNull(RankRewardsRules.size(n -> false));
    }

    @Test
    void kitsAreFoodAndBlocksOnly() {
        List<String> problems = new ArrayList<>();
        List<RankRewardsRules.Line> lines = RankRewardsRules.parse(List.of("cooked_beef 16", "diamond_pickaxe 1", "spawner 1",
                "custom:tool_x 1", "cobblestone 0", "nonsense", "stone x"), id -> !id.equals("nope"), problems::add);
        assertEquals(List.of(new RankRewardsRules.Line("cooked_beef", 16)), lines);
        assertEquals(6, problems.size(), () -> String.join("\n", problems));
        assertTrue(RankRewardsRules.forbidden("netherite_sword"));
    }

    @Test
    void monthlyPicksTheFirstTagNotOwned() {
        assertEquals("spark", RankRewardsRules.nextCosmetic(List.of("Ember", "spark"), Set.of("ember")));
        assertNull(RankRewardsRules.nextCosmetic(List.of("ember"), Set.of("ember")));
        assertNull(RankRewardsRules.nextCosmetic(List.of(), Set.of()));
    }
}
