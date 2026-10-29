package net.skybornmc.skyblock.islands;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Every message key the M2.7 code uses exists in messages/islands.yml (owner rule: all player-facing text in messages). */
class IslandMessagesTest {

    private static final Pattern LITERAL = Pattern.compile("\"([a-z][a-z-]*(?:\\.[a-z][a-z-]*)+)\"");
    /** Files written for M2.7 (older files are covered by their own reviews). */
    private static final List<String> FILES = List.of("IslandLifecycle.java", "IslandAdmin.java", "SlotsMenu.java", "IslandClearer.java");
    /** Keys of other files (commands.yml) and strings that are not message keys. */
    private static final Set<String> FOREIGN = Set.of("invalid-number", "player-unknown");

    private static YamlConfiguration messages() throws Exception {
        try (var in = IslandMessagesTest.class.getClassLoader().getResourceAsStream("messages/islands.yml")) {
            assertNotNull(in, "messages/islands.yml missing");
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    private static boolean has(YamlConfiguration yml, String key) {
        return yml.contains(key) || yml.contains(key + ".name");
    }

    @Test
    void literalKeysExist() throws Exception {
        YamlConfiguration yml = messages();
        Path src = Path.of("src/main/java/net/skybornmc/skyblock/islands");
        Set<String> missing = new TreeSet<>();
        for (String name : FILES) {
            Matcher m = LITERAL.matcher(Files.readString(src.resolve(name)));
            while (m.find()) {
                String key = m.group(1);
                if (key.startsWith("skyblock.") || FOREIGN.contains(key) || key.startsWith("isadmin ")) {
                    continue;
                }
                if (!has(yml, key) && !yml.isConfigurationSection(key)) {
                    missing.add(key + " (" + name + ")");
                }
            }
        }
        assertEquals(Set.of(), missing);
    }

    @Test
    void dynamicKeysExist() throws Exception {
        YamlConfiguration yml = messages();
        List<String> keys = new ArrayList<>();
        for (SlotState state : SlotState.values()) {
            keys.add("admin.state." + state.key());
        }
        // confirm screens built from a prefix in IslandAdmin.confirm
        for (String prefix : List.of("admin.delete", "admin.reset", "admin.kick", "admin.setowner")) {
            keys.add(prefix + ".crumb");
            keys.add(prefix + ".shown.name");
            keys.add(prefix + ".details");
            keys.add(prefix + ".confirm");
        }
        for (String role : List.of("leader", "mod", "member", "coop")) {
            keys.add("role." + role);
        }
        keys.add("disband.confirm-word");
        Set<String> missing = new TreeSet<>();
        for (String key : keys) {
            if (!yml.contains(key)) {
                missing.add(key);
            }
        }
        assertEquals(Set.of(), missing);
    }
}
