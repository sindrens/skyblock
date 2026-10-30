package net.skybornmc.skyblock.progression;

import net.skybornmc.skyblock.common.progression.TaskType;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Every message key of the M5.3 code exists in messages/progression.yml. */
class TaskMessagesTest {

    private static final Pattern LITERAL = Pattern.compile("\"((?:tasks|level-menu\\.tasks)(?:\\.[a-z0-9][a-z0-9-]*)+)\"");
    private static final List<String> FILES = List.of("TaskManager.java", "TaskMenus.java", "TaskBookMenu.java",
            "TaskTierMenu.java", "TaskDeliverMenu.java", "LevelMenu.java");

    private static YamlConfiguration messages() throws Exception {
        try (var in = TaskMessagesTest.class.getClassLoader().getResourceAsStream("messages/progression.yml")) {
            assertNotNull(in);
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
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
                if (!yml.contains(key)) {
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
        for (TaskType type : TaskType.values()) {
            for (String key : List.of("tasks.name." + type.id(), "tasks.item.type." + type.id())) {
                if (!yml.contains(key)) {
                    missing.add(key);
                }
            }
            if (type.counted() && !type.anyOnly() && type != TaskType.DELIVER && !yml.contains("tasks.name-any." + type.id())) {
                missing.add("tasks.name-any." + type.id());
            }
        }
        for (String key : List.of("settings.task-notices.name", "settings.task-notices.lore", "tasks.tier.empty.name",
                "tasks.tier.empty.lore")) {
            if (!yml.contains(key)) {
                missing.add(key);
            }
        }
        assertEquals(Set.of(), missing);
    }

    @Test
    void deliverMenuSlotsAreInside() {
        for (int slot : new int[]{TaskDeliverMenu.SLOT_INVENTORY, TaskDeliverMenu.SLOT_STORAGE}) {
            int row = slot / 9;
            int col = slot % 9;
            assertEquals(true, row >= 1 && row <= 4 && col >= 1 && col <= 7, "slot " + slot);
        }
    }
}
