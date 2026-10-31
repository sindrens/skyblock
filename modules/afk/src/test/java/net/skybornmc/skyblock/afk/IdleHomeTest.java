package net.skybornmc.skyblock.afk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Owner R3-35: no idle kick; not earning for 60 s in the pool sends the player to their island. */
class IdleHomeTest {

    private static final long DELAY = 60_000;

    @Test
    void notEarningStates() {
        assertTrue(IdleHome.notEarning(AfkState.CAPPED));
        assertTrue(IdleHome.notEarning(AfkState.IP_CAPPED));
        assertTrue(IdleHome.notEarning(AfkState.IP_BLOCKED));
        assertFalse(IdleHome.notEarning(AfkState.EARNING));
        assertFalse(IdleHome.notEarning(AfkState.LOADING));
        assertFalse(IdleHome.notEarning(AfkState.NO_PERMISSION));
        assertFalse(IdleHome.notEarning(AfkState.OUTSIDE));
    }

    @Test
    void warnsOnceThenSendsAfterTheDelay() {
        IdleHome.Step s = IdleHome.step(true, AfkState.CAPPED, 0, 1_000, DELAY);
        assertEquals(IdleHome.Action.WARN, s.action());
        assertEquals(1_000, s.since());
        s = IdleHome.step(true, AfkState.CAPPED, s.since(), 30_000, DELAY);
        assertEquals(IdleHome.Action.NONE, s.action());
        assertEquals(1_000, s.since());
        s = IdleHome.step(true, AfkState.CAPPED, s.since(), 60_999, DELAY);
        assertEquals(IdleHome.Action.NONE, s.action());
        s = IdleHome.step(true, AfkState.CAPPED, s.since(), 61_000, DELAY);
        assertEquals(IdleHome.Action.SEND, s.action());
        assertEquals(0, s.since());
    }

    @Test
    void earningAgainResetsTheTimer() {
        IdleHome.Step s = IdleHome.step(true, AfkState.IP_BLOCKED, 0, 1_000, DELAY);
        s = IdleHome.step(true, AfkState.EARNING, s.since(), 50_000, DELAY);
        assertEquals(IdleHome.Action.NONE, s.action());
        assertEquals(0, s.since());
        // Paused again later: a fresh 60 s.
        s = IdleHome.step(true, AfkState.IP_BLOCKED, s.since(), 55_000, DELAY);
        assertEquals(IdleHome.Action.WARN, s.action());
        assertEquals(IdleHome.Action.NONE, IdleHome.step(true, AfkState.IP_BLOCKED, s.since(), 100_000, DELAY).action());
        assertEquals(IdleHome.Action.SEND, IdleHome.step(true, AfkState.IP_BLOCKED, s.since(), 115_000, DELAY).action());
    }

    @Test
    void switchingBetweenNotEarningStatesKeepsTheTimer() {
        IdleHome.Step s = IdleHome.step(true, AfkState.IP_BLOCKED, 0, 1_000, DELAY);
        s = IdleHome.step(true, AfkState.CAPPED, s.since(), 40_000, DELAY);
        assertEquals(1_000, s.since());
        assertEquals(IdleHome.Action.SEND, IdleHome.step(true, AfkState.IP_CAPPED, s.since(), 61_000, DELAY).action());
    }

    @Test
    void offDoesNothing() {
        IdleHome.Step s = IdleHome.step(false, AfkState.CAPPED, 1_000, 1_000_000, DELAY);
        assertEquals(IdleHome.Action.NONE, s.action());
        assertEquals(0, s.since());
    }
}
