package net.skybornmc.skyblock.rewards;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Active-time counting and playtime milestones (R6). */
class PlaytimeRulesTest {

    private static final long IDLE = 5 * 60_000L;
    private static final NavigableMap<Integer, String> TITLES = new TreeMap<>(java.util.Map.of(50, "regular", 100, "veteran",
            250, "dedicated", 500, "timeless"));

    @Test
    void onlyActiveTimeCounts() {
        long now = 1_000_000_000L;
        assertTrue(PlaytimeRules.active(now - 10_000, now, IDLE, false, false, false));
        assertFalse(PlaytimeRules.active(now - IDLE, now, IDLE, false, false, false), "5 minutes without action = AFK");
        assertFalse(PlaytimeRules.active(now - 10_000, now, IDLE, true, false, false), "marked AFK");
        assertFalse(PlaytimeRules.active(now - 10_000, now, IDLE, false, true, false), "in the AFK pool");
        assertFalse(PlaytimeRules.active(now - 10_000, now, IDLE, false, false, true), "frozen, staff mode, switching");
        assertFalse(PlaytimeRules.active(0, now, IDLE, false, false, false), "no action seen yet");
    }

    @Test
    void ownMovesOnly() {
        assertTrue(ActivityTracker.ownMove(0, 0, 10, 0, false, false), "camera turned");
        assertTrue(ActivityTracker.ownMove(0, 0, 0, 0, true, true), "walked on the ground");
        assertFalse(ActivityTracker.ownMove(0, 0, 0, 0, true, false), "pushed by water or riding");
        assertFalse(ActivityTracker.ownMove(0, 0, 0, 0, false, true), "no block change");
    }

    @Test
    void activityTimestampsAreThrottled() {
        ActivityTracker t = new ActivityTracker();
        java.util.UUID id = java.util.UUID.randomUUID();
        t.touch(id, 10_000);
        t.touch(id, 10_500);
        assertEquals(10_000, t.lastAction(id));
        t.touch(id, 11_000);
        assertEquals(11_000, t.lastAction(id));
        t.forget(id);
        assertEquals(0, t.lastAction(id));
    }

    @Test
    void hourlyRewardsKeysAndTitles() {
        PlaytimeRules.Due none = PlaytimeRules.due(new PlaytimeRules.Row(3_599, 0, 0, 0), 10, TITLES, 24);
        assertFalse(none.any());

        PlaytimeRules.Due first = PlaytimeRules.due(new PlaytimeRules.Row(3_600, 0, 0, 0), 10, TITLES, 24);
        assertEquals(1, first.hours());
        assertEquals(0, first.keys());
        assertEquals(new PlaytimeRules.Row(3_600, 1, 0, 0), first.after());

        PlaytimeRules.Due tenth = PlaytimeRules.due(new PlaytimeRules.Row(10 * 3_600 + 5, 9, 0, 0), 10, TITLES, 24);
        assertEquals(1, tenth.hours());
        assertEquals(1, tenth.keys());

        PlaytimeRules.Due fifty = PlaytimeRules.due(new PlaytimeRules.Row(50 * 3_600, 49, 4, 0), 10, TITLES, 24);
        assertEquals(List.of(50), fifty.titles());
        assertEquals(new PlaytimeRules.Row(50 * 3_600, 50, 5, 50), fifty.after());

        // Paid already: nothing twice.
        assertFalse(PlaytimeRules.due(fifty.after(), 10, TITLES, 24).any());
    }

    @Test
    void bigBacklogIsPaidInSteps() {
        PlaytimeRules.Row row = new PlaytimeRules.Row(120 * 3_600, 0, 0, 0);
        PlaytimeRules.Due d = PlaytimeRules.due(row, 10, TITLES, 24);
        assertEquals(24, d.hours());
        assertEquals(2, d.keys());
        assertEquals(List.of(), d.titles());
        int steps = 1;
        while (true) {
            PlaytimeRules.Due next = PlaytimeRules.due(d.after(), 10, TITLES, 24);
            if (!next.any()) {
                break;
            }
            d = next;
            steps++;
        }
        assertEquals(5, steps);
        assertEquals(new PlaytimeRules.Row(120 * 3_600, 120, 12, 100), d.after());
    }

    @Test
    void keysOff() {
        PlaytimeRules.Due d = PlaytimeRules.due(new PlaytimeRules.Row(20 * 3_600, 0, 0, 0), 0, new TreeMap<>(), 24);
        assertEquals(20, d.hours());
        assertEquals(0, d.keys());
        assertEquals(0, PlaytimeRules.untilNextKey(5, 0));
    }

    @Test
    void countdowns() {
        assertEquals(3_600, PlaytimeRules.untilNextHour(0));
        assertEquals(1, PlaytimeRules.untilNextHour(3_599));
        assertEquals(10, PlaytimeRules.untilNextKey(0, 10));
        assertEquals(1, PlaytimeRules.untilNextKey(9 * 3_600, 10));
    }
}
