package net.skybornmc.skyblock.essentials;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Owner 3 Nov 2026 start flow: island at join, the guide boss bar until the player met the guide, /start buttons. */
class StartRulesTest {

    @Test
    void islandIsCreatedForNewAndIslandlessPlayers() {
        assertTrue(StartRules.createIsland(true, false, true, false, true), "new player");
        assertTrue(StartRules.createIsland(false, false, true, true, true), "returning player without an island");
        assertFalse(StartRules.createIsland(false, false, true, false, true), "islandless setting off");
        assertFalse(StartRules.createIsland(true, true, true, true, true), "already has an island");
        assertFalse(StartRules.createIsland(true, false, true, true, false), "staff in creative/spectator");
        assertFalse(StartRules.createIsland(true, false, false, false, true), "both off");
    }

    @Test
    void guideBarShowsUntilMet() {
        assertTrue(StartRules.showGuide(true, null));
        assertTrue(StartRules.showGuide(true, " "));
        assertFalse(StartRules.showGuide(true, "1730000000000"));
        assertFalse(StartRules.showGuide(false, null), "start.enabled off");
    }

    @Test
    void guideRoleAndCommands() {
        assertTrue(StartRules.guideRole(" Guide ", "guide"));
        assertFalse(StartRules.guideRole("quests", "guide"));
        assertFalse(StartRules.guideRole(null, "guide"));
        assertEquals("is home", StartRules.command("/is home "));
        assertEquals("farm", StartRules.command("farm"));
        assertEquals("", StartRules.command(null));
    }

    @Test
    void defaultsMatchTheOwnersAnswers() {
        EssentialsConfig c = new EssentialsConfig();
        assertTrue(c.startEnabled);
        assertTrue(c.startIslandOnFirstJoin);
        assertTrue(c.startIslandForIslandless);
        assertEquals("guide", c.startGuideRole);
        assertEquals(List.of("is home", "farm", "mine"), List.of(c.startIslandCommand, c.startFarmCommand, c.startMineCommand));
    }

    @Test
    void gateOpensOnlyAfterTheGuideOrWithBypass() {
        assertFalse(StartRules.gateOpen(true, true, false, true, null), "not met yet: locked");
        assertFalse(StartRules.gateOpen(true, true, false, true, " "), "blank = not met");
        assertFalse(StartRules.gateOpen(true, true, false, false, null), "not loaded: stays locked");
        assertTrue(StartRules.gateOpen(true, true, false, true, "1730700000000"), "met: open");
        assertTrue(StartRules.gateOpen(true, true, true, true, null), "staff bypass");
        assertTrue(StartRules.gateOpen(false, true, false, true, null), "start flow off: nothing to meet");
        assertTrue(StartRules.gateOpen(true, false, false, true, null), "gate off");
        assertEquals(net.skybornmc.skyblock.common.player.StartGate.MET_KEY, StartRules.MET_KEY, "one flag, shared");
    }

    @Test
    void textsExistInBothCopies() {
        for (String file : List.of("../../plugin/src/main/resources/messages/essentials.yml",
                "../../server/plugins/PhoenixSkyblock/messages/essentials.yml")) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new File(file));
            for (String key : List.of("start.guide-name", "start.bossbar", "start.objective", "start.welcome", "start.menu.title",
                    "start.menu.island.name", "start.menu.farm.name", "start.menu.mine.name", "start.locked")) {
                assertTrue(yaml.isString(key), file + " misses " + key);
            }
            for (String button : List.of("island", "farm", "mine")) {
                List<String> lore = yaml.getStringList("start.menu." + button + ".lore");
                assertTrue(lore.getLast().contains("» CLICK"), file + " " + button + ": lore ends with the click line");
            }
            assertEquals("Rowan", yaml.getString("start.guide-name"));
        }
        for (String file : List.of("../npcs/src/main/resources/messages/npcs.yml", "../../server/plugins/PhoenixSkyblock/messages/npcs.yml")) {
            assertEquals("Rowan", YamlConfiguration.loadConfiguration(new File(file)).getString("roles.guide.name"), file);
        }
    }
}
