package net.skybornmc.skyblock.islands;

import org.junit.jupiter.api.Test;

import static net.skybornmc.skyblock.islands.StaffModeBypass.Change.NONE;
import static net.skybornmc.skyblock.islands.StaffModeBypass.Change.TURN_OFF;
import static net.skybornmc.skyblock.islands.StaffModeBypass.Change.TURN_ON;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** F15: entering staff mode turns the island bypass on for owner/admin; leaving turns it off. */
class StaffModeBypassTest {

    @Test
    void enteringTurnsTheBypassOnOnlyForPermittedStaff() {
        assertEquals(TURN_ON, StaffModeBypass.decide(true, true, true, false), "owner/admin");
        assertEquals(NONE, StaffModeBypass.decide(true, false, true, false), "a mod keeps the manual /is bypass only");
        assertEquals(NONE, StaffModeBypass.decide(true, true, false, false), "no bypass permission, no bypass");
        assertEquals(NONE, StaffModeBypass.decide(true, true, true, true), "already on");
    }

    @Test
    void leavingAlwaysTurnsItOff() {
        assertEquals(TURN_OFF, StaffModeBypass.decide(false, true, true, true));
        assertEquals(TURN_OFF, StaffModeBypass.decide(false, false, true, true), "also a bypass switched on by hand");
        assertEquals(NONE, StaffModeBypass.decide(false, true, true, false));
    }
}
