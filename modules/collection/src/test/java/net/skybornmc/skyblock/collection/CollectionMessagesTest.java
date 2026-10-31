package net.skybornmc.skyblock.collection;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Every message key the code uses exists in messages/collection.yml (owner rule: all player-facing text in messages). */
class CollectionMessagesTest {

    private static final Pattern LITERAL = Pattern.compile("\"([a-z][a-z-]*(?:\\.[a-z][a-z-]*)+)\"");

    private static YamlConfiguration yml() {
        try (var in = CollectionMessagesTest.class.getClassLoader().getResourceAsStream("messages/collection.yml")) {
            assertNotNull(in, "messages/collection.yml missing");
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean has(YamlConfiguration yml, String key) {
        return yml.contains(key) || yml.contains(key + ".name") || yml.isConfigurationSection(key);
    }

    @Test
    void literalKeysExist() throws Exception {
        YamlConfiguration yml = yml();
        Path src = Path.of("src/main/java/net/skybornmc/skyblock/collection");
        Set<String> missing = new TreeSet<>();
        try (Stream<Path> files = Files.list(src)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java") && !f.endsWith("CollectionSettings.java")).toList()) {
                Matcher m = LITERAL.matcher(Files.readString(file));
                while (m.find()) {
                    String key = m.group(1);
                    if (key.startsWith("skyblock.") || key.endsWith(".yml") || key.startsWith("collection.yml")) {
                        continue;
                    }
                    if (!has(yml, key)) {
                        missing.add(key + " (" + file.getFileName() + ")");
                    }
                }
            }
        }
        assertEquals(Set.of(), missing);
    }

    @Test
    void dynamicKeysExist() {
        YamlConfiguration yml = yml();
        List<String> keys = new ArrayList<>(List.of("text", "disabled", "titles.log", "hint.crates", "sources.other",
                "reward.title", "reward.key", "reward.set", "reward.none", "reward.separator", "menu.loading.name", "menu.empty.name",
                "menu.info", "menu.title", "menu.entry.locked"));
        for (Catalog.PageKind k : Catalog.PageKind.values()) {
            keys.add("pages." + k.id() + ".name");
            keys.add("titles." + k.id());
            if (k != Catalog.PageKind.CRATES) {
                keys.add("hint." + k.id());
                keys.add("sources.depths_" + k.id());
            }
        }
        for (String screen : List.of("found.screen", "milestone.screen", "milestone.page-done.screen", "milestone.all.screen")) {
            keys.add(screen + ".title");
            keys.add(screen + ".subtitle");
        }
        Set<String> missing = new TreeSet<>();
        for (String k : keys) {
            if (!has(yml, k)) {
                missing.add(k);
            }
        }
        assertEquals(Set.of(), missing);
    }
}
