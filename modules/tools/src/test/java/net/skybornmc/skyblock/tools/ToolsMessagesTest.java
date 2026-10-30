package net.skybornmc.skyblock.tools;

import net.skybornmc.skyblock.common.tools.ToolKind;
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

/** Every message key the code uses exists in messages/tools.yml, and every catalog entry has a name (owner rule). */
class ToolsMessagesTest {

    private static final Pattern LITERAL = Pattern.compile("\"([a-z][a-z-]*(?:\\.[a-z][a-z-]*)+)\"");
    /** Config paths and file names in the code that are not message keys. */
    private static final Set<String> NOT_MESSAGES = Set.of("tools.yml", "tools-defaults.yml", "drop.chance", "drop.min-tier", "drop.wing");

    static YamlConfiguration messages() throws Exception {
        try (var in = ToolsMessagesTest.class.getClassLoader().getResourceAsStream("messages/tools.yml")) {
            assertNotNull(in, "messages/tools.yml missing");
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    private static boolean has(YamlConfiguration yml, String key) {
        return yml.contains(key) || yml.contains(key + ".name");
    }

    @Test
    void literalKeysExist() throws Exception {
        YamlConfiguration yml = messages();
        Path src = Path.of("src/main/java/net/skybornmc/skyblock/tools");
        Set<String> missing = new TreeSet<>();
        try (Stream<Path> files = Files.list(src)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java") && !f.endsWith("ToolsConfig.java")).toList()) {
                Matcher m = LITERAL.matcher(Files.readString(file));
                while (m.find()) {
                    String key = m.group(1);
                    if (key.startsWith("skyblock.") || NOT_MESSAGES.contains(key) || key.startsWith("abilities.")) {
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
    void dynamicKeysExist() throws Exception {
        YamlConfiguration yml = messages();
        List<String> keys = new ArrayList<>(List.of("error", "inventory-full"));
        for (ToolKind kind : ToolKind.values()) {
            keys.add("kind." + kind.id());
            keys.add("kind-plural." + kind.id());
            keys.add("skill." + kind.skill().id());
            keys.add("item.description." + kind.id());
            keys.add("tool-menu.level.how." + kind.id());
        }
        for (Forge.State state : Forge.State.values()) {
            String k = ForgeMenu.key(state);
            keys.add("forge.state." + k);
            keys.add("forge.tier.mark." + k);
            keys.add("forge.tier.state." + k);
        }
        for (ToolProgress.PrestigeCheck check : ToolProgress.PrestigeCheck.values()) {
            String k = check.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
            keys.add("tool-menu.prestige." + k);
            if (check != ToolProgress.PrestigeCheck.OK) {
                keys.add("prestige." + k);
            }
        }
        for (String stat : List.of(ToolData.STAT_BLOCKS, ToolData.STAT_LOGS, ToolData.STAT_KILLS)) {
            keys.add("item.history." + stat);
        }
        for (String state : List.of("applied", "owned", "locked")) {
            keys.add("skins.menu.state." + state);
        }
        for (String wing : List.of("mine", "forest", "combat")) {
            keys.add("wing." + wing);
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
    void everyCatalogEntryHasAName() throws Exception {
        YamlConfiguration yml = messages();
        ToolCatalog catalog = ToolCatalogTest.catalog();
        Set<String> missing = new TreeSet<>();
        for (ToolKind kind : catalog.kinds()) {
            for (ToolCatalog.TierDef tier : catalog.tiers(kind)) {
                if (!yml.contains("tier." + kind.id() + "." + tier.id())) {
                    missing.add("tier." + kind.id() + "." + tier.id());
                }
            }
        }
        for (String id : catalog.abilities().keySet()) {
            for (String k : List.of("ability." + id + ".name", "ability." + id + ".description")) {
                if (!yml.contains(k)) {
                    missing.add(k);
                }
            }
        }
        for (String id : catalog.blueprints().keySet()) {
            for (String k : List.of("blueprint." + id + ".name", "blueprint." + id + ".tool-name")) {
                if (!yml.contains(k)) {
                    missing.add(k);
                }
            }
        }
        for (String id : catalog.skins().keySet()) {
            for (String k : List.of("skin." + id + ".name", "skin." + id + ".description")) {
                if (!yml.contains(k)) {
                    missing.add(k);
                }
            }
        }
        assertEquals(Set.of(), missing);
    }
}
