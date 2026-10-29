package net.skybornmc.skyblock.staff;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every message key the staff module uses must exist in the bundled {@code messages/staff.yml}, or players see
 * "[missing: staff:…]" (the staff-mode vanish item did). Covers every dotted string literal in the module's code plus the
 * keys built at runtime (kit items in both vanish states, report tabs/entries, outcomes).
 */
class StaffMessageKeysTest {

    private static final Path ROOT = Path.of("..", "..").toAbsolutePath().normalize();
    private static final Path SOURCES = ROOT.resolve("modules/staff/src/main/java/net/skybornmc/skyblock/staff");
    private static final Pattern LITERAL = Pattern.compile("\"([a-z][a-z0-9-]*(?:\\.[a-z0-9-]+)+)\"");
    private static YamlConfiguration yaml;

    @BeforeAll
    static void load() throws Exception {
        yaml = new YamlConfiguration();
        yaml.load(ROOT.resolve("plugin/src/main/resources/messages/staff.yml").toFile());
    }

    /** A plain text, a list, or an item/title section ({@code .name} or {@code .title} inside). */
    private static boolean present(String key) {
        if (yaml.isConfigurationSection(key)) {
            return yaml.isString(key + ".name") || yaml.isString(key + ".title");
        }
        return yaml.isString(key) || yaml.isList(key);
    }

    private static Set<String> literalKeys() throws IOException {
        Set<String> keys = new TreeSet<>();
        try (Stream<Path> files = Files.list(SOURCES)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                for (String line : Files.readAllLines(file)) {
                    if (line.contains("@Setting(") || line.contains("getLogger()")) {
                        continue; // config paths, log text
                    }
                    Matcher m = LITERAL.matcher(line);
                    while (m.find()) {
                        String key = m.group(1);
                        if (!key.startsWith("skyblock.") && !key.endsWith(".yml") && !key.endsWith("-")) {
                            keys.add(key);
                        }
                    }
                }
            }
        }
        return keys;
    }

    @Test
    void everyLiteralKeyExists() throws IOException {
        Set<String> keys = literalKeys();
        assertFalse(keys.isEmpty(), "no keys found: wrong source path " + SOURCES);
        assertTrue(keys.contains("freeze.no-commands") && keys.contains("menu.inspect.freeze"), "scanner missed known keys: " + keys);
        List<String> missing = keys.stream().filter(k -> !present(k)).toList();
        assertTrue(missing.isEmpty(), "Missing in messages/staff.yml: " + missing);
    }

    @Test
    void everyKitItemHasNameAndLoreInEveryState() {
        List<String> missing = new ArrayList<>();
        for (StaffMode.Tool tool : StaffMode.Tool.values()) {
            for (boolean vanished : new boolean[]{true, false}) {
                String key = StaffMode.toolKey(tool, vanished);
                if (!yaml.isString(key + ".name")) {
                    missing.add(key + ".name");
                }
                if (!yaml.isList(key + ".lore") && !yaml.isString(key + ".lore")) {
                    missing.add(key + ".lore");
                }
            }
        }
        assertTrue(missing.isEmpty(), "Kit item texts missing: " + missing);
        // The vanish keys are quoted "on"/"off": unquoted, YAML reads them as true/false.
        assertTrue(yaml.isString("tool.vanish.on.name") && yaml.isString("tool.vanish.off.name"));
        assertFalse(yaml.contains("tool.vanish.true") || yaml.contains("tool.vanish.false"));
    }

    @Test
    void runtimeBuiltKeysExist() {
        List<String> missing = new ArrayList<>();
        for (Report.Status status : Report.Status.values()) {
            for (String key : List.of("menu.reports.tab." + status.db(), "menu.reports.tab." + status.db() + "-selected",
                    "menu.reports.entry." + status.db())) {
                if (!present(key)) {
                    missing.add(key);
                }
            }
        }
        for (Report.Outcome outcome : Report.Outcome.values()) {
            for (String key : List.of("outcome." + outcome.key(), "report.closed-" + outcome.key())) {
                if (!present(key)) {
                    missing.add(key);
                }
            }
        }
        assertTrue(missing.isEmpty(), "Missing in messages/staff.yml: " + missing);
    }

    @Test
    void trackedServerCopyHasTheSameKeys() throws Exception {
        YamlConfiguration server = new YamlConfiguration();
        server.load(ROOT.resolve("server/plugins/PhoenixSkyblock/messages/staff.yml").toFile());
        Set<String> expected = new TreeSet<>(yaml.getKeys(true));
        expected.removeAll(server.getKeys(true));
        assertTrue(expected.isEmpty(), "Run tools/sync_server_configs.py; missing in the tracked copy: " + expected);
    }
}
