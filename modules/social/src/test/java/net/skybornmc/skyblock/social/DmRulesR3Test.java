package net.skybornmc.skyblock.social;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** STAFF-R3 (R3-32): DMs to accounts still in the tutorial, the DM filter settings and the /rules privacy line. */
class DmRulesR3Test {

    private static final UUID NEW = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID STRANGER = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID FRIEND = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final Path ROOT = Path.of("..", "..").toAbsolutePath().normalize();

    @Test
    void newAccountsOnlyGetDmsFromStaffAndPlayersTheyMessagedFirst() {
        NewPlayerDms dms = new NewPlayerDms();
        assertFalse(dms.allows(true, true, false, NEW, STRANGER), "a stranger can't DM a player in the tutorial");
        assertTrue(dms.allows(true, true, true, NEW, STRANGER), "staff always can");
        dms.messaged(NEW, FRIEND);
        assertTrue(dms.allows(true, true, false, NEW, FRIEND), "the new player messaged them first");
        assertFalse(dms.allows(true, true, false, NEW, STRANGER));
        assertTrue(dms.allows(true, false, false, NEW, STRANGER), "after the tutorial everyone can");
        assertTrue(dms.allows(false, true, false, NEW, STRANGER), "setting off");
        dms.forget(NEW);
        assertFalse(dms.allows(true, true, false, NEW, FRIEND), "the list ends with the session");
        assertEquals(0, dms.tracked());
    }

    @Test
    void filterAndProtectionAreOnByDefault() {
        SocialConfig config = new SocialConfig();
        assertTrue(config.dmContactFilter);
        assertTrue(config.dmNewPlayerProtection);
    }

    @Test
    void rulesGotTheReportDmLineAndTheOldListIsRetired() throws Exception {
        for (String file : List.of("plugin/src/main/resources/messages/social.yml", "server/plugins/PhoenixSkyblock/messages/social.yml")) {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(ROOT.resolve(file).toFile());
            List<String> rules = yaml.getStringList("rules");
            List<String> expected = new ArrayList<>(RetiredTexts.RULES_V1);
            int privacy = expected.size() - 2; // after the privacy line, before "Full rules"
            expected.add(privacy, " <c:muted>Private messages attached to a /report are kept 180 days as evidence.");
            assertEquals(expected, rules, file + ": rules = old rules + the report DM line (else the retire never matches)");
            for (String key : List.of("dm.not-yet", "dm.blocked-link", "dm.blocked-ip", "dm.blocked-phone")) {
                assertTrue(yaml.isString(key), file + " lacks " + key);
            }
        }
    }
}
