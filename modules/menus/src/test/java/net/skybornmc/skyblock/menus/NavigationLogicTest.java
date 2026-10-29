package net.skybornmc.skyblock.menus;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NavigationLogicTest {

    private static final String HELP = """
            categories:
              start:
                status: active
                name: "Start"
                topics: [welcome, missing]
              islands:
                status: coming-soon
                name: "Islands"
                topics: []
              commands:
                all-commands: true
                name: "All"
            topics:
              welcome:
                name: "Welcome"
                aliases: [hello, Hi]
                take-me-there: "spawn"
                commands: [Info]
                pages:
                  - title: "One"
                    lines: ["a", "b"]
                  - title: "Two"
                    lines: ["c"]
            """;

    @Test
    void helpBookParsesCategoriesTopicsAndAliases() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(HELP);
        List<String> problems = new ArrayList<>();
        HelpBook book = HelpBook.parse(yaml, problems::add);

        assertEquals(List.of("start", "islands", "commands"), book.categories().stream().map(HelpBook.Category::id).toList());
        assertEquals(1, problems.size(), "unknown topic 'missing' is reported");
        assertEquals(List.of("welcome"), book.category("start").orElseThrow().topics());
        assertEquals(HelpBook.Status.COMING_SOON, book.category("islands").orElseThrow().status());
        assertTrue(book.category("commands").orElseThrow().allCommands());

        HelpBook.Topic welcome = book.topic("HI").orElseThrow();
        assertEquals("welcome", welcome.id());
        assertEquals(2, welcome.pages().size());
        assertEquals(List.of("a", "b"), welcome.pages().getFirst().lines());
        assertEquals("spawn", welcome.takeMeThere());
        assertEquals("start", book.categoryOf(welcome).orElseThrow().id());
        assertTrue(book.ids().containsAll(List.of("welcome", "hello", "hi", "start", "islands")));
        assertFalse(book.topic("nope").isPresent());
    }

    @Test
    void storedSettingValues() {
        assertTrue(SettingsRegistry.parse("true", false));
        assertFalse(SettingsRegistry.parse("false", true));
        assertTrue(SettingsRegistry.parse(null, true));
        assertFalse(SettingsRegistry.parse("garbage", false));
    }

    @Test
    void adminHelpers() {
        assertEquals(List.of("Main-thread work slower than this", "(milliseconds) is logged."),
                AdminFileMenu.wrap("Main-thread work slower than this (milliseconds) is logged.", 34));
        assertEquals(1, AdminFileMenu.steps(50)[0]);
        assertEquals(100, AdminFileMenu.steps(5_000)[2]);
        assertEquals(1_000_000_000L, AdminFileMenu.steps(Long.MAX_VALUE)[2]);
        assertEquals("abc", AdminFileMenu.shorten("abc"));
        assertEquals(60, AdminFileMenu.shorten("x".repeat(100)).length());
        assertEquals("5", AdminFileMenu.number(5.0));
        assertEquals("0.7", AdminFileMenu.number(0.7));
    }

    @Test
    void hubReservesTheFrame() {
        assertTrue(HubLayout.RESERVED.contains(0));
        assertTrue(HubLayout.RESERVED.contains(4));   // profile head row
        assertTrue(HubLayout.RESERVED.contains(45));
        assertTrue(HubLayout.RESERVED.contains(17));  // right border
        assertFalse(HubLayout.RESERVED.contains(10));
        assertFalse(HubLayout.RESERVED.contains(43));
        assertEquals(54 - 28, HubLayout.RESERVED.size());
    }
}
