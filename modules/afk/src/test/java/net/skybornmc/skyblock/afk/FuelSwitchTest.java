package net.skybornmc.skyblock.afk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PHASE-7 M7.1: banked AFK fuel is handed out once each time Island Fuel switches on. */
class FuelSwitchTest {

    @Test
    void firesOncePerSwitchOn() {
        FuelSwitch s = new FuelSwitch();
        assertFalse(s.turnedOn(false));
        assertTrue(s.turnedOn(true));
        assertFalse(s.turnedOn(true));
        assertFalse(s.turnedOn(false));
        assertTrue(s.turnedOn(true), "switched off and on again (reload)");
    }
}
