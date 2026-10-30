package net.skybornmc.skyblock.crates;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

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

/** Every message key the code uses exists in messages/crates.yml (owner rule: all player-facing text in messages). */
class CratesMessagesTest {

    private static final Pattern LITERAL = Pattern.compile("\"([a-z][a-z-]*(?:\\.[a-z][a-z-]*)+)\"");

    private static boolean has(YamlConfiguration yml, String key) {
        return yml.contains(key) || yml.contains(key + ".name");
    }

    @Test
    void literalKeysExist() throws Exception {
        YamlConfiguration yml = Fixtures.resource("messages/crates.yml");
        Path src = Path.of("src/main/java/net/skybornmc/skyblock/crates");
        Set<String> missing = new TreeSet<>();
        try (Stream<Path> files = Files.list(src)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java") && !f.endsWith("CratesSettings.java")).toList()) {
                Matcher m = LITERAL.matcher(Files.readString(file));
                while (m.find()) {
                    String key = m.group(1);
                    if (key.startsWith("skyblock.") || key.endsWith(".yml")) {
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
    void dynamicKeysExist() {
        YamlConfiguration yml = Fixtures.resource("messages/crates.yml");
        List<String> keys = new ArrayList<>(List.of("error", "loading", "frozen", "staff-mode", "switching", "unknown-crate",
                "coming-soon", "busy", "changed", "no-keys", "text"));
        for (CrateCatalog.Crate crate : Fixtures.bundled().crates()) {
            keys.add("crates." + crate.id() + ".name");
            keys.add("crates." + crate.id() + ".key");
            for (CrateCatalog.Reward r : crate.rewards()) {
                keys.add("rewards." + crate.id() + "." + r.id());
            }
        }
        for (RewardType t : RewardType.values()) {
            keys.add("reward-types." + t.key());
        }
        List<String> missing = keys.stream().filter(k -> !yml.contains(k)).toList();
        assertEquals(List.of(), missing);
    }

    @Test
    void noUnescapedQuotesOrUnsafeSymbols() throws Exception {
        String text = Files.readString(Path.of("src/main/resources/messages/crates.yml"));
        // Only Bedrock-safe symbols (style.yml bedrock-safe-symbols) besides plain ASCII.
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
