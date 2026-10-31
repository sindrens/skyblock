package net.skybornmc.skyblock.menus;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** GUI-2: the /menu → Travel places (menu.yml → travel, was the UX3 warps list), their targets and texts. */
class TravelLayoutTest {

    private static YamlConfiguration resource(String name) throws Exception {
        try (var in = TravelLayoutTest.class.getClassLoader().getResourceAsStream(name)) {
            assertNotNull(in, name);
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            return yaml;
        }
    }

    @Test
    void targetsParse() {
        assertEquals(new WarpLayout.Command("spawn"), WarpLayout.target("command spawn"));
        assertEquals(new WarpLayout.Command("is warp"), WarpLayout.target("command  /is warp "));
        assertEquals(new WarpLayout.Point("entries", "tavern_road"), WarpLayout.target("point entries tavern_road"));
        assertEquals(new WarpLayout.Place("spawn", 0.5, 99, 33.5, 0f, 0f), WarpLayout.target("location spawn 0.5 99 33.5"));
        assertEquals(new WarpLayout.Place("depths", 7.5, 100, 14.5, 165f, 90f), WarpLayout.target("location depths 7.5 100 14.5 165 200"),
                "pitch clamped");
        for (String bad : new String[]{null, "", "spawn", "command", "command /", "point entries", "point a b c",
                "location spawn 1 2", "location spawn 1 2 3 4", "location spawn x 2 3", "location spawn NaN 2 3", "teleport spawn"}) {
            assertNull(WarpLayout.target(bad), String.valueOf(bad));
        }
    }

    @Test
    void roadEntriesStandInTheMiddleOfTheBlock() {
        assertEquals(-45.5, WarpLayout.centre(-46));
        assertEquals(10.5, WarpLayout.centre(10));
        assertEquals(7.5, WarpLayout.centre(7.5), "feet positions stay");
    }

    @Test
    void reasonsMapToTeleportReasons() {
        assertEquals("spawn", TravelMenu.reason("spawn").id());
        assertEquals("dungeon", TravelMenu.reason("dungeon").id());
        assertEquals("spawn", TravelMenu.reason("nonsense").id(), "fallback: the /spawn warmup");
    }

    /** Every bundled place has a valid target, a slot of the 3-row menu and its texts (GUI_DESIGN §7.3: 7 places). */
    @Test
    void bundledPlacesAreComplete() throws Exception {
        YamlConfiguration menu = resource("menu.yml");
        YamlConfiguration texts = resource("messages/navigation.yml");
        ConfigurationSection travel = menu.getConfigurationSection("travel");
        assertNotNull(travel);
        Set<Integer> slots = new HashSet<>();
        for (String id : travel.getKeys(false)) {
            ConfigurationSection e = travel.getConfigurationSection(id);
            assertNotNull(e, id);
            int slot = e.getInt("slot", -1);
            assertTrue(ButtonLayout.SMALL_SLOTS.contains(slot) && slots.add(slot), id + " slot " + slot);
            assertNotNull(WarpLayout.target(e.getString("target")), id + " target");
            assertTrue(WarpLayout.REASONS.contains(e.getString("reason", WarpLayout.DEFAULT_REASON)), id + " reason");
            assertTrue(texts.isString("travel.places." + id + ".name"), id + " name");
            assertTrue(texts.isList("travel.places." + id + ".lore"), id + " lore");
        }
        assertEquals(java.util.List.of("spawn", "my-island", "depths-gate", "crate-yard", "market", "forge-quarter", "visit-islands"),
                java.util.List.copyOf(travel.getKeys(false)));
        java.util.List<String> problems = new java.util.ArrayList<>();
        assertEquals(7, WarpLayout.parse(menu, HubButtonsTest.ITEMS, problems::add).warps().size());
        assertEquals(java.util.List.of(), problems);
        assertInstanceOf(WarpLayout.Command.class, WarpLayout.target(travel.getString("spawn.target")));
        assertEquals("dungeon", travel.getString("depths-gate.reason"));
        for (String key : new String[]{"travel.title", "travel.action", "travel.fallback-name", "travel.gone", "travel.empty.name"}) {
            assertTrue(texts.contains(key), key);
        }
    }

    @Test
    void placesOutsideTheMiddleRowAreSkipped() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("travel.a.slot", 20); // a 6-row slot: not in a 3-row menu
        yaml.set("travel.a.target", "command spawn");
        yaml.set("travel.b.slot", 10);
        yaml.set("travel.b.target", "command spawn");
        yaml.set("travel.c.slot", 10);
        yaml.set("travel.c.target", "command spawn");
        java.util.List<String> problems = new java.util.ArrayList<>();
        WarpLayout layout = WarpLayout.parse(yaml, HubButtonsTest.ITEMS, problems::add);
        assertEquals(1, layout.warps().size());
        assertEquals("b", layout.warps().getFirst().id());
        assertEquals(2, problems.size(), "outside the row + slot used twice");
    }
}
