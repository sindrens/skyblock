package net.skybornmc.skyblock.depths.tiers;

import net.skybornmc.skyblock.depths.zones.Aspect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Owner answers 345/346: which Depths places count as combat zones, and teleports into locked tiers. */
class DepthsTeleportsTest {

    @Test
    void bossArenaAndCombatTiersAreCombatZones() {
        assertTrue(DepthsTeleports.combatZone(true, null, false), "the boss arena always counts");
        assertTrue(DepthsTeleports.combatZone(false, Aspect.COMBAT, true));
        assertFalse(DepthsTeleports.combatZone(false, Aspect.COMBAT, false), "setting off");
        assertFalse(DepthsTeleports.combatZone(false, Aspect.MINE, true), "mine tiers are not combat zones");
        assertFalse(DepthsTeleports.combatZone(false, Aspect.FOREST, true));
        assertFalse(DepthsTeleports.combatZone(false, null, true), "The Gate and causeways are not zones");
    }

    @Test
    void teleportsIntoLockedTiersAreRefused() {
        assertTrue(DepthsTeleports.refuseInto(true, true, true));
        assertFalse(DepthsTeleports.refuseInto(true, false, true), "unlocked tier");
        assertFalse(DepthsTeleports.refuseInto(false, true, true), "not a tier (The Gate)");
        assertFalse(DepthsTeleports.refuseInto(true, true, false), "setting off");
    }
}
