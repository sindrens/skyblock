package net.skybornmc.skyblock.common.flight;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** FIXES1: one feature ending its flight never takes away flight another feature still gives. */
class FlightGrantsTest {

    @Test
    void flyOffOnAnIslandKeepsIslandFly() {
        UUID p = UUID.randomUUID();
        FlightGrants.grant(p, FlightGrants.ISLAND);
        FlightGrants.grant(p, FlightGrants.FLY);
        assertFalse(FlightGrants.revoke(p, FlightGrants.FLY), "island fly still holds: allowFlight must stay on");
        assertEquals(FlightGrants.ISLAND, FlightGrants.otherHolder(p, FlightGrants.FLY));
        assertTrue(FlightGrants.revoke(p, FlightGrants.ISLAND), "nothing left: the last one may switch flight off");
        assertFalse(FlightGrants.any(p));
    }

    @Test
    void revokeWithoutGrantIsSafe() {
        UUID p = UUID.randomUUID();
        assertTrue(FlightGrants.revoke(p, FlightGrants.FLY));
        assertNull(FlightGrants.otherHolder(p, FlightGrants.FLY));
        assertFalse(FlightGrants.othersHold(p, FlightGrants.FLY));
    }

    @Test
    void forgetClearsEverything() {
        UUID p = UUID.randomUUID();
        FlightGrants.grant(p, FlightGrants.STAFF_MODE);
        FlightGrants.grant(p, FlightGrants.FREEZE);
        assertTrue(FlightGrants.holds(p, FlightGrants.FREEZE));
        assertEquals(2, FlightGrants.sources(p).size());
        FlightGrants.forget(p);
        assertFalse(FlightGrants.any(p));
    }
}
