package net.skybornmc.skyblock.boosters;

import org.junit.jupiter.api.Test;

import java.util.List;

import static net.skybornmc.skyblock.boosters.BoosterRules.MINUTE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoosterRulesTest {

    private static final long MAX = 180 * MINUTE;

    @Test
    void sameTypeAddsTimeInsteadOfStacking() {
        BoosterRules.Decision first = BoosterRules.activate(null, 25, 10 * MINUTE, MAX);
        assertEquals(BoosterRules.Outcome.STARTED, first.outcome());
        assertEquals(10 * MINUTE, first.remaining());
        BoosterRules.Decision more = BoosterRules.activate(new BoosterRules.Run(25, 4 * MINUTE), 25, 10 * MINUTE, MAX);
        assertEquals(BoosterRules.Outcome.EXTENDED, more.outcome());
        assertEquals(14 * MINUTE, more.remaining(), "time is added, the strength stays +25%");
    }

    @Test
    void anotherStrengthWaitsAndTheCapRefuses() {
        assertEquals(BoosterRules.Outcome.OTHER_STRENGTH,
                BoosterRules.activate(new BoosterRules.Run(25, MINUTE), 50, 10 * MINUTE, MAX).outcome());
        assertEquals(BoosterRules.Outcome.FULL,
                BoosterRules.activate(new BoosterRules.Run(50, 175 * MINUTE), 50, 10 * MINUTE, MAX).outcome());
        assertEquals(BoosterRules.Outcome.EXTENDED,
                BoosterRules.activate(new BoosterRules.Run(50, 170 * MINUTE), 50, 10 * MINUTE, MAX).outcome(), "exactly 3 h is fine");
        assertEquals(BoosterRules.Outcome.FULL, BoosterRules.activate(null, 50, 200 * MINUTE, MAX).outcome());
        assertEquals(BoosterRules.Outcome.STARTED, BoosterRules.activate(new BoosterRules.Run(50, 0), 100, MINUTE, MAX).outcome(),
                "a finished run counts as none");
        assertEquals(BoosterRules.Outcome.INVALID, BoosterRules.activate(null, 0, MINUTE, MAX).outcome());
        assertFalse(BoosterRules.activate(null, 25, 0, MAX).ok());
    }

    @Test
    void bankedTimeStartsOnlyWhenItFits() {
        assertEquals(10 * MINUTE, BoosterRules.bankMove(null, 50, 10 * MINUTE, MAX));
        assertEquals(0, BoosterRules.bankMove(new BoosterRules.Run(100, MINUTE), 50, 10 * MINUTE, MAX), "another strength runs");
        assertEquals(10 * MINUTE, BoosterRules.bankMove(new BoosterRules.Run(50, MINUTE), 50, 10 * MINUTE, MAX));
        assertEquals(5 * MINUTE, BoosterRules.bankMove(new BoosterRules.Run(50, 175 * MINUTE), 50, 10 * MINUTE, MAX), "only what fits");
        assertEquals(0, BoosterRules.bankMove(null, 50, 0, MAX));
    }

    @Test
    void bankAddsUpToItsCap() {
        assertEquals(20 * MINUTE, BoosterRules.bankAdd(10 * MINUTE, 10 * MINUTE, 60 * MINUTE));
        assertEquals(60 * MINUTE, BoosterRules.bankAdd(55 * MINUTE, 10 * MINUTE, 60 * MINUTE));
        assertEquals(Long.MAX_VALUE, BoosterRules.bankAdd(Long.MAX_VALUE, 10, 0), "no cap, never overflows");
    }

    @Test
    void thanksInChatMatchWholeWordsOnly() {
        List<String> phrases = List.of("thanks", "thank you", "ty", "thx");
        assertTrue(BoosterRules.isThanks("Thanks!", phrases, 60));
        assertTrue(BoosterRules.isThanks("ty for the boost", phrases, 60));
        assertTrue(BoosterRules.isThanks("ok thank   you :)", phrases, 60));
        assertFalse(BoosterRules.isThanks("what type is it", phrases, 60), "'ty' inside a word does not count");
        assertFalse(BoosterRules.isThanks("thank", phrases, 60));
        assertFalse(BoosterRules.isThanks("thanks " + "x".repeat(80), phrases, 60), "long lines never count");
        assertFalse(BoosterRules.isThanks("", phrases, 60));
    }

    @Test
    void timeAndProgressText() {
        assertEquals("45s", BoosterRules.time(45_000));
        assertEquals("12m 05s", BoosterRules.time(12 * MINUTE + 5_000));
        assertEquals("1h 05m", BoosterRules.time(65 * MINUTE));
        assertEquals("0s", BoosterRules.time(-5));
        assertEquals("1s", BoosterRules.time(1), "rounded up, never shows 0s while running");
        assertEquals(0.5f, BoosterRules.progress(5, 10));
        assertEquals(0f, BoosterRules.progress(5, 0));
        assertEquals(1f, BoosterRules.progress(20, 10));
    }

    @Test
    void settingsLimitItems() {
        BoostersSettings s = new BoostersSettings();
        assertTrue(s.valid(25, 10, false));
        assertTrue(s.valid(100, 60, false));
        assertFalse(s.valid(150, 10, false), "above personal.max-percent");
        assertFalse(s.valid(25, 0, false));
        assertFalse(s.valid(25, 600, false), "above personal.max-item-minutes");
        assertTrue(s.valid(50, 60, true));
        assertFalse(s.valid(200, 60, true), "above global.max-percent");
    }
}
