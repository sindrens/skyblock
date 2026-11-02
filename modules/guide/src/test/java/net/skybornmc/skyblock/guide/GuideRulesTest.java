package net.skybornmc.skyblock.guide;

import net.skybornmc.skyblock.common.island.IslandService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuideRulesTest {

    @Test
    void defaultSpotIsNextToTheHomeFacingIt() {
        GuideStore.Spot s = GuideRules.defaultSpot("islands", 100.3, 64, 200.7, 2, 0, 1);
        assertEquals(102.5, s.x(), 1e-9);
        assertEquals(64, s.y(), 1e-9);
        assertEquals(201.5, s.z(), 1e-9);
        assertTrue(Math.abs(s.yaw()) <= 180);
    }

    @Test
    void yawFollowsMinecraft() {
        assertEquals(0f, GuideRules.yawToward(0, 0, 0, 5), 1e-4, "south");
        assertEquals(90f, GuideRules.yawToward(0, 0, -5, 0), 1e-4, "west");
        assertEquals(-90f, GuideRules.yawToward(0, 0, 5, 0), 1e-4, "east");
        assertEquals(0f, GuideRules.yawToward(1, 1, 1, 1), 1e-4);
    }

    @Test
    void onlyLeaderAndAdminsMove() {
        assertTrue(GuideRules.mayMove(IslandService.Role.LEADER));
        assertTrue(GuideRules.mayMove(IslandService.Role.ADMIN));
        assertFalse(GuideRules.mayMove(IslandService.Role.MOD));
        assertFalse(GuideRules.mayMove(IslandService.Role.MEMBER));
        assertFalse(GuideRules.mayMove(IslandService.Role.COOP));
        assertFalse(GuideRules.mayMove(null));
    }

    @Test
    void insideTheBorderOnly() {
        IslandService.Bounds b = new IslandService.Bounds("islands", 0, 0, 47, 47);
        assertTrue(GuideRules.inside(b, 10.9, 47.2));
        assertFalse(GuideRules.inside(b, 48, 10));
        assertFalse(GuideRules.inside(null, 1, 1));
    }

    @Test
    void chunkKeyPacksLikeBukkit() {
        assertEquals(0L, GuideRules.chunkKey(0, 0));
        assertEquals((1L) | (2L << 32), GuideRules.chunkKey(16, 32));
        assertEquals(0xffffffffL | (0xffffffffL << 32), GuideRules.chunkKey(-1, -1));
    }
}
