package net.skybornmc.skyblock.production;

import net.skybornmc.skyblock.common.production.FuelState;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every message key the code uses exists in messages/production.yml (owner rule: all player-facing text in messages). */
class ProductionMessagesTest {

    private static final Pattern LITERAL = Pattern.compile("\"([a-z][a-z-]*(?:\\.[a-z][a-z-]*)+)\"");

    private static YamlConfiguration yml() {
        try (var in = ProductionMessagesTest.class.getClassLoader().getResourceAsStream("messages/production.yml")) {
            assertNotNull(in);
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void literalKeysExist() throws Exception {
        YamlConfiguration yml = yml();
        Path src = Path.of("src/main/java/net/skybornmc/skyblock/production");
        Set<String> missing = new TreeSet<>();
        try (Stream<Path> files = Files.list(src)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java") && !f.endsWith("ProductionSettings.java")).toList()) {
                Matcher m = LITERAL.matcher(Files.readString(file));
                while (m.find()) {
                    String key = m.group(1);
                    if (key.startsWith("skyblock.") || key.endsWith(".yml") || key.endsWith("-") || key.startsWith("production:")) {
                        continue;
                    }
                    if (!yml.contains(key)) {
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
        for (FuelState s : FuelState.values()) {
            assertTrue(yml.contains("state." + s.key()), "state." + s.key());
            assertTrue(yml.contains("gauge.state-" + s.key()), "gauge.state-" + s.key());
        }
        assertTrue(yml.contains("gauge.state-never"));
    }

    @Test
    void noUnescapedDoubleQuotesOrUnsafeSymbols() {
        YamlConfiguration yml = yml();
        for (String key : yml.getKeys(true)) {
            Object v = yml.get(key);
            String text = v instanceof String s ? s : v instanceof java.util.List<?> l ? String.join("\n", l.stream().map(String::valueOf).toList()) : null;
            if (text == null) {
                continue;
            }
            for (int cp : text.codePoints().toArray()) {
                if (cp > 127) {
                    assertTrue("✦✧◆●▸▶★✔✘⚔⛏❤⌛»«│·•".indexOf(cp) >= 0, key + " uses a symbol Bedrock may not show: " + new String(Character.toChars(cp)));
                }
            }
            assertFalse(text.contains("<sym:fuel>"), key);
        }
    }
}
