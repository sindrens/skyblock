package net.skybornmc.skyblock.stacking;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** F13: staff alert levels and rate limits for hoppers per chunk. */
class HopperAlertsTest {

    private static final long MIN = 60_000L;
    private static final long COOLDOWN = 30 * MIN;

    @Test
    void levels() {
        assertEquals(0, HopperAlerts.level(64, 64, 64));
        assertEquals(1, HopperAlerts.level(65, 64, 64));
        assertEquals(1, HopperAlerts.level(128, 64, 64));
        assertEquals(2, HopperAlerts.level(129, 64, 64));
        assertEquals(1, HopperAlerts.level(10_000, 64, 0), "step 0: only the first crossing");
    }

    @Test
    void risesOnlyWhenALevelIsCrossed() {
        assertTrue(HopperAlerts.rises(64, 65, 64, 64));
        assertFalse(HopperAlerts.rises(65, 66, 64, 64));
        assertFalse(HopperAlerts.rises(63, 64, 64, 64), "64 is allowed, the alert is ABOVE 64");
        assertTrue(HopperAlerts.rises(128, 129, 64, 64));
        assertFalse(HopperAlerts.rises(66, 65, 64, 64), "breaking never alerts");
    }

    @Test
    void sameChunkSameLevelWaitsForTheCooldown() {
        HopperAlerts alerts = new HopperAlerts();
        long k = HopperCounts.key(0, 0);
        assertTrue(alerts.tryAlert("islands", k, 65, 64, 64, COOLDOWN, 5, 0));
        // break one and place it again: no spam
        assertFalse(alerts.tryAlert("islands", k, 65, 64, 64, COOLDOWN, 5, MIN));
        assertTrue(alerts.tryAlert("islands", k, 65, 64, 64, COOLDOWN, 5, COOLDOWN + 1), "after the cooldown again");
    }

    @Test
    void higherLevelAlertsAtOnce() {
        HopperAlerts alerts = new HopperAlerts();
        long k = HopperCounts.key(0, 0);
        assertTrue(alerts.tryAlert("islands", k, 65, 64, 64, COOLDOWN, 5, 0));
        assertTrue(alerts.tryAlert("islands", k, 129, 64, 64, COOLDOWN, 5, 1000));
        assertFalse(alerts.tryAlert("islands", k, 65, 64, 64, COOLDOWN, 5, 2000), "back down: quiet");
    }

    @Test
    void belowTheThresholdNeverAlerts() {
        assertFalse(new HopperAlerts().tryAlert("islands", 0, 64, 64, 64, COOLDOWN, 5, 0));
    }

    @Test
    void globalLimitPerMinute() {
        HopperAlerts alerts = new HopperAlerts();
        for (int i = 0; i < 3; i++) {
            assertTrue(alerts.tryAlert("islands", HopperCounts.key(i, 0), 65, 64, 64, COOLDOWN, 3, i));
        }
        long blocked = HopperCounts.key(9, 9);
        assertFalse(alerts.tryAlert("islands", blocked, 65, 64, 64, COOLDOWN, 3, 100));
        assertTrue(alerts.tryAlert("islands", blocked, 65, 64, 64, COOLDOWN, 3, MIN + 10),
                "a chunk refused by the global limit is not remembered and may alert a minute later");
    }

    @Test
    void memoryStaysBounded() {
        HopperAlerts alerts = new HopperAlerts();
        for (int i = 0; i < 5000; i++) {
            alerts.tryAlert("islands", HopperCounts.key(i, 0), 65, 64, 64, MIN, 600, i * MIN);
        }
        assertTrue(alerts.remembered() <= 2049, "old chunks are pruned: " + alerts.remembered());
    }
}
