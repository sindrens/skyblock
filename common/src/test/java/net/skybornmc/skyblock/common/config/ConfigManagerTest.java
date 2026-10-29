package net.skybornmc.skyblock.common.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigManagerTest {

    enum Color { RED, DARK_RED }

    static final class TestConfig extends ModuleConfig {
        @Setting(desc = "A number", min = 0, max = 100) int amount = 10;
        @Setting(desc = "A flag") boolean enabled = true;
        @Setting(path = "nested.name", desc = "A name") String name = "phoenix";
        @Setting(desc = "A color") Color color = Color.RED;
        @Setting(desc = "Some lines") List<String> lines = List.of("a");
        int version = 1;
        Map<Integer, Map<String, String>> migrations = Map.of();

        @Override public String fileName() { return "test.yml"; }
        @Override public int version() { return version; }
        @Override public Map<Integer, Map<String, String>> migrations() { return migrations; }
    }

    private final Logger log = Logger.getLogger("test");

    @Test
    void createsFileWithDefaults(@TempDir Path dir) {
        ConfigManager manager = new ConfigManager(dir.toFile(), log);
        assertTrue(manager.load(new TestConfig()).isEmpty());
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dir.resolve("test.yml").toFile());
        assertEquals(10, yaml.getInt("amount"));
        assertEquals("phoenix", yaml.getString("nested.name"));
        assertEquals("red", yaml.getString("color"));
        assertEquals(1, yaml.getInt(ConfigManager.VERSION_KEY));
    }

    @Test
    void mergesMissingKeysWithoutOverwriting(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("test.yml"), "amount: 42\n");
        TestConfig config = new TestConfig();
        new ConfigManager(dir.toFile(), log).load(config);
        assertEquals(42, config.amount);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dir.resolve("test.yml").toFile());
        assertEquals(42, yaml.getInt("amount"));
        assertTrue(yaml.getBoolean("enabled"));
    }

    @Test
    void outOfRangeKeepsPreviousValue(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("test.yml"), "amount: 50\n");
        TestConfig config = new TestConfig();
        ConfigManager manager = new ConfigManager(dir.toFile(), log);
        manager.load(config);
        Files.writeString(dir.resolve("test.yml"), "amount: 500\n");
        List<String> problems = manager.load(config);
        assertEquals(1, problems.size());
        assertTrue(problems.getFirst().contains("amount"));
        assertTrue(problems.getFirst().contains("0 to 100"));
        assertEquals(50, config.amount);
    }

    @Test
    void wrongTypeKeepsDefault(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("test.yml"), "amount: lots\nenabled: maybe\ncolor: blue\n");
        TestConfig config = new TestConfig();
        List<String> problems = new ConfigManager(dir.toFile(), log).load(config);
        assertEquals(3, problems.size());
        assertEquals(10, config.amount);
        assertTrue(config.enabled);
        assertEquals(Color.RED, config.color);
    }

    @Test
    void brokenYamlNeverOverwritesFile(@TempDir Path dir) throws Exception {
        String broken = "amount: [unclosed\n";
        Files.writeString(dir.resolve("test.yml"), broken);
        TestConfig config = new TestConfig();
        ConfigManager manager = new ConfigManager(dir.toFile(), log);
        assertEquals(1, manager.load(config).size());
        assertEquals(broken, Files.readString(dir.resolve("test.yml")));
        assertFalse(manager.problems().isEmpty());
    }

    @Test
    void enumAcceptsKebabCase(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("test.yml"), "color: dark-red\n");
        TestConfig config = new TestConfig();
        new ConfigManager(dir.toFile(), log).load(config);
        assertEquals(Color.DARK_RED, config.color);
    }

    @Test
    void migratesRenamedKeys(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("test.yml"), "config-version: 1\nold-amount: 77\n");
        TestConfig config = new TestConfig();
        config.version = 2;
        config.migrations = Map.of(2, Map.of("old-amount", "amount"));
        new ConfigManager(dir.toFile(), log).load(config);
        assertEquals(77, config.amount);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(dir.resolve("test.yml").toFile());
        assertFalse(yaml.contains("old-amount"));
        assertEquals(2, yaml.getInt(ConfigManager.VERSION_KEY));
    }

    @Test
    void setValidatesAndDescribeReportsDefaults(@TempDir Path dir) {
        TestConfig config = new TestConfig();
        ConfigManager manager = new ConfigManager(dir.toFile(), log);
        manager.load(config);
        manager.set(config, "amount", "20");
        assertEquals(20, config.amount);
        assertThrows(IllegalArgumentException.class, () -> manager.set(config, "amount", "-1"));
        assertThrows(IllegalArgumentException.class, () -> manager.set(config, "nope", "1"));
        SettingInfo info = manager.describe(config).stream().filter(i -> i.path().equals("amount")).findFirst().orElseThrow();
        assertEquals(20, info.value());
        assertEquals(10, info.defaultValue());
    }

    @Test
    void kebabCasePaths() throws Exception {
        assertEquals("slow-operation-ms", ConfigManager.pathOf(CoreConfig.class.getField("slowOperationMs"),
                CoreConfig.class.getField("slowOperationMs").getAnnotation(Setting.class)));
        assertTrue(new File(".").exists());
    }
}
