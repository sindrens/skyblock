package net.skybornmc.skyblock.islands;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** BUILDS: the builders' metadata of the starter island (world coordinates) becomes offsets from the paste origin. */
class IslandMetadataTest {

    @Test
    void readsTheBuildersStarterIsland() {
        IslandBuilder.Metadata m = IslandBuilder.readMetadata(new File("../../server/builds/islands_starter_meadow_v1.yml"));
        assertArrayEquals(new int[]{0, 1, 0}, m.home());   // feet (0.5, 101, 0.5) above origin (0, 100, 0)
        assertEquals(180f, m.homeYaw());
        assertArrayEquals(new int[]{-1, 1, -2}, m.chest()); // containers: starter_chest
        assertArrayEquals(new int[]{1, 2, -2}, m.sign());   // signs: menu_sign
    }

    @Test
    void oldOffsetFormatStillWorks() throws InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("home: { x: 0, y: 1, z: 0, yaw: 90 }\nchest: { x: 0, y: 1, z: -3 }\n");
        IslandBuilder.Metadata m = IslandBuilder.readMetadata(yaml);
        assertArrayEquals(new int[]{0, 1, 0}, m.home());
        assertEquals(90f, m.homeYaw());
        assertArrayEquals(new int[]{0, 1, -3}, m.chest());
        assertEquals(null, m.sign());
    }

    @Test
    void builderFormatWithAnotherOrigin() throws InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                origin: { x: 10, y: 64, z: -5 }
                home: { x: 12.5, y: 65, z: -4.5, yaw: 0 }
                containers:
                  - { id: other, x: 1, y: 1, z: 1 }
                  - { id: starter_chest, x: 11, y: 65, z: -7 }
                """);
        IslandBuilder.Metadata m = IslandBuilder.readMetadata(yaml);
        assertArrayEquals(new int[]{2, 1, 0}, m.home()); // block (12, 65, -5)
        assertArrayEquals(new int[]{1, 1, -2}, m.chest());
    }
}
