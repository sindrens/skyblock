package net.skybornmc.skyblock.progression;

import net.skybornmc.skyblock.common.progression.LevelSource;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every message key of the M5.2 code exists in messages/progression.yml; the menus' slots are valid. */
class LevelMessagesTest {

    private static final Pattern LITERAL = Pattern.compile("\"((?:level|level-menu|top-menu)(?:\\.[a-z0-9][a-z0-9-]*)+)\"");
    private static final List<String> FILES = List.of("LevelManager.java", "LevelMenus.java", "LevelMenu.java", "TopMenu.java");

    private static YamlConfiguration messages() throws Exception {
        try (var in = LevelMessagesTest.class.getClassLoader().getResourceAsStream("messages/progression.yml")) {
            assertNotNull(in, "messages/progression.yml missing");
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    private static boolean has(YamlConfiguration yml, String key) {
        return yml.contains(key) || yml.isConfigurationSection(key);
    }

    @Test
    void literalKeysExist() throws Exception {
        YamlConfiguration yml = messages();
        Path src = Path.of("src/main/java/net/skybornmc/skyblock/progression");
        Set<String> missing = new TreeSet<>();
        for (String name : FILES) {
            Matcher m = LITERAL.matcher(Files.readString(src.resolve(name)));
            while (m.find()) {
                String key = m.group(1);
                if (key.endsWith(".")) {
                    continue;
                }
                // prefixes completed in code (e.g. "level-menu.source." + id, "level-menu.recalculate." + state)
                if (!has(yml, key)) {
                    missing.add(key + " (" + name + ")");
                }
            }
        }
        assertEquals(Set.of(), missing);
    }

    @Test
    void dynamicKeysExist() throws Exception {
        YamlConfiguration yml = messages();
        Set<String> missing = new TreeSet<>();
        for (LevelSource s : LevelSource.values()) {
            String base = "level-menu.source." + s.id();
            for (String part : List.of(".name", ".how")) {
                if (!yml.contains(base + part)) {
                    missing.add(base + part);
                }
            }
        }
        for (String s : List.of("upgrades", "achievements", "collections")) {
            if (!yml.contains("level-menu.source." + s + ".soon")) {
                missing.add("level-menu.source." + s + ".soon");
            }
        }
        for (String s : List.of("pillars", "skills", "depths", "bank", "blocks")) {
            if (!yml.contains("level-menu.source." + s + ".detail")) {
                missing.add("level-menu.source." + s + ".detail");
            }
        }
        for (String state : List.of("lore", "lore-running", "lore-cooldown", "lore-locked")) {
            if (!yml.contains("level-menu.recalculate." + state)) {
                missing.add("level-menu.recalculate." + state);
            }
        }
        for (String tab : List.of("level", "value", "weekly", "pillars")) {
            for (String part : List.of(".name", ".lore")) {
                if (!yml.contains("top-menu.tab." + tab + part)) {
                    missing.add("top-menu.tab." + tab + part);
                }
            }
        }
        for (String tab : List.of("weekly", "pillars")) {
            if (!yml.contains("top-menu.tab." + tab + ".lore-active")) {
                missing.add("top-menu.tab." + tab + ".lore-active");
            }
        }
        // TopMenu builds these from a prefix: tab key + ".name"/".lore", weekly option key + "-name"/"-lore".
        for (String key : List.of("top-menu.weekly.points-name", "top-menu.weekly.points-lore", "top-menu.weekly.pillars-name",
                "top-menu.weekly.pillars-lore")) {
            if (!yml.contains(key)) {
                missing.add(key);
            }
        }
        for (net.skybornmc.skyblock.common.progression.Pillar pillar : net.skybornmc.skyblock.common.progression.Pillar.values()) {
            if (!yml.contains("pillar." + pillar.id() + ".name")) {
                missing.add("pillar." + pillar.id() + ".name");
            }
        }
        assertEquals(Set.of(), missing);
    }

    @Test
    void menuSlotsAreInsideTheFrameAndUnique() {
        Set<Integer> used = new HashSet<>();
        used.add(LevelMenu.SLOT_HEADER);
        used.add(LevelMenu.SLOT_RECALCULATE);
        assertTrue(inner(LevelMenu.SLOT_TASKS));
        assertTrue(used.add(LevelMenu.SLOT_TASKS), "challenge book slot used twice");
        for (int slot : LevelMenu.SOURCE_SLOTS) {
            assertTrue(inner(slot), "source slot " + slot);
            assertTrue(used.add(slot), "slot used twice: " + slot);
        }
        for (int slot : LevelMenu.PILLAR_SLOTS) {
            assertTrue(inner(slot), "pillar slot " + slot);
            assertTrue(used.add(slot), "slot used twice: " + slot);
        }
        assertTrue(inner(LevelMenu.SLOT_RECALCULATE));
        assertEquals(LevelMenu.SOURCES.size(), LevelMenu.SOURCE_SLOTS.size());
        assertEquals(LevelSource.values().length, LevelMenu.slots().size());
        Set<Integer> top = new HashSet<>(List.of(TopMenu.SLOT_LEVEL, TopMenu.SLOT_WEEKLY, TopMenu.SLOT_VALUE, TopMenu.SLOT_PILLARS,
                TopMenu.SLOT_BOARD));
        assertEquals(5, top.size(), "top row slots used twice");
        top.forEach(slot -> assertTrue(slot > 0 && slot < 8, "top row slot " + slot));
        assertEquals(28, TopMenu.PLACES.size());
        assertEquals(28, new HashSet<>(TopMenu.PLACES).size());
        TopMenu.PLACES.forEach(slot -> assertTrue(inner(slot), "place slot " + slot));
    }

    /** Rows 2-5, columns 2-8 of a 6-row menu. */
    private static boolean inner(int slot) {
        int row = slot / 9;
        int col = slot % 9;
        return row >= 1 && row <= 4 && col >= 1 && col <= 7;
    }
}
