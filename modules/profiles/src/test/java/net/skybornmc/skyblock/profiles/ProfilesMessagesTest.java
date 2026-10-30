package net.skybornmc.skyblock.profiles;

import net.skybornmc.skyblock.common.profile.ProfileNames;
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

/** Every message key the code uses exists in messages/profiles.yml (owner rule: all player-facing text in messages). */
class ProfilesMessagesTest {

    private static final Pattern LITERAL = Pattern.compile("\"([a-z][a-z-]*(?:\\.[a-z][a-z-]*)+)\"");
    /** Keys of the shared menus file. */
    private static final Set<String> FOREIGN = Set.of("frame.back");

    private static YamlConfiguration messages() throws Exception {
        try (var in = ProfilesMessagesTest.class.getClassLoader().getResourceAsStream("messages/profiles.yml")) {
            assertNotNull(in, "messages/profiles.yml missing");
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    private static boolean has(YamlConfiguration yml, String key) {
        return yml.contains(key) || yml.contains(key + ".name");
    }

    @Test
    void literalKeysExist() throws Exception {
        YamlConfiguration yml = messages();
        Path src = Path.of("src/main/java/net/skybornmc/skyblock/profiles");
        Set<String> missing = new TreeSet<>();
        try (Stream<Path> files = Files.list(src)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java") && !f.endsWith("ProfilesConfig.java") && !f.endsWith("StateCodec.java")).toList()) {
                Matcher m = LITERAL.matcher(Files.readString(file));
                while (m.find()) {
                    String key = m.group(1);
                    if (key.startsWith("skyblock.") || key.endsWith(".yml") || FOREIGN.contains(key) || key.endsWith("-")) {
                        continue;
                    }
                    if (!has(yml, key) && !yml.isConfigurationSection(key)) {
                        missing.add(key + " (" + file.getFileName() + ")");
                    }
                }
            }
        }
        assertEquals(Set.of(), missing);
    }

    @Test
    void dynamicKeysExist() throws Exception {
        YamlConfiguration yml = messages();
        List<String> keys = new ArrayList<>(List.of("menu.rank-2", "menu.rank-3", "slots.already", "slots.not-for-sale", "slots.invalid",
                "switch.changed", "create.slot-used", "create.invalid-slot", "create.changed", "rename.gone", "icon.gone",
                "delete.active", "delete.gone", "delete.changed"));
        for (SwitchRules.Problem p : SwitchRules.Problem.values()) {
            keys.add("switch." + p.key);
        }
        for (ProfileNames.Problem p : ProfileNames.Problem.values()) {
            keys.add("rename." + p.key);
        }
        List<String> missing = keys.stream().filter(k -> !yml.contains(k)).toList();
        assertEquals(List.of(), missing);
    }
}
