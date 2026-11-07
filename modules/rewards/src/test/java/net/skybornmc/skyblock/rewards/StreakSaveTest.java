package net.skybornmc.skyblock.rewards;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Owner 7 Nov 2026: a broken streak saved in the credit shop within the save window. */
class StreakSaveTest {

    @Test
    void aLostStreakComesBackWithinTheWindow() {
        // broke on day 100 (lost 12), claimed again: streak 1 today
        DailyRules.State s = new DailyRules.State(13, 100, 1, 12, 12, 100, 13);
        Optional<DailyRules.State> saved = DailyRules.saved(s, 101, 2);
        assertTrue(saved.isPresent());
        assertEquals(13, saved.get().streak());
        assertEquals(13, saved.get().best());
        assertEquals(0, saved.get().lostStreak());
        assertEquals(DailyRules.NEVER, saved.get().brokenOn());
        assertEquals(13, saved.get().total());
    }

    @Test
    void nothingToSaveOrTooLate() {
        assertTrue(DailyRules.saved(DailyRules.State.NONE, 100, 2).isEmpty());
        DailyRules.State s = new DailyRules.State(13, 100, 1, 12, 12, 100, 13);
        assertTrue(DailyRules.saved(s, 103, 2).isEmpty());
        assertTrue(DailyRules.saved(s, 102, 2).isPresent());
    }
}
