package net.skybornmc.skyblock.islands.stats;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecentPlacementsTest {

    @Test
    void placeThenBreakIsRecognisedOnce() {
        RecentPlacements recent = new RecentPlacements(4);
        recent.placed(10, 64, -20);
        assertTrue(recent.brokeOwnPlacement(10, 64, -20));
        assertFalse(recent.brokeOwnPlacement(10, 64, -20)); // forgotten after the break
        assertFalse(recent.brokeOwnPlacement(11, 64, -20));
    }

    @Test
    void oldestIsForgottenAtCapacity() {
        RecentPlacements recent = new RecentPlacements(2);
        recent.placed(1, 1, 1);
        recent.placed(2, 2, 2);
        recent.placed(1, 1, 1); // refreshed: now newest
        recent.placed(3, 3, 3); // evicts 2,2,2
        assertEquals(2, recent.size());
        assertFalse(recent.brokeOwnPlacement(2, 2, 2));
        assertTrue(recent.brokeOwnPlacement(1, 1, 1));
        assertTrue(recent.brokeOwnPlacement(3, 3, 3));
    }

    @Test
    void zeroCapacityIsOff() {
        RecentPlacements recent = new RecentPlacements(0);
        recent.placed(1, 1, 1);
        assertFalse(recent.brokeOwnPlacement(1, 1, 1));
    }

    @Test
    void packKeepsNegativeAndFarCoordinatesApart() {
        assertNotEquals(RecentPlacements.pack(-1, 64, 0), RecentPlacements.pack(1, 64, 0));
        assertNotEquals(RecentPlacements.pack(0, 64, -1), RecentPlacements.pack(0, 64, 1));
        assertNotEquals(RecentPlacements.pack(0, -1, 0), RecentPlacements.pack(0, 1, 0));
        assertNotEquals(RecentPlacements.pack(1_000_000, 0, -1_000_000), RecentPlacements.pack(-1_000_000, 0, 1_000_000));
    }
}
