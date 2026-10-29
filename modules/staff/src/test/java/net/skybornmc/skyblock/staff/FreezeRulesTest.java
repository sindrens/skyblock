package net.skybornmc.skyblock.staff;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FreezeRulesTest {

    @Test
    void commandLabels() {
        assertEquals("msg", FreezeRules.commandLabel("/msg Bob hi"));
        assertEquals("msg", FreezeRules.commandLabel("/MSG"));
        assertEquals("msg", FreezeRules.commandLabel("/minecraft:msg Bob"));
        assertEquals("tell", FreezeRules.commandLabel("/phoenixskyblock:tell Bob"));
        assertEquals("r", FreezeRules.commandLabel("  / r  hello"));
        assertEquals("rules", FreezeRules.commandLabel("//rules"));
        assertEquals("help", FreezeRules.commandLabel("/help\tpage"));
        assertEquals("", FreezeRules.commandLabel("/"));
        assertEquals("", FreezeRules.commandLabel(null));
    }

    @Test
    void defaultWhitelistBlocksEverythingIncludingMessaging() {
        List<String> none = List.of();
        for (String cmd : List.of("/msg Staff help", "/r hi", "/tell a b", "/w a b", "/minecraft:msg a b", "/spawn", "/home",
                "/is go", "/pay a 5", "/report a", "/discord", "/")) {
            assertFalse(FreezeRules.commandAllowed(cmd, none), cmd);
        }
    }

    @Test
    void configuredWhitelistMatchesLabelOnly() {
        List<String> allowed = List.of("Discord", "/rules", " ");
        assertEquals(Set.of("discord", "rules"), FreezeRules.normalize(allowed));
        assertTrue(FreezeRules.commandAllowed("/discord", allowed));
        assertTrue(FreezeRules.commandAllowed("/RULES 2", allowed));
        assertTrue(FreezeRules.commandAllowed("/someplugin:rules", allowed));
        assertFalse(FreezeRules.commandAllowed("/discordx", allowed));
        assertFalse(FreezeRules.commandAllowed("/msg discord", allowed));
    }

    @Test
    void movedIgnoresNoiseButCatchesSteps() {
        assertFalse(FreezeRules.moved(10.5, 64, -3.25, 10.5, 64, -3.25));
        assertFalse(FreezeRules.moved(10.5, 64, -3.25, 10.50001, 64.00001, -3.25001));
        assertTrue(FreezeRules.moved(10.5, 64, -3.25, 10.6, 64, -3.25));
        assertTrue(FreezeRules.moved(10.5, 64, -3.25, 10.5, 64.42, -3.25)); // jump
        assertTrue(FreezeRules.moved(10.5, 64, -3.25, 10.5, 63.9, -3.25)); // falling
        assertTrue(FreezeRules.moved(10.5, 64, -3.25, 10.5, 64, -3.2));
    }

    @Test
    void ownSnapBackAlwaysPassesEvenAsPluginTeleport() {
        // Paper turns PlayerMoveEvent#setTo into a PLUGIN teleport: refusing it was the original bug.
        assertTrue(FreezeRules.teleportAllowed("PLUGIN", List.of(), true));
        assertTrue(FreezeRules.teleportAllowed("UNKNOWN", List.of(), true));
        assertTrue(FreezeRules.teleportAllowed("ENDER_PEARL", List.of(), true));
    }

    @Test
    void playerTeleportsBlockedStaffTeleportsAllowed() {
        List<String> defaults = new StaffConfig().freezeAllowedTeleportCauses;
        assertTrue(FreezeRules.teleportAllowed("COMMAND", defaults, false));
        assertTrue(FreezeRules.teleportAllowed("PLUGIN", defaults, false));
        for (String cause : List.of("ENDER_PEARL", "CHORUS_FRUIT", "CONSUMABLE_EFFECT", "NETHER_PORTAL", "END_PORTAL",
                "END_GATEWAY", "SPECTATE", "EXIT_BED", "DISMOUNT", "UNKNOWN")) {
            assertFalse(FreezeRules.teleportAllowed(cause, defaults, false), cause);
        }
        assertTrue(FreezeRules.teleportAllowed("ENDER_PEARL", List.of("Ender-Pearl"), false));
        assertTrue(FreezeRules.teleportAllowed("END_GATEWAY", List.of("end_gateway"), false));
        assertFalse(FreezeRules.teleportAllowed(null, defaults, false));
    }

    @Test
    void safeConfigDefaults() {
        StaffConfig config = new StaffConfig();
        assertTrue(config.freezeAllowedCommands.isEmpty(), "frozen players get no commands by default (owner: no messaging)");
        assertTrue(config.freezeKeepOnRejoin, "a freeze survives logout by default");
        assertFalse(config.inspectToolMaterial.equalsIgnoreCase("spyglass"), "the spyglass zooms on the client");
    }
}
