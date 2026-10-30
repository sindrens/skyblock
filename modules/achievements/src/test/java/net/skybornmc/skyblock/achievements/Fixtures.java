package net.skybornmc.skyblock.achievements;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Shared test data: the bundled achievements.yml and messages. */
final class Fixtures {

    private Fixtures() {
    }

    static YamlConfiguration resource(String name) {
        try (var in = Fixtures.class.getClassLoader().getResourceAsStream(name)) {
            assertNotNull(in, name + " missing");
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static AchievementRegistry registry() {
        List<String> problems = new ArrayList<>();
        AchievementRegistry registry = AchievementRegistry.load(resource(AchievementRegistry.FILE), id -> true, problems::add);
        assertEquals(List.of(), problems, "bundled achievements.yml must load without problems");
        return registry;
    }

    static AchievementRegistry parse(String yaml, List<String> problems) {
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.loadFromString(yaml);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return AchievementRegistry.load(config, id -> !id.equals("not_an_item"), problems::add);
    }
}
