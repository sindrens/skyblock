package net.skybornmc.skyblock.perks;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FreeRankRulesTest {

    private static final long MIN = 60_000L;

    @Test
    void needsThirtyMinutesOfPlaytime() {
        assertEquals(FreeRankRules.State.TOO_EARLY, FreeRankRules.state(true, true, 29 * MIN, 30, null));
        assertEquals(FreeRankRules.State.CLAIM, FreeRankRules.state(true, true, 30 * MIN, 30, null));
        assertEquals(MIN, FreeRankRules.remainingMillis(29 * MIN, 30));
        assertEquals(0, FreeRankRules.remainingMillis(31 * MIN, 30));
        assertEquals(30 * MIN, FreeRankRules.remainingMillis(-5, 30));
    }

    @Test
    void claimedOnceThenPickTagThenDone() {
        FreeRankRules.Claim noTag = new FreeRankRules.Claim("ember", 1, null);
        FreeRankRules.Claim withTag = new FreeRankRules.Claim("ember", 1, "spark");
        assertEquals(FreeRankRules.State.PICK_TAG, FreeRankRules.state(true, true, 0, 30, noTag));
        assertEquals(FreeRankRules.State.DONE, FreeRankRules.state(true, true, 999 * MIN, 30, withTag));
        // A claimed player can still finish picking a tag when the feature or LuckPerms is off.
        assertEquals(FreeRankRules.State.PICK_TAG, FreeRankRules.state(false, false, 0, 30, noTag));
    }

    @Test
    void disabledOrNoRankPlugin() {
        assertEquals(FreeRankRules.State.DISABLED, FreeRankRules.state(false, true, 99 * MIN, 30, null));
        assertEquals(FreeRankRules.State.NO_RANKS, FreeRankRules.state(true, false, 99 * MIN, 30, null));
    }

    @Test
    void tagChoicesAreKnownDistinctAndAtMostThree() {
        Set<String> known = Set.of("ember", "spark", "ash", "phoenix");
        assertEquals(List.of("ember", "spark", "ash"), FreeRankRules.tagChoices(List.of("Ember", " spark ", "ember", "ash", "phoenix"), known));
        assertEquals(List.of("spark"), FreeRankRules.tagChoices(List.of("missing", "spark"), known));
        assertTrue(FreeRankRules.validChoice("SPARK", List.of("spark")));
        assertFalse(FreeRankRules.validChoice("phoenix", List.of("ember", "spark", "ash")));
        assertFalse(FreeRankRules.validChoice(null, List.of("ember")));
    }
}
