package net.skybornmc.skyblock.achievements;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** achievements.yml validation of the AC2 category boost and the AC3 island section. */
class RegistryBoostsTest {

    private static final String BASE = """
            categories:
              skills: {name: "Skills", icon: book, slot: 20, description: "x", boost: skill_xp}
              plain: {name: "Plain", icon: book, slot: 21, description: "x"}
              broken: {name: "Broken", icon: book, slot: 22, description: "x", boost: sell}
              summary: {name: "On the head", icon: book, slot: 13, description: "x"}
            achievements:
              a: {name: "A", description: ["x"], icon: book, category: skills, metric: skill_mining, goals: [1, 2]}
            """;

    @Test
    void categoryBoostsAreValidated() {
        List<String> problems = new ArrayList<>();
        AchievementRegistry r = Fixtures.parse(BASE, problems);
        assertEquals(PersonalBoost.SKILL_XP, r.category("skills").orElseThrow().boost());
        assertEquals(PersonalBoost.NONE, r.category("plain").orElseThrow().boost(), "no boost key = no boost");
        assertTrue(r.category("broken").isEmpty(), "sell is not a personal boost (RTA-39)");
        assertTrue(r.category("summary").isEmpty(), "slot 13 is the summary head");
        assertEquals(2, problems.size(), problems.toString());
        assertTrue(r.island().isEmpty(), "the island section is optional");
    }

    @Test
    void islandAchievementsAreValidated() {
        List<String> problems = new ArrayList<>();
        AchievementRegistry r = Fixtures.parse(BASE + """
                island-achievements:
                  good: {name: "Good", description: ["x"], icon: book, metric: likes, goals: [1, 5], team-only: true,
                         title: {id: good_title, display: "Good", rarity: epic}}
                  bad_metric: {name: "Bad", description: ["x"], icon: book, metric: skill_mining, goals: [1]}
                  bad_goals: {name: "Bad", description: ["x"], icon: book, metric: likes, goals: [5, 1]}
                  off: {name: "Off", description: ["x"], icon: book, metric: likes, goals: [1], enabled: false}
                """, problems);
        assertEquals(List.of("good"), r.island().stream().map(IslandAchievementDef::id).toList());
        IslandAchievementDef good = r.islandAchievement("good").orElseThrow();
        assertTrue(good.teamOnly());
        assertEquals("good_title", good.title().id());
        assertTrue(r.titles().stream().anyMatch(t -> t.id().equals("good_title")), "island titles are defined like personal ones");
        assertEquals(4, problems.size(), problems.toString()); // 2 category problems + bad_metric + bad_goals
    }
}
