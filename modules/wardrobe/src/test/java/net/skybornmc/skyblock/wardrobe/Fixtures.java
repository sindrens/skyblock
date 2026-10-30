package net.skybornmc.skyblock.wardrobe;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Shared test data: the bundled sets.yml and messages. */
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

    static SetRegistry registry() {
        List<String> problems = new ArrayList<>();
        SetRegistry registry = SetRegistry.load(resource("sets.yml"), problems::add);
        assertEquals(List.of(), problems, "bundled sets.yml must load without problems");
        return registry;
    }

    static SetDef def(String id) {
        return registry().get(id).orElseThrow(() -> new AssertionError("no set " + id));
    }
}
