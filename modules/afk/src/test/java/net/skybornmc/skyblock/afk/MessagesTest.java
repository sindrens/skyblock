package net.skybornmc.skyblock.afk;

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

/** Every message key the code uses exists, the file parses, and only Bedrock-safe symbols are used. */
class MessagesTest {

    private static final Pattern KEY = Pattern.compile("\"(entered|left|capped|ip-blocked|(?:reward|key|booster|action-bar|scoreboard)(?:\\.[a-z][a-z-]*)+)\"");
    private static final String SAFE = "✦✧◆●▸▶★✔✘⚔⛏❤⌛»«│·•";

    private static String text() throws Exception {
        try (var in = MessagesTest.class.getClassLoader().getResourceAsStream("messages/afk.yml")) {
            assertNotNull(in);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void usedKeysExist() throws Exception {
        YamlConfiguration yml = new YamlConfiguration();
        yml.loadFromString(text()); // throws on a broken quote
        Set<String> missing = new TreeSet<>();
        try (Stream<Path> files = Files.list(Path.of("src/main/java/net/skybornmc/skyblock/afk"))) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java") && !f.endsWith("AfkConfig.java")).toList()) {
                Matcher m = KEY.matcher(Files.readString(file));
                while (m.find()) {
                    if (!yml.isString(m.group(1))) {
                        missing.add(m.group(1) + " (" + file.getFileName() + ")");
                    }
                }
            }
        }
        assertEquals(Set.of(), missing);
        for (String id : new String[]{"ember", "cinder", "blaze"}) {
            assertNotNull(yml.getString("crate-names." + id), "crate name " + id);
        }
    }

    @Test
    void onlyBedrockSafeSymbols() throws Exception {
        Set<String> bad = new TreeSet<>();
        text().codePoints().filter(cp -> cp > 127 && SAFE.indexOf(cp) < 0).forEach(cp -> bad.add(new String(Character.toChars(cp))));
        assertEquals(Set.of(), bad);
    }
}
