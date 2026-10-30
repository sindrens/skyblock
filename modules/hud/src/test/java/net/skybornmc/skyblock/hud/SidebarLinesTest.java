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

    /**
     * UX1 + SB: every bundled layout stays short (title + max 9 lines, conditional lines included), every place layout
     * named in hud.yml's defaults exists, and each layout shows what is relevant for its place.
     */
    @Test
    void bundledLayoutsAreShort() {
        HudConfig defaults = new HudConfig();
        List<String> places = new java.util.ArrayList<>(List.of("default", defaults.islandLayout, defaults.islandVisitLayout,
                defaults.depthsLayout, defaults.spawnLayout, defaults.warzoneLayout));
        PlaceRules.parseVariants(defaults.depthsVariants, p -> {
            throw new AssertionError(p);
        }).forEach(v -> places.add(v.layout()));
        for (String file : List.of("../../plugin/src/main/resources/messages/hud.yml", "../../server/plugins/PhoenixSkyblock/messages/hud.yml")) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(Path.of(file).toFile());
            var section = yaml.getConfigurationSection("scoreboard");
            assertTrue(section != null, file);
            for (String layout : section.getKeys(false)) {
                List<String> lines = yaml.getStringList("scoreboard." + layout + ".lines");
                assertTrue(yaml.isString("scoreboard." + layout + ".title"), file + " " + layout + " title");
                assertTrue(!lines.isEmpty() && lines.size() <= 9, file + " " + layout + ": " + lines.size() + " lines");
                for (String line : lines) {
                    assertTrue(!line.startsWith(LayoutLines.IF) || LayoutLines.line(line).condition() != null,
                            file + " " + layout + ": broken if: line " + line);
                }
            }
            for (String layout : places) {
                assertTrue(section.isConfigurationSection(layout), file + " misses layout " + layout);
            }
            contains(yaml, file, "depths", "<ph:depths_zone>", "<ph:depths_tier>", "<ph:skill_mining_level>");
            contains(yaml, file, "depths-forest", "<ph:depths_zone>", "<ph:skill_foraging_level>");
            contains(yaml, file, "depths-combat", "<ph:depths_zone>", "<ph:skill_combat_level>");
            contains(yaml, file, "depths-gate", "<ph:skill_mining_level>", "<ph:skill_foraging_level>", "<ph:skill_combat_level>");
            contains(yaml, file, "island", "<island>", "if:island_next_note|");
            contains(yaml, file, "island-visit", "<island_here>");
        }
    }

    private static void contains(YamlConfiguration yaml, String file, String layout, String... parts) {
        String text = String.join("\n", yaml.getStringList("scoreboard." + layout + ".lines"));
        for (String part : parts) {
            assertTrue(text.contains(part), file + " " + layout + " layout misses " + part);
        }
    }
}
