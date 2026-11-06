package net.skybornmc.skyblock.essentials;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Owner 7 Nov 2026 (OWNER_PICKS_7NOV.md): the meteor at spawn and the fishing contest. */
class ServerEventsTest {

    @Test
    void defaultsMatchThePlan() {
        EssentialsConfig c = new EssentialsConfig();
        assertTrue(c.meteorEnabled && c.contestEnabled);
        assertEquals(60, c.meteorEveryMinutes);
        assertEquals(5, c.meteorFragments);
        assertEquals("spawn", c.meteorWorld);
        assertEquals(120, c.contestEveryMinutes);
        assertEquals(10, c.contestMinutes);
        assertEquals(List.of(3, 2, 1), c.contestKeys);
        assertEquals("7:05", ServerEvents.clock(425));
        assertEquals("0:09", ServerEvents.clock(9));
    }
}
