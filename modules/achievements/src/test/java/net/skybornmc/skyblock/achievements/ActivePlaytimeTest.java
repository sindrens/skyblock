package net.skybornmc.skyblock.achievements;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Owner 2 Nov 2026: playtime achievements only count minutes you really play. */
class ActivePlaytimeTest {

    @Test
    void onlyRecentlyActiveAndNotIdle() {
        long now = 1_000_000L;
        assertTrue(Tracker.activeMinute(now - 30_000, now, 90, false, false, false));
        assertFalse(Tracker.activeMinute(now - 91_000, now, 90, false, false, false), "standing still for over 90 s");
        assertFalse(Tracker.activeMinute(0, now, 90, false, false, false), "joined and did nothing");
        assertFalse(Tracker.activeMinute(now - 1_000, now, 90, true, false, false), "AFK");
        assertFalse(Tracker.activeMinute(now - 1_000, now, 90, false, true, false), "AFK pool");
        assertFalse(Tracker.activeMinute(now - 1_000, now, 90, false, false, true), "staff mode / vanished");
    }

    @Test
    void newMetricsAreCounters() {
        assertTrue(Metric.CRATES_OPENED.kind() == Metric.Kind.COUNTER);
        assertTrue(Metric.GUIDE_HAND_INS.kind() == Metric.Kind.COUNTER);
    }
}
