package net.skybornmc.skyblock.perks;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Ranks V2 (owner EP-Q15): Ashborn after the tutorial and 30 minutes, given once, never a downgrade. */
class AshbornRulesTest {

    private static final long MIN = 60_000L;

    @Test
    void dueOnlyWithTheTutorialAndThePlaytime() {
        assertTrue(FreeRankRules.due(true, true, true, 30 * MIN, 30, false));
        assertFalse(FreeRankRules.due(true, true, false, 90 * MIN, 30, false), "tutorial first");
        assertFalse(FreeRankRules.due(true, true, true, 29 * MIN, 30, false), "not long enough");
        assertFalse(FreeRankRules.due(true, true, true, 90 * MIN, 30, true), "already claimed");
        assertFalse(FreeRankRules.due(false, true, true, 90 * MIN, 30, false), "switched off");
        assertFalse(FreeRankRules.due(true, false, true, 90 * MIN, 30, false), "no LuckPerms");
    }

    @Test
    void freerankSaysWhyBeforeTheClaim() {
        assertEquals(FreeRankRules.State.TUTORIAL, FreeRankRules.state(true, true, false, 90 * MIN, 30, null));
        assertEquals(FreeRankRules.State.TOO_EARLY, FreeRankRules.state(true, true, true, 10 * MIN, 30, null));
        assertEquals(FreeRankRules.State.CLAIM, FreeRankRules.state(true, true, true, 30 * MIN, 30, null));
        assertEquals(FreeRankRules.State.PICK_TAG, FreeRankRules.state(true, true, false, 0, 30,
                new FreeRankRules.Claim("ember", 1, null)), "a claim always allows the tag pick");
    }

    @Test
    void noDowngradePath() throws Exception {
        for (String f : new String[]{"AshbornAuto.java", "FreeRankService.java"}) {
            String src = Files.readString(Path.of("src/main/java/net/skybornmc/skyblock/perks", f));
            assertFalse(src.contains("removeGroup") || src.contains("parent set") || src.contains("setGroup"),
                    f + " must only ever add the group");
        }
    }
}
