package net.skybornmc.skyblock;

import net.skybornmc.skyblock.common.config.ModuleConfig;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * REVIEW3 §6 decision ledger: every config-backed owner answer in {@code docs/decisions.yml} must match the code
 * default, so an answer that never reached the code (641, N2-1) fails the build. The ledger is also bundled in the jar
 * for the startup migration.
 */
class DecisionLedgerTest {

    private static final Path LEDGER = Path.of("..").toAbsolutePath().normalize().resolve("docs/decisions.yml");

    @Test
    void codeDefaultsMatchTheOwnersAnswers() throws Exception {
        YamlConfiguration ledger = YamlConfiguration.loadConfiguration(LEDGER.toFile());
        List<Map<?, ?>> entries = ledger.getMapList("decisions");
        assertTrue(entries.size() >= 4, "the ledger starts with 641, R3-15, N2-1 and R3-22");
        Map<String, ModuleConfig> configs = new java.util.HashMap<>();
        for (ModuleConfig c : DeadConfigKeysTest.instances()) {
            configs.put(c.fileName(), c);
        }
        Set<String> ids = new HashSet<>();
        List<String> problems = new ArrayList<>();
        for (Map<?, ?> e : entries) {
            String id = String.valueOf(e.get("id"));
            assertTrue(ids.add(id), "duplicate decision id " + id);
            assertNotNull(e.get("answer"), id + ": answer missing");
            if (Boolean.FALSE.equals(e.get("check"))) {
                continue;
            }
            String file = String.valueOf(e.get("file"));
            String path = String.valueOf(e.get("path"));
            ModuleConfig config = configs.get(file);
            if (config == null) {
                problems.add(id + ": no typed config " + file);
                continue;
            }
            Object actual;
            try {
                actual = DeadConfigKeysTest.settingValue(config, path);
            } catch (IllegalArgumentException ex) {
                problems.add(id + ": " + ex.getMessage());
                continue;
            }
            if (e.get("item") != null) {
                String item = String.valueOf(e.get("item"));
                if (!(actual instanceof List<?> list) || !list.contains(item)) {
                    problems.add(id + ": " + file + " " + path + " default " + actual + " lacks " + item);
                } else if (e.get("migrate-from") != null && list.contains(String.valueOf(e.get("migrate-from")))) {
                    problems.add(id + ": " + file + " " + path + " default still has " + e.get("migrate-from"));
                }
            } else if (!ModuleConfig.ValueChange.of(e.get("value"), e.get("value")).matches(actual)) {
                problems.add(id + ": " + file + " " + path + " default is " + actual + ", the owner decided " + e.get("value"));
            }
        }
        assertTrue(problems.isEmpty(), "Owner answers that did not reach the code defaults (docs/decisions.yml):\n"
                + String.join("\n", problems));
    }

    @Test
    void migratingEntriesHaveAnOldDefaultThatDiffers() {
        YamlConfiguration ledger = YamlConfiguration.loadConfiguration(LEDGER.toFile());
        for (Map<?, ?> e : ledger.getMapList("decisions")) {
            Object from = e.get("migrate-from");
            if (from == null || Boolean.FALSE.equals(e.get("check"))) {
                continue;
            }
            Object to = e.get("item") != null ? e.get("item") : e.get("value");
            assertNotNull(to, e.get("id") + ": value or item needed");
            assertFalse(ModuleConfig.ValueChange.of(from, from).matches(to), e.get("id") + ": migrate-from equals the new value");
        }
    }

    @Test
    void ledgerIsBundledInTheJar() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("decisions.yml")) {
            assertNotNull(in, "plugin/build.gradle.kts must copy docs/decisions.yml into the jar");
            assertEquals(Files.readString(LEDGER), new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
