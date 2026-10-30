package net.skybornmc.skyblock.stacking;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every message key the code uses exists in messages/stacking.yml (owner rule: all player-facing text in messages). */
class StackingMessagesTest {

    private static final Pattern LITERAL = Pattern.compile("\"([a-z][a-z-]*(?:\\.[a-z][a-z-]*)+)\"");

    private static YamlConfiguration messages() throws Exception {
        try (var in = StackingMessagesTest.class.getClassLoader().getResourceAsStream("messages/stacking.yml")) {
            assertNotNull(in, "messages/stacking.yml missing");
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    @Test
    void literalKeysExist() throws Exception {
        YamlConfiguration yml = messages();
        Path src = Path.of("src/main/java/net/skybornmc/skyblock/stacking");
        Set<String> missing = new TreeSet<>();
        try (Stream<Path> files = Files.list(src)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java") && !f.endsWith("StackingConfig.java")).toList()) {
                Matcher m = LITERAL.matcher(Files.readString(file));
                while (m.find()) {
                    String key = m.group(1);
                    if (key.startsWith("skyblock.") || key.endsWith(".yml") || key.startsWith("stacking-")) {
                        continue;
                    }
                    if (!yml.contains(key) && !yml.contains(key + ".name") && !yml.isConfigurationSection(key)) {
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
        for (EntityLimits.Kind kind : EntityLimits.Kind.values()) {
            assertTrue(yml.contains("limit.kind." + kind.key), "limit.kind." + kind.key);
        }
        for (String key : new String[]{"mob-name", "item-name", "menu.status.active", "menu.status.paused", "menu.status.busy",
                "spawner.item.name", "spawner.item.subtitle", "spawner.item.mob", "spawner.item.tier"}) {
            assertTrue(yml.contains(key), key);
        }
    }

    @Test
    void ownerNumbersAreTheDefaults() {
        StackingConfig c = new StackingConfig();
        assertEquals(1024, c.mobMaxStack);
        assertEquals(5.0, c.mobMergeRadius);
        assertEquals(8, c.mobMaxStacksPerChunk);
        assertEquals(3.0, c.itemMergeRadius);
        assertEquals(10, c.itemDespawnMinutes);
        assertEquals(64, c.hopperPerTransfer);
        assertEquals(8, c.spawnerStackStart);
        assertEquals(512, c.spawnerStackMax);
        assertEquals(16, c.activationRange);
        assertEquals(20, c.limitArmorStands);
        assertEquals(60, c.limitItemFrames);
        assertEquals(20, c.limitMinecarts);
        assertEquals(10, c.limitBoats);
        assertEquals(20, c.limitVillagers);
        assertEquals(30, c.limitDisplays);
        assertEquals(java.util.List.of("islands"), c.worlds);
        assertTrue(c.wholeStackDamage.containsAll(java.util.List.of("fall", "lava", "out_of_world")));
    }
}
