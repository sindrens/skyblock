package net.skybornmc.skyblock.pass;

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

/** Every message key the code uses exists in messages/pass.yml (owner rule: all player-facing text in messages). */
class PassMessagesTest {

    private static final Pattern LITERAL = Pattern.compile("\"([a-z][a-z-]*(?:\\.[a-z][a-z-]*)+)\"");

    private static YamlConfiguration yml() {
        try (var in = PassMessagesTest.class.getClassLoader().getResourceAsStream("messages/pass.yml")) {
            assertNotNull(in, "messages/pass.yml missing");
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean has(YamlConfiguration yml, String key) {
        return yml.contains(key) || yml.contains(key + ".name") || yml.isConfigurationSection(key);
    }

    private static Set<String> settingPaths() {
        Set<String> out = new TreeSet<>();
        for (java.lang.reflect.Field f : PassSettings.class.getFields()) {
            net.skybornmc.skyblock.common.config.Setting s = f.getAnnotation(net.skybornmc.skyblock.common.config.Setting.class);
            if (s != null) {
                out.add(s.path());
            }
        }
        return out;
    }

    @Test
    void literalKeysExist() throws Exception {
        YamlConfiguration yml = yml();
        Path src = Path.of("src/main/java/net/skybornmc/skyblock/pass");
        Set<String> missing = new TreeSet<>();
        try (Stream<Path> files = Files.list(src)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java") && !f.endsWith("PassSettings.java")).toList()) {
                Matcher m = LITERAL.matcher(Files.readString(file));
                while (m.find()) {
                    String key = m.group(1);
                    if (key.startsWith("skyblock.") || key.endsWith(".yml") || key.startsWith("frame.") || key.startsWith("title.")
                            || key.startsWith("menu.tier.name.") || key.startsWith("menu.tier.overflow-name.")
                            || key.startsWith("menu.tier.state.") || key.startsWith("menu.level.")) {
                        continue; // framework keys (menus module) and prefixes completed in code (checked below)
                    }
                    if (settingPaths().contains(key)) {
                        continue; // pass.yml paths in warnings
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
        for (String state : List.of("claim", "claimed", "locked", "premium", "unavailable")) {
            keys.add("menu.tier.name." + state);
            keys.add("menu.tier.overflow-name." + state);
            keys.add("menu.tier.state." + state);
        }
        for (String level : List.of("reached", "current", "later")) {
            keys.add("menu.level." + level + ".name");
            keys.add("menu.level.overflow-" + level + ".name");
        }
        PassSettings s = new PassSettings();
        PassTable t = PassTable.parse(s.freeEveryLevel, s.freeMilestones, s.freeOverflow, s.premiumMilestones, s.maxLevel, p -> { });
        for (PassTable.Reward r : t.cosmetics()) {
            if (r.kind() == PassTable.Kind.TITLE) {
                keys.add("titles." + r.ref());
            }
        }
        for (int level = 1; level <= 101; level++) {
            for (PassTable.Reward r : t.free(level, 100)) {
                if (r.kind() == PassTable.Kind.KEY) {
                    keys.add("crates." + r.ref());
                }
                if (r.kind() == PassTable.Kind.BOOSTER) {
                    keys.add("boosters." + r.ref().split(":")[0]);
                }
            }
        }
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
