package net.skybornmc.skyblock.milestones;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Synergy: completed island milestone tiers give island level points. */
class IslandLevelPointsTest {

    @Test
    void tiersTimesPointsNeverNegative() {
        assertEquals(0, IslandLevelPoints.points(0, 2), 1e-9);
        assertEquals(0, IslandLevelPoints.points(-3, 2), 1e-9);
        assertEquals(0, IslandLevelPoints.points(10, 0), 1e-9);
        assertEquals(0, IslandLevelPoints.points(10, Double.NaN), 1e-9);
        assertEquals(20, IslandLevelPoints.points(10, 2), 1e-9);
    }

    @Test
    void defaultIsTwoPointsPerTier() {
        assertEquals(2, new MilestonesConfig().levelPointsPerIslandTier, 1e-9);
    }
}
