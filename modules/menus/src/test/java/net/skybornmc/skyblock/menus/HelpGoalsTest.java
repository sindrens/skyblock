package net.skybornmc.skyblock.menus;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Plan 4 Nov C §1: /help is "What do you want to do?", goal cards with 1-2 buttons that do something. */
class HelpGoalsTest {

    @Test
    void theBundledGoalsAreTheSixOfThePlan() {
        for (String file : List.of("src/main/resources/help.yml", "../../server/plugins/PhoenixSkyblock/help.yml")) {
            List<String> problems = new ArrayList<>();
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new File(file));
            // Only the goals: the categories need item icons, which need a server.
            List<HelpBook.Goal> goals = HelpBook.goals(yaml.getConfigurationSection("goals"), problems::add);
            assertEquals(List.of(), problems, file);
            assertEquals(List.of("make-money", "upgrade-tools", "grow-island", "play-with-friends", "find-my-way", "rules-support"),
                    goals.stream().map(HelpBook.Goal::id).toList(), file);
            for (HelpBook.Goal g : goals) {
                assertFalse(g.buttons().isEmpty(), g.id() + " has a button");
                assertTrue(g.buttons().size() <= HelpBook.MAX_GOAL_BUTTONS, g.id());
                assertTrue(g.lore().size() <= 3, g.id() + ": at most 3 lines");
                assertTrue(g.name().contains("<bold>"), g.id() + ": bold name");
                assertTrue(g.subtitle().contains("<c:muted>"), g.id() + ": muted subtitle");
                for (HelpBook.GoalButton b : g.buttons()) {
                    assertFalse(b.command().startsWith("/"), g.id() + ": command without /");
                    assertFalse(b.action().isBlank(), g.id());
                }
            }
            HelpBook.Goal way = goals.stream().filter(g -> g.id().equals("find-my-way")).findFirst().orElseThrow();
            assertTrue(way.buttons().getFirst().nextStep(), "Find my way runs the shared next step");
            assertEquals("menu", way.fallback());
            assertTrue(way.lore().stream().anyMatch(l -> l.contains("<next>")), "Find my way shows the next step");
            assertTrue(yaml.isConfigurationSection("categories"), "the old topics stay as All topics");
        }
    }

    @Test
    void goalParsingRules() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                goals:
                  a:
                    name: "A"
                    lore: ["1", "2", "3", "4"]
                    buttons:
                      - {command: "/farm", name: "Go", action: "go"}
                      - {name: "No command"}
                      - {command: "shop"}
                      - {command: "sell"}
                  hidden: {show: false, name: "H"}
                  b: {name: "B"}
                  c: {name: "C"}
                  d: {name: "D"}
                  e: {name: "E"}
                  f: {name: "F"}
                  g: {name: "G"}
                """);
        List<String> problems = new ArrayList<>();
        List<HelpBook.Goal> goals = HelpBook.goals(yaml.getConfigurationSection("goals"), problems::add);
        assertEquals(List.of("a", "b", "c", "d", "e", "f"), goals.stream().map(HelpBook.Goal::id).toList(), "hidden skipped, 7th cut");
        HelpBook.Goal a = goals.getFirst();
        assertEquals(List.of("1", "2", "3"), a.lore());
        assertEquals(List.of("farm", "shop"), a.buttons().stream().map(HelpBook.GoalButton::command).toList());
        assertEquals("open", a.buttons().get(1).action(), "default action word");
        assertEquals(4, problems.size(), "no command, 3rd button, 4th lore line, 7th goal: " + problems);
        assertEquals(List.of(), HelpBook.goals(null, problems::add));
    }

    @Test
    void layoutHasAirAndNeverTouchesTheBottomRow() {
        assertArrayEquals(new int[]{0, 2, 4}, HelpGoalLayout.rows(3));
        assertArrayEquals(new int[]{1, 3}, HelpGoalLayout.rows(2));
        assertArrayEquals(new int[]{2}, HelpGoalLayout.rows(1));
        assertThrows(IllegalArgumentException.class, () -> HelpGoalLayout.rows(4));
        // six goals: two per row, rows 0, 2, 4
        int[] cards = new int[6];
        for (int i = 0; i < 6; i++) {
            cards[i] = HelpGoalLayout.card(i, 6);
        }
        assertArrayEquals(new int[]{1, 5, 19, 23, 37, 41}, cards);
        // five goals: the last one alone in the middle
        assertEquals(39, HelpGoalLayout.card(4, 5));
        assertEquals(21, HelpGoalLayout.card(0, 1));
        for (int n = 1; n <= HelpGoalLayout.MAX; n++) {
            Set<Integer> used = new HashSet<>();
            for (int i = 0; i < n; i++) {
                int card = HelpGoalLayout.card(i, n);
                for (int slot : new int[]{card, HelpGoalLayout.button(card, 0), HelpGoalLayout.button(card, 1)}) {
                    assertTrue(used.add(slot), n + " goals: slot used twice " + slot);
                    assertTrue(slot < 45, "above the bottom row");
                    assertTrue(slot % 9 >= 1 && slot % 9 <= 7, "inside columns 1-7");
                    assertTrue(slot != HelpGoalLayout.SLOT_ALL_TOPICS);
                }
                assertTrue(card % 9 == 1 || card % 9 == 5 || card % 9 == 3, "card column");
            }
        }
        assertThrows(IllegalArgumentException.class, () -> HelpGoalLayout.button(10, 2));
        assertThrows(IllegalArgumentException.class, () -> HelpGoalLayout.card(6, 6));
    }
}
