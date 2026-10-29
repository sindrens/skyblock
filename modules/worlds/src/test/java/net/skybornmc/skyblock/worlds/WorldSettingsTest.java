package net.skybornmc.skyblock.worlds;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldSettingsTest {

    private static YamlConfiguration bundledConfig() {
        var stream = Objects.requireNonNull(WorldSettingsTest.class.getResourceAsStream("/worlds.yml"));
        return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }

    @Test
    void bundledConfigDefinesAllDesignWorlds() {
        YamlConfiguration config = bundledConfig();
        Map<String, Object> defaults = WorldSettings.parseDefaults(config);
        ConfigurationSection worlds = config.getConfigurationSection("worlds");

        assertEquals(Set.of("spawn", "islands", "outposts", "depths", "warzone"), worlds.getKeys(false));
        for (String name : worlds.getKeys(false)) {
            WorldSettings s = WorldSettings.parse(name, worlds.getConfigurationSection(name), defaults);
            assertTrue(s.gamerules().containsKey("keep_inventory"), name + " inherits defaults");
            assertTrue(s.spawnY() > 0, name + " spawn above void");
        }
    }

    @Test
    void worldRulesOverrideDefaultsAndPvpOnlyInWarzone() {
        YamlConfiguration config = bundledConfig();
        Map<String, Object> defaults = WorldSettings.parseDefaults(config);
        ConfigurationSection worlds = config.getConfigurationSection("worlds");

        for (String name : worlds.getKeys(false)) {
            WorldSettings s = WorldSettings.parse(name, worlds.getConfigurationSection(name), defaults);
            assertEquals("warzone".equals(name), s.gamerules().get("pvp"), "pvp in " + name);
        }
        WorldSettings spawn = WorldSettings.parse("spawn", worlds.getConfigurationSection("spawn"), defaults);
        assertFalse(spawn.create(), "main world is created by the server");
        assertEquals("PEACEFUL", spawn.difficulty());
    }

    @Test
    void islandsAreNeverPeaceful() {
        YamlConfiguration config = bundledConfig();
        ConfigurationSection worlds = config.getConfigurationSection("worlds");
        for (String name : Set.of("islands", "outposts")) {
            WorldSettings s = WorldSettings.parse(name, worlds.getConfigurationSection(name), Map.of());
            assertFalse("PEACEFUL".equals(s.difficulty()), name + " must not be peaceful (spawner mobs despawn)");
        }
    }

    @Test
    void rejectsInvalidValues() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("time", 30000);
        assertThrows(IllegalArgumentException.class, () -> WorldSettings.parse("ok", config, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> WorldSettings.parse("Bad Name", new YamlConfiguration(), Map.of()));
    }

    @Test
    void missingOptionalValuesUseSafeDefaults() {
        WorldSettings s = WorldSettings.parse("test", new YamlConfiguration(), Map.of());
        assertTrue(s.create());
        assertEquals("the_void", s.biome());
        assertNull(s.time());
        assertEquals(0, s.border());
    }
}
