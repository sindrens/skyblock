package net.skybornmc.skyblock;

import net.skybornmc.skyblock.common.config.ModuleConfig;
import net.skybornmc.skyblock.common.config.Setting;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * REVIEW3 §6 "Dead-key test": dead settings piled up after quick changes (the 30-minute keys, {@code homes.*},
 * {@code buy.confirm-above}) and misled the owner. Two rules keep them from coming back:
 * <ol>
 *   <li>every {@link Setting} field of every typed config is read somewhere in the code (not only declared);</li>
 *   <li>a bundled or tracked yml with the name of a typed config ({@code modules/*&#47;src/main/resources/x.yml},
 *       {@code server/plugins/PhoenixSkyblock/x.yml}) holds no key outside that config's settings.</li>
 * </ol>
 * Documented exceptions are on the allow-lists below, each with its reason. Keys that are gone for good belong in
 * {@link ModuleConfig#removedKeys()} (deleted from live files on load).
 */
class DeadConfigKeysTest {

    private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();

    /**
     * Settings that are declared but read by nothing yet, with the reason. Keep this list short: a dead key misleads the
     * owner, so either use it, remove it (and add it to removedKeys), or explain here why it waits.
     */
    private static final Map<String, String> UNREAD_ALLOWED = Map.of(
            "RewardsSettings.saveEnabled", "M10-6 'Save streak' button waits for the store (Phase 11); the rewards lane reads it then");

    /**
     * Keys in tracked/bundled copies of typed configs that are not settings, per file (prefix match), with the reason.
     */
    private static final Map<String, String> YML_ALLOWED = Map.of(
            "config-version", "written by ConfigManager in every typed config",
            "motd.yml:brand", "MotdModule reads the brand line straight from the yml (catalog part of the file)",
            "motd.yml:motds", "MotdModule reads the MOTD catalog (motds.<id>.line1/line2) straight from the yml",
            "worlds.yml:worlds", "WorldsModule reads one section per world (create, biome, spawn, border, gamerules)",
            "worlds.yml:defaults", "WorldsModule reads the default gamerules for every world");

    // ------------------------------------------------------------------ rule 1: every setting is read

    private static final Pattern SETTING_FIELD = Pattern.compile(
            "@Setting\\s*\\((?:[^()]|\\([^()]*\\))*\\)\\s*(?:@\\w+\\s*)*(?:public|protected|private)?\\s*(?:final\\s+)?"
                    + "[\\w.<>?,\\s\\[\\]]+?\\s+(\\w+)\\s*=", Pattern.DOTALL);

    @Test
    void everySettingIsReadSomewhere() throws IOException {
        List<Path> sources = mainSources();
        StringBuilder all = new StringBuilder();
        Map<Path, String> texts = new LinkedHashMap<>();
        for (Path file : sources) {
            String text = stripComments(Files.readString(file));
            texts.put(file, text);
            all.append(text).append('\n');
        }
        String everything = all.toString();
        List<String> dead = new ArrayList<>();
        int[] checked = {0};
        texts.forEach((file, text) -> {
            if (!text.contains("@Setting")) {
                return;
            }
            String owner = file.getFileName().toString().replace(".java", "");
            Matcher m = SETTING_FIELD.matcher(text);
            while (m.find()) {
                String field = m.group(1);
                checked[0]++;
                int uses = count(everything, field);
                if (uses <= 1 && !UNREAD_ALLOWED.containsKey(owner + "." + field)) {
                    dead.add(ROOT.relativize(file) + ": " + field);
                }
            }
        });
        assertTrue(checked[0] > 500, "the @Setting scan found only " + checked[0] + " fields; the pattern is broken");
        assertTrue(dead.isEmpty(), "Settings that no code reads (use them, delete them and list the key in removedKeys(), or "
                + "allow-list them with a reason in DeadConfigKeysTest):\n" + String.join("\n", dead));
    }

    private static int count(String text, String word) {
        Matcher m = Pattern.compile("(?<![\\w$])" + Pattern.quote(word) + "(?![\\w$])").matcher(text);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }

    /** Removes // and block comments (keeps strings intact well enough for counting identifiers). */
    static String stripComments(String java) {
        StringBuilder out = new StringBuilder(java.length());
        boolean block = false;
        boolean line = false;
        boolean string = false;
        char quote = 0;
        for (int i = 0; i < java.length(); i++) {
            char c = java.charAt(i);
            char next = i + 1 < java.length() ? java.charAt(i + 1) : 0;
            if (block) {
                if (c == '*' && next == '/') {
                    block = false;
                    i++;
                }
                continue;
            }
            if (line) {
                if (c == '\n') {
                    line = false;
                    out.append(c);
                }
                continue;
            }
            if (string) {
                out.append(c);
                if (c == '\\') {
                    if (next != 0) {
                        out.append(next);
                        i++;
                    }
                } else if (c == quote) {
                    string = false;
                }
                continue;
            }
            if (c == '/' && next == '*') {
                block = true;
                i++;
            } else if (c == '/' && next == '/') {
                line = true;
                i++;
            } else {
                if (c == '"' || c == '\'') {
                    string = true;
                    quote = c;
                }
                out.append(c);
            }
        }
        return out.toString();
    }

    private static List<Path> mainSources() throws IOException {
        List<Path> out = new ArrayList<>();
        for (Path base : List.of(ROOT.resolve("common"), ROOT.resolve("plugin"), ROOT.resolve("modules"))) {
            try (Stream<Path> files = Files.walk(base)) {
                files.filter(p -> p.toString().endsWith(".java") && p.toString().contains("/src/main/java/")
                        && !p.toString().contains("/build/")).forEach(out::add);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ rule 2: tracked/bundled copies hold only settings

    @Test
    void bundledAndTrackedConfigCopiesHoldOnlySettings() throws Exception {
        Map<String, Set<String>> settingsByFile = typedConfigs();
        assertTrue(settingsByFile.size() > 30, "found only " + settingsByFile.size() + " typed configs");
        List<String> problems = new ArrayList<>();
        for (Path yml : candidateFiles()) {
            String name = yml.getFileName().toString();
            Set<String> paths = settingsByFile.get(name);
            if (paths == null) {
                continue;
            }
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(yml.toFile());
            for (String key : yaml.getKeys(true)) {
                if (yaml.isConfigurationSection(key) || paths.contains(key) || allowed(name, key)) {
                    continue;
                }
                problems.add(ROOT.relativize(yml) + ": " + key);
            }
        }
        assertTrue(problems.isEmpty(), "Keys in a tracked/bundled copy of a typed config that no setting reads (remove them, "
                + "or allow-list the section with a reason in DeadConfigKeysTest):\n" + String.join("\n", problems));
    }

    private static boolean allowed(String file, String key) {
        for (Map.Entry<String, String> e : YML_ALLOWED.entrySet()) {
            String rule = e.getKey();
            int colon = rule.indexOf(':');
            String ruleFile = colon < 0 ? null : rule.substring(0, colon);
            String prefix = colon < 0 ? rule : rule.substring(colon + 1);
            if ((ruleFile == null || ruleFile.equals(file)) && (key.equals(prefix) || key.startsWith(prefix + "."))) {
                return true;
            }
        }
        return false;
    }

    private static List<Path> candidateFiles() throws IOException {
        List<Path> out = new ArrayList<>();
        try (Stream<Path> files = Files.walk(ROOT.resolve("modules"), 5)) {
            files.filter(p -> p.toString().endsWith(".yml") && p.getParent().endsWith(Path.of("src", "main", "resources")))
                    .forEach(out::add);
        }
        for (Path dir : List.of(ROOT.resolve("plugin/src/main/resources"), ROOT.resolve("common/src/main/resources"),
                ROOT.resolve("server/plugins/PhoenixSkyblock"))) {
            if (Files.isDirectory(dir)) {
                try (Stream<Path> files = Files.list(dir)) {
                    files.filter(p -> p.toString().endsWith(".yml")).forEach(out::add);
                }
            }
        }
        return out;
    }

    /** File name → setting paths of every typed config class found in the main sources. */
    static Map<String, Set<String>> typedConfigs() throws Exception {
        Map<String, Set<String>> out = new HashMap<>();
        for (ModuleConfig config : instances()) {
            Set<String> paths = out.computeIfAbsent(config.fileName(), k -> new LinkedHashSet<>());
            paths.addAll(settingPaths(config));
        }
        return out;
    }

    /** One instance of every concrete ModuleConfig class in the main sources (no-arg constructor). */
    static List<ModuleConfig> instances() throws Exception {
        List<ModuleConfig> out = new ArrayList<>();
        for (Path file : mainSources()) {
            String text = Files.readString(file);
            if (!text.contains("extends ModuleConfig") || text.contains("abstract class")) {
                continue;
            }
            String path = file.toString();
            String fqn = path.substring(path.indexOf("/src/main/java/") + "/src/main/java/".length(), path.length() - 5).replace('/', '.');
            Class<?> type = Class.forName(fqn);
            if (!ModuleConfig.class.isAssignableFrom(type) || Modifier.isAbstract(type.getModifiers())) {
                continue;
            }
            var ctor = type.getDeclaredConstructor();
            ctor.setAccessible(true);
            out.add((ModuleConfig) ctor.newInstance());
        }
        return out;
    }

    /** The setting paths of a config (same rule as ConfigManager#pathOf). */
    static List<String> settingPaths(ModuleConfig config) {
        List<String> out = new ArrayList<>();
        for (Class<?> c = config.getClass(); c != null && c != ModuleConfig.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                Setting s = f.getAnnotation(Setting.class);
                if (s != null && !Modifier.isStatic(f.getModifiers())) {
                    out.add(pathOf(f, s));
                }
            }
        }
        return out;
    }

    static String pathOf(Field field, Setting setting) {
        if (!setting.path().isBlank()) {
            return setting.path();
        }
        return field.getName().replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase(Locale.ROOT);
    }

    /** The value of a setting by path (decision ledger test). */
    static Object settingValue(ModuleConfig config, String path) throws IllegalAccessException {
        for (Class<?> c = config.getClass(); c != null && c != ModuleConfig.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                Setting s = f.getAnnotation(Setting.class);
                if (s != null && !Modifier.isStatic(f.getModifiers()) && pathOf(f, s).equals(path)) {
                    f.setAccessible(true);
                    return f.get(config);
                }
            }
        }
        throw new IllegalArgumentException(config.fileName() + " has no setting " + path);
    }
}
