package net.skybornmc.skyblock.box;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
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

/** Every message key the code uses exists, every source has a name, the file parses, only Bedrock-safe symbols. */
class MessagesTest {

    private static final Pattern KEY = Pattern.compile("\"((?:join|action-bar|delivered|error)|(?:cap|take|profiles|menu|staff)(?:\\.[a-z][a-z-]*)+)\"");
    private static final String SAFE = "✦✧◆●▸▶★✔✘⚔⛏❤⌛»«│·•";

    private static String text() throws Exception {
        try (var in = MessagesTest.class.getClassLoader().getResourceAsStream("messages/box.yml")) {
            assertNotNull(in);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void usedKeysExist() throws Exception {
        YamlConfiguration yml = new YamlConfiguration();
        yml.loadFromString(text()); // throws on a broken quote
        Set<String> missing = new TreeSet<>();
        try (Stream<Path> files = Files.list(Path.of("src/main/java/net/skybornmc/skyblock/box"))) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java") && !f.endsWith("BoxConfig.java")).toList()) {
                Matcher m = KEY.matcher(Files.readString(file));
                while (m.find()) {
                    String k = m.group(1);
                    boolean item = yml.isString(k + ".name") && yml.isList(k + ".lore");
                    if (!yml.isString(k) && !yml.isList(k) && !item) {
                        missing.add(k + " (" + file.getFileName() + ")");
                    }
                }
            }
        }
        assertEquals(Set.of(), missing);
    }

    @Test
    void everySourceHasAName() throws Exception {
        YamlConfiguration yml = new YamlConfiguration();
        yml.loadFromString(text());
        for (Field f : net.skybornmc.skyblock.common.box.BoxSource.class.getFields()) {
            if (f.getType() == String.class) {
                String key = (String) f.get(null);
                assertTrue(yml.isString("sources." + key), "sources." + key);
            }
        }
        assertTrue(yml.isString("sources.other"));
    }

    @Test
    void onlyBedrockSafeSymbols() throws Exception {
        Set<String> bad = new TreeSet<>();
        text().codePoints().filter(cp -> cp > 127 && SAFE.indexOf(cp) < 0).forEach(cp -> bad.add(new String(Character.toChars(cp))));
        assertEquals(Set.of(), bad);
    }
}
