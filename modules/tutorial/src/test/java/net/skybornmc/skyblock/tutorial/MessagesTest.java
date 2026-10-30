package net.skybornmc.skyblock.tutorial;

import net.skybornmc.skyblock.common.tutorial.Gates;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** messages/tutorial.yml parses, every literal key the code uses exists, every step and gate has its texts, Bedrock-safe. */
class MessagesTest {

    private static final Pattern KEY = Pattern.compile("\"([a-z][a-z-]*(?:\\.[a-z][a-z-]*)+)\"");
    private static final String SAFE = "✦✧◆●▸▶★✔✘⚔⛏❤⌛»«│·•";

    private static YamlConfiguration yml() throws Exception {
        try (var in = MessagesTest.class.getClassLoader().getResourceAsStream("messages/tutorial.yml")) {
            assertNotNull(in);
            YamlConfiguration y = new YamlConfiguration();
            y.loadFromString(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            return y;
        }
    }

    private static boolean exists(YamlConfiguration y, String k) {
        return y.isString(k) || y.isList(k) || y.isConfigurationSection(k);
    }

    @Test
    void usedKeysExist() throws Exception {
        YamlConfiguration y = yml();
        Set<String> missing = new TreeSet<>();
        try (Stream<Path> files = Files.list(Path.of("src/main/java/net/skybornmc/skyblock/tutorial"))) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java") && !f.endsWith("TutorialConfig.java")).toList()) {
                Matcher m = KEY.matcher(Files.readString(file));
                while (m.find()) {
                    String k = m.group(1);
                    if (k.startsWith("skyblock.") || k.startsWith("tutorial.") || k.startsWith("starter-") || k.endsWith("-")) {
                        continue; // permissions, config paths
                    }
                    if (!exists(y, k)) {
                        missing.add(k + " (" + file.getFileName() + ")");
                    }
                }
            }
        }
        assertEquals(Set.of(), missing);
    }

    @Test
    void everyStepAndGateHasItsTexts() throws Exception {
        YamlConfiguration y = yml();
        for (String step : Steps.KNOWN) {
            for (String part : new String[]{"name", "objective", "hint", "lore"}) {
                assertTrue(exists(y, "steps." + step + "." + part), "steps." + step + "." + part);
            }
        }
        for (String gate : Gates.ALL) {
            assertTrue(y.isString("gates." + gate), "gates." + gate);
        }
        for (String state : new String[]{"new", "active", "finished", "legacy", "staff_skipped"}) {
            assertTrue(y.isString("states." + state), "states." + state);
        }
        for (String dir : new String[]{"ahead", "left", "right", "behind", "here"}) {
            assertTrue(y.isString("compass." + dir), "compass." + dir);
        }
        assertEquals(6, y.getStringList("book.pages").size());
    }

    @Test
    void onlyBedrockSafeSymbols() throws Exception {
        String text;
        try (var in = MessagesTest.class.getClassLoader().getResourceAsStream("messages/tutorial.yml")) {
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        Set<String> bad = new TreeSet<>();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch > 0x7E && SAFE.indexOf(ch) < 0) {
                bad.add(String.valueOf(ch));
            }
        }
        assertEquals(Set.of(), bad);
    }
}
