package net.skybornmc.skyblock.menus;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** GUI-2: a live menu.yml with the old 28-entry hub and the UX3 warps list becomes the clean hub, You and Travel, once. */
class HubV2MigrationTest {

    private static YamlConfiguration bundled() throws Exception {
        try (var in = HubV2MigrationTest.class.getClassLoader().getResourceAsStream("menu.yml")) {
            assertNotNull(in);
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            return yaml;
        }
    }

    /** The live file of update108: old entries and warps, plus what YamlFiles adds from the new bundled file. */
    private static YamlConfiguration live() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("entries.island.slot", 10);
        yaml.set("entries.island.command", "is");
        yaml.set("entries.warps.slot", 28);
        yaml.set("entries.warps.command", "warps");
        yaml.set("entries.help.slot", 41);
        yaml.set("entries.help.command", "help");
        yaml.set("warps.spawn.slot", 20);
        yaml.set("warps.spawn.target", "command spawn");
        yaml.set("warps.market.slot", 21);
        yaml.set("warps.market.target", "location spawn 4.5 99 40.5 0 0"); // moved by the owner
        yaml.set("warps.depths.slot", 31);
        yaml.set("warps.depths.target", "location depths 7.5 100 14.5 165 0");
        yaml.set("warps.depths.reason", "dungeon");
        yaml.set("warps.tavern.slot", 24);
        yaml.set("warps.tavern.target", "point entries tavern_road");
        yaml.set("staff-entries.admin.slot", 21);
        yaml.set("staff-entries.admin.command", "admin");
        YamlConfiguration bundled = bundled();
        for (String key : bundled.getKeys(true)) { // same as YamlFiles: new leaf keys are added
            if (!bundled.isConfigurationSection(key) && !yaml.contains(key)) {
                yaml.set(key, bundled.get(key));
            }
        }
        return yaml;
    }

    @Test
    void oldLayoutIsReplacedAndPlacesKeepTheirSpot() throws Exception {
        YamlConfiguration yaml = live();
        assertTrue(HubV2Migration.apply(yaml));
        assertFalse(yaml.contains("entries"));
        assertFalse(yaml.contains("warps"));
        assertEquals("location spawn 4.5 99 40.5 0 0", yaml.getString("travel.market.target"), "owner-moved market carried over");
        assertEquals("dungeon", yaml.getString("travel.depths-gate.reason"));
        assertEquals("admin", yaml.getString("staff-entries.admin.command"), "/staff untouched");
        assertFalse(yaml.contains("travel.tavern"), "Tavern is not a Travel place");

        List<String> problems = new ArrayList<>();
        assertEquals(15, ButtonLayout.hub(yaml, HubButtonsTest.ITEMS, problems::add).buttons().size());
        assertEquals(8, ButtonLayout.you(yaml, HubButtonsTest.ITEMS, problems::add).buttons().size(), "6 + Quests (M10.7) + Collection (M10.10)");
        assertEquals(7, WarpLayout.parse(yaml, HubButtonsTest.ITEMS, problems::add).warps().size());
        assertEquals(List.of(), problems);

        // Saved and loaded again: the migration never runs twice.
        YamlConfiguration again = new YamlConfiguration();
        again.loadFromString(yaml.saveToString());
        assertFalse(HubV2Migration.apply(again));
        assertEquals("location spawn 4.5 99 40.5 0 0", again.getString("travel.market.target"));
    }

    @Test
    void aFreshFileIsLeftAlone() throws Exception {
        assertFalse(HubV2Migration.apply(bundled()));
        assertFalse(HubV2Migration.apply(new YamlConfiguration()));
    }
}
