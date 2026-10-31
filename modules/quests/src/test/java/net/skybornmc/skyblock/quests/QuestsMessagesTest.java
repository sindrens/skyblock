package net.skybornmc.skyblock.quests;

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
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every message key the code uses exists in messages/quests.yml (owner rule: all player-facing text in messages). */
class QuestsMessagesTest {

    private static final Pattern LITERAL = Pattern.compile("\"([a-z][a-z-]*(?:\\.[a-z][a-z-]*)+)\"");

    private static YamlConfiguration yml() {
        try (var in = QuestsMessagesTest.class.getClassLoader().getResourceAsStream("messages/quests.yml")) {
            assertNotNull(in, "messages/quests.yml missing");
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean has(YamlConfiguration yml, String key) {
        return yml.contains(key) || yml.contains(key + ".name") || yml.contains(key + ".title") || yml.isConfigurationSection(key);
    }

    @Test
    void literalKeysExist() throws Exception {
        YamlConfiguration yml = yml();
        Path src = Path.of("src/main/java/net/skybornmc/skyblock/quests");
        Set<String> missing = new TreeSet<>();
        try (Stream<Path> files = Files.list(src)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java") && !f.endsWith("QuestsSettings.java")).toList()) {
                Matcher m = LITERAL.matcher(Files.readString(file));
                while (m.find()) {
                    String key = m.group(1);
                    if (key.startsWith("skyblock.") || key.endsWith(".yml")) {
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
        List<String> keys = new ArrayList<>();
        for (QuestType t : QuestType.values()) {
            keys.add("quest." + t.id() + ".name");
            keys.add("quest." + t.id() + ".short");
            if (t.depths()) {
                keys.add("quest." + t.id() + ".where");
            }
        }
        for (QuestKind k : QuestKind.values()) {
            keys.add("menu.tabs." + k.id() + ".crumb");
            keys.add("menu.tabs." + k.id() + ".name");
            keys.add("menu.tabs." + k.id() + ".lore");
            keys.add("menu.info." + k.id());
        }
        keys.addAll(List.of("menu.summary.daily-name", "menu.summary.weekly-name", "done.screen.title", "done.island-screen.title",
                "crates.cinder", "crates.blaze"));
        Set<String> missing = new TreeSet<>();
        for (String key : keys) {
            if (!has(yml, key)) {
                missing.add(key);
            }
        }
        assertEquals(Set.of(), missing);
    }

    @Test
    void onlyBedrockSafeSymbols() {
        String safe = "✦✧◆●▸▶★✔✘⚔⛏❤⌛»«│·•";
        YamlConfiguration yml = yml();
        for (String key : yml.getKeys(true)) {
            Object value = yml.get(key);
            List<String> texts = value instanceof List<?> list ? list.stream().map(String::valueOf).toList()
                    : value instanceof String s ? List.of(s) : List.of();
            for (String text : texts) {
                for (char ch : text.toCharArray()) {
                    assertTrue(ch < 0x2000 || safe.indexOf(ch) >= 0, key + " uses '" + ch + "', which Bedrock may not show");
                }
            }
        }
    }
}
