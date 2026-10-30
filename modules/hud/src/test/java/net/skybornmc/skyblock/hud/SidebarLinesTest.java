package net.skybornmc.skyblock.hud;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SidebarLinesTest {

    @Test
    void entriesAreUniqueAndInvisible() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 32; i++) {
            String e = SidebarLines.entry(i);
            assertTrue(seen.add(e), "duplicate entry for line " + i);
            assertTrue(e.replaceAll("§.", "").isEmpty(), "entry must only contain color codes");
        }
        assertThrows(IllegalArgumentException.class, () -> SidebarLines.entry(32));
    }

    @Test
    void onlyChangedLinesAreReported() {
        assertEquals(List.of(1), SidebarLines.changed(List.of("a", "b", "c"), List.of("a", "x", "c")));
        assertEquals(List.of(), SidebarLines.changed(List.of("a"), List.of("a", "b")));
    }

    @Test
    void playersAreSpreadOverTheInterval() {
        int due = 0;
        for (long tick = 0; tick < 20; tick++) {
            if (SidebarLines.due(12345, tick, 20)) {
                due++;
            }
        }
        assertEquals(1, due);
        assertTrue(SidebarLines.due(-7, 13, 20), "negative hashes map with floorMod");
    }

    @Test
    void worldLayoutWinsOverDefault() {
        assertEquals("depths", SidebarLines.layout("depths", Set.of("default", "depths")::contains));
        assertEquals("default", SidebarLines.layout("islands", Set.of("default", "depths")::contains));
        assertEquals("default", SidebarLines.layout("", Set.of("")::contains));
    }

    /** UX1: the bundled layouts stay short (title + max 9 lines) and the Depths one shows zone, tier and mining level. */
    @Test
    void bundledLayoutsAreShort() {
        for (String file : List.of("../../plugin/src/main/resources/messages/hud.yml", "../../server/plugins/PhoenixSkyblock/messages/hud.yml")) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(Path.of(file).toFile());
            for (String layout : List.of("default", "depths")) {
                List<String> lines = yaml.getStringList("scoreboard." + layout + ".lines");
                assertTrue(yaml.isString("scoreboard." + layout + ".title"), file + " " + layout + " title");
                assertTrue(!lines.isEmpty() && lines.size() <= 9, file + " " + layout + ": " + lines.size() + " lines");
            }
            String depths = String.join("\n", yaml.getStringList("scoreboard.depths.lines"));
            for (String ph : List.of("<ph:depths_zone>", "<ph:depths_tier>", "<ph:skill_mining_level>")) {
                assertTrue(depths.contains(ph), file + " depths layout misses " + ph);
            }
        }
    }
}
