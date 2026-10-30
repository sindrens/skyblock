package net.skybornmc.skyblock.essentials;

import org.junit.jupiter.api.Test;

import java.util.List;

import static net.skybornmc.skyblock.essentials.StaffPowerRules.Check;
import static net.skybornmc.skyblock.essentials.StaffPowerRules.Creative;
import static net.skybornmc.skyblock.essentials.StaffPowerRules.Power;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ANS3: owner answers 331 (creative), 333 (god/fly/heal only in staff or build mode) and 334 (powers off otherwise). */
class StaffPowerRulesTest {

    private static final List<String> WORLDS = List.of("spawn", "depths");

    @Test
    void creativeOnlyForBuildersAndAdminsInTheCreativeWorlds() {
        assertEquals(Creative.OK, StaffPowerRules.creative(true, true, "spawn", WORLDS));
        assertEquals(Creative.OK, StaffPowerRules.creative(true, true, "Depths", WORLDS), "world names ignore case");
        assertEquals(Creative.WRONG_PLACE, StaffPowerRules.creative(true, true, "islands", WORLDS));
        assertEquals(Creative.WRONG_PLACE, StaffPowerRules.creative(true, true, "warzone", WORLDS));
        assertEquals(Creative.NOT_ALLOWED, StaffPowerRules.creative(true, false, "spawn", WORLDS), "mods use spectator");
        assertEquals(Creative.OK, StaffPowerRules.creative(false, true, "islands", WORLDS), "switched off: permission only");
        assertEquals(Creative.NOT_ALLOWED, StaffPowerRules.creative(false, false, "spawn", WORLDS));
        assertEquals("wrong-place", Creative.WRONG_PLACE.key());
    }

    @Test
    void godFlyAndHealNeedStaffOrBuildMode() {
        for (Power power : Power.values()) {
            assertEquals(Check.NEED_MODE, StaffPowerRules.power(power, true, false, false, true, false), power.name());
            assertEquals(Check.OK, StaffPowerRules.power(power, true, false, true, true, true), power.name());
            assertEquals(Check.OK, StaffPowerRules.power(power, false, false, false, true, false), "off: " + power.name());
        }
    }

    @Test
    void normalPlayersNeverGetGodOrFlyButCanBeHealed() {
        assertEquals(Check.TARGET_NEEDS_MODE, StaffPowerRules.power(Power.GOD, true, false, true, false, false));
        assertEquals(Check.TARGET_NEEDS_MODE, StaffPowerRules.power(Power.FLY, true, false, true, false, false));
        assertEquals(Check.OK, StaffPowerRules.power(Power.HEAL, true, false, true, false, false));
        assertEquals(Check.OK, StaffPowerRules.power(Power.FLY, true, false, true, false, true), "another staff member in mode");
        assertEquals(Check.TARGET_NEEDS_MODE, StaffPowerRules.power(Power.GOD, true, true, false, false, false),
                "the console can't give a normal player god mode either");
        assertEquals(Check.OK, StaffPowerRules.power(Power.HEAL, true, true, false, false, false), "the console may heal");
    }

    @Test
    void powersEndWhenBothModesAreOff() {
        assertTrue(StaffPowerRules.keep(true, true));
        assertFalse(StaffPowerRules.keep(true, false));
        assertTrue(StaffPowerRules.keep(false, false));
    }

    /**
     * FIXES1 regression: the owner on his island (island fly on) ran /fly five times and got "Flying off." every time,
     * because the toggle read allowFlight (true from island fly). The toggle now reads only its own grant.
     */
    @Test
    void flyToggleReadsOnlyItsOwnGrant() {
        assertEquals(StaffPowerRules.FlyAction.TURN_ON, StaffPowerRules.fly(false, false),
                "island fly makes allowFlight true, but /fly was never on: turn /fly on (after the mode check)");
        assertEquals(StaffPowerRules.FlyAction.TURN_OFF, StaffPowerRules.fly(true, false));
        assertEquals(StaffPowerRules.FlyAction.GAME_MODE, StaffPowerRules.fly(false, true), "creative/spectator always fly");
        assertEquals(StaffPowerRules.FlyAction.GAME_MODE, StaffPowerRules.fly(true, true));
    }

    @Test
    void flyTextFollowsTheRealResult() {
        assertEquals(StaffPowerRules.FlyText.ENABLED, StaffPowerRules.flyText(true, true));
        assertEquals(StaffPowerRules.FlyText.DISABLED, StaffPowerRules.flyText(false, false));
        assertEquals(StaffPowerRules.FlyText.DISABLED_STILL_FLYING, StaffPowerRules.flyText(false, true),
                "/fly off on an island: say island fly still works instead of a plain 'off'");
        assertEquals("disabled-still-flying", StaffPowerRules.FlyText.DISABLED_STILL_FLYING.key());
    }

    @Test
    void staffPowerRuleOnlyAppliesToTurningFlyOn() {
        // Owner answer 333: outside staff/build mode /fly can't be turned on; island fly is not a staff power at all.
        assertEquals(Check.NEED_MODE, StaffPowerRules.power(Power.FLY, true, false, false, true, false));
        assertEquals(Check.OK, StaffPowerRules.power(Power.FLY, true, false, true, true, true));
    }
}
