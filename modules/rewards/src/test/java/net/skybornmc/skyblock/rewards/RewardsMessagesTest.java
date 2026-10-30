package net.skybornmc.skyblock.rewards;

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

/** Every message key the code uses exists in messages/rewards.yml (owner rule: all player-facing text in messages). */
class RewardsMessagesTest {

    private static final Pattern LITERAL = Pattern.compile("\"([a-z][a-z-]*(?:\\.[a-z][a-z-]*)+)\"");

    private static YamlConfiguration yml() {
        try (var in = RewardsMessagesTest.class.getClassLoader().getResourceAsStream("messages/rewards.yml")) {
            assertNotNull(in, "messages/rewards.yml missing");
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
        Path src = Path.of("src/main/java/net/skybornmc/skyblock/rewards");
        Set<String> missing = new TreeSet<>();
        try (Stream<Path> files = Files.list(src)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java") && !f.endsWith("RewardsSettings.java")).toList()) {
                Matcher m = LITERAL.matcher(Files.readString(file));
                while (m.find()) {
                    String key = m.group(1);
                    if (key.startsWith("skyblock.") || key.endsWith(".yml") || key.startsWith("daily.yml")) {
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
        List<String> keys = new ArrayList<>(List.of("error", "busy", "frozen", "staff-mode", "switching", "no-permission", "box-full",
                "daily.claimed", "daily.already", "daily.changed", "daily.reminder", "daily.disabled", "daily.streak-lost",
                "daily.streak-gems", "playtime.reward", "reward.line", "reward.fallback-line", "reward.for-now", "reward.title",
                "reward.pass-xp", "reward.book-named", "settings.daily-reminder.name"));
        for (PlaytimeService.Status s : PlaytimeService.Status.values()) {
            keys.add("menu.playtime.status." + s.name().toLowerCase(java.util.Locale.ROOT));
        }
        for (RewardSpec.Kind k : RewardSpec.Kind.values()) {
            keys.add(k.currency() ? "reward-names." + k.key() : "reward." + k.key());
        }
        for (BoosterType t : BoosterType.values()) {
            keys.add("booster-types." + t.id());
        }
        for (String crate : List.of("ember", "cinder", "blaze", "inferno", "phoenix", "eclipse")) {
            keys.add("crates." + crate);
        }
        RewardsSettings s = new RewardsSettings();
        for (String line : s.streakTitles) {
            keys.add("titles." + line.substring(line.indexOf(':') + 1).trim());
        }
        for (String line : s.playtimeTitles) {
            keys.add("titles." + line.substring(line.indexOf(':') + 1).trim());
        }
        List<String> missing = keys.stream().filter(k -> !yml.contains(k)).toList();
        assertEquals(List.of(), missing);
    }

    @Test
    void onlyBedrockSafeSymbols() throws Exception {
        String text = Files.readString(Path.of("src/main/resources/messages/rewards.yml"));
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
