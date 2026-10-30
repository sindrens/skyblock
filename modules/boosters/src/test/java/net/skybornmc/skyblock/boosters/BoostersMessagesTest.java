package net.skybornmc.skyblock.boosters;

import net.skybornmc.skyblock.common.booster.BoosterType;
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

/** Every message key the code uses exists in messages/boosters.yml (owner rule: all player-facing text in messages). */
class BoostersMessagesTest {

    private static final Pattern LITERAL = Pattern.compile("\"([a-z][a-z-]*(?:\\.[a-z][a-z-]*)+)\"");

    private static YamlConfiguration yml() {
        try (var in = BoostersMessagesTest.class.getClassLoader().getResourceAsStream("messages/boosters.yml")) {
            assertNotNull(in);
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void literalKeysExist() throws Exception {
        YamlConfiguration yml = yml();
        Path src = Path.of("src/main/java/net/skybornmc/skyblock/boosters");
        Set<String> missing = new TreeSet<>();
        try (Stream<Path> files = Files.list(src)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java") && !f.endsWith("BoostersSettings.java")).toList()) {
                Matcher m = LITERAL.matcher(Files.readString(file));
                while (m.find()) {
                    String key = m.group(1);
                    if (key.startsWith("skyblock.") || key.endsWith(".yml") || key.startsWith("boosters:")) {
                        continue;
                    }
                    if (!yml.contains(key) && !yml.contains(key + ".name")) {
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
        List<String> keys = new ArrayList<>(List.of("error", "loading", "busy", "frozen", "staff-mode", "switching", "no-permission",
                "invalid", "moved", "not-yours", "already-used", "item.personal.name", "item.personal.lore", "item.global.name",
                "item.global.lore", "settings.booster-bar.name", "settings.booster-bar.lore"));
        for (BoosterType t : BoosterType.values()) {
            keys.add("types." + t.id());
        }
        for (String prefix : List.of("menu.personal.running", "menu.personal.idle", "menu.global.running", "menu.global.idle",
                "menu.owned.personal", "menu.owned.global")) {
            keys.add(prefix + ".name");
            keys.add(prefix + ".lore");
        }
        assertEquals(List.of(), keys.stream().filter(k -> !yml.contains(k)).toList());
    }

    @Test
    void onlyBedrockSafeSymbols() throws Exception {
        String text = Files.readString(Path.of("src/main/resources/messages/boosters.yml"));
        String safe = "✦✧◆●▸▶★✔✘⚔⛏❤⌛»«│·•";
        Set<String> bad = new TreeSet<>();
        for (char ch : text.toCharArray()) {
            if (ch > 127 && safe.indexOf(ch) < 0) {
                bad.add(String.valueOf(ch));
            }
        }
        assertEquals(Set.of(), bad);
    }
}
