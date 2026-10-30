package net.skybornmc.skyblock.islands;

import net.skybornmc.skyblock.common.island.IslandService.PortalEntry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IslandPortalFlowTest {

    @Test
    void firstUseCreatesTheIsland() {
        assertEquals(PortalEntry.CREATING, IslandPortalFlow.decide(false, false, true));
    }

    @Test
    void anExistingIslandAlwaysGoesHome() {
        assertEquals(PortalEntry.HOME, IslandPortalFlow.decide(true, false, true));
        assertEquals(PortalEntry.HOME, IslandPortalFlow.decide(true, false, false), "home checks its own world");
    }

    @Test
    void neverBuildsTwice() {
        assertEquals(PortalEntry.BUSY, IslandPortalFlow.decide(false, true, true));
        assertEquals(PortalEntry.BUSY, IslandPortalFlow.decide(true, true, true));
    }

    @Test
    void noIslandsWorldNoIsland() {
        assertEquals(PortalEntry.REFUSED, IslandPortalFlow.decide(false, false, false));
    }
}
