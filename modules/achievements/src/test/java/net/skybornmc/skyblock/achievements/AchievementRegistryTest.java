package net.skybornmc.skyblock.achievements;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The bundled achievements.yml (about 30 starter achievements) and the validation of broken entries. */
class AchievementRegistryTest {

    @Test
    void bundledFileHas30AchievementsInSixCategories() {
        AchievementRegistry r = Fixtures.registry();
        assertEquals(30, r.size());
        assertEquals(List.of("skills", "island", "combat", "economy", "gear", "social"),
                r.categories().stream().map(Category::id).toList());
        for (Category c : r.categories()) {
            assertFalse(r.inCategory(c.id()).isEmpty(), c.id() + " has achievements");
        }
        assertTrue(r.get("harvester").isEmpty(), "farming waits for Phase 7 (enabled: false)");
        assertTrue(r.get("angler").isEmpty(), "fishing waits for Phase 7 (enabled: false)");
    }

    @Test
    void bundledEntriesAreSane() {
        AchievementRegistry r = Fixtures.registry();
        Set<String> titles = new HashSet<>();
        int hidden = 0;
        for (AchievementDef def : r.all()) {
            assertTrue(def.tiers() >= 1 && def.tiers() <= AchievementRegistry.MAX_TIERS, def.id());
            for (int t = 2; t <= def.tiers(); t++) {
                assertTrue(def.goal(t) > def.goal(t - 1), def.id() + " goals rise");
            }
            if (def.hasTitle()) {
                assertTrue(titles.add(def.title().id()), "title " + def.title().id() + " is the reward of one achievement only");
            }
            if (def.hidden()) {
                hidden++;
            }
            assertTrue(AchievementRegistry.visible(def.name()).length() <= AchievementRegistry.MAX_NAME, def.id());
        }
        assertTrue(hidden >= 4, "a few secret achievements (DESIGN: fun/secret ones)");
        // Titles already in tags-titles.yml are referenced by id (no display), new ones bring a display.
        assertNull(r.get("tycoon").orElseThrow().title().display());
        assertEquals("deep_diver", r.get("into_the_void").orElseThrow().title().id());
        assertEquals("phoenix_reborn", r.get("reborn").orElseThrow().title().id());
        assertEquals("<pillar:mining>Prospector", r.get("prospector").orElseThrow().title().display());
    }

    @Test
    void metricLookupFindsEveryUser() {
        AchievementRegistry r = Fixtures.registry();
        assertEquals(List.of("prospector", "into_the_void"), r.byMetric(Metric.SKILL_MINING).stream().map(AchievementDef::id).toList());
        assertEquals(List.of("first_sale", "merchant"), r.byMetric(Metric.ITEMS_SOLD).stream().map(AchievementDef::id).toList());
        assertTrue(r.usedMetrics().contains(Metric.PLAYTIME_MINUTES));
        assertFalse(r.usedMetrics().contains(Metric.SKILL_FARMING), "only used by a disabled achievement");
        assertTrue(r.byMetric(Metric.SKILL_FARMING).isEmpty());
    }

    @Test
    void brokenEntriesAreSkippedWithAReadableProblem() {
        List<String> problems = new ArrayList<>();
        AchievementRegistry r = Fixtures.parse("""
                categories:
                  a: {name: "A", icon: stone, slot: 20}
                  b: {name: "B", icon: stone, slot: 20}
                  c: {name: "C", icon: stone, slot: 3}
                achievements:
                  ok:
                    name: "Fine"
                    description: ["One line."]
                    icon: stone
                    category: a
                    metric: items_sold
                    goals: [1, 10]
                    title: some_title
                  falling:
                    name: "Falling"
                    description: ["x"]
                    icon: stone
                    category: a
                    metric: items_sold
                    goals: [10, 5]
                  nometric:
                    name: "No metric"
                    description: ["x"]
                    icon: stone
                    category: a
                    metric: jumps
                    goals: [1]
                  badicon:
                    name: "Bad icon"
                    description: ["x"]
                    icon: not_an_item
                    category: a
                    metric: items_sold
                    goals: [1]
                  longname:
                    name: "<c:flame>This name is far too long for Bedrock"
                    description: ["x"]
                    icon: stone
                    category: a
                    metric: items_sold
                    goals: [1]
                  nocat:
                    name: "No cat"
                    description: ["x"]
                    icon: stone
                    category: b
                    metric: items_sold
                    goals: [1]
                  badrarity:
                    name: "Rarity"
                    description: ["x"]
                    icon: stone
                    category: a
                    metric: items_sold
                    goals: [1]
                    title: {id: t, display: "T", rarity: shiny}
                  off:
                    name: "Off"
                    description: ["x"]
                    icon: stone
                    category: a
                    metric: items_sold
                    goals: [1]
                    enabled: false
                """, problems);
        assertEquals(List.of("ok"), r.all().stream().map(AchievementDef::id).toList());
        assertEquals(List.of("a"), r.categories().stream().map(Category::id).toList());
        for (String id : List.of("categories.b", "categories.c", "falling", "nometric", "badicon", "longname", "nocat", "badrarity")) {
            assertTrue(problems.stream().anyMatch(p -> p.contains(id) && p.endsWith("(skipped)")), id + " reported: " + problems);
        }
        assertFalse(problems.stream().anyMatch(p -> p.contains("achievements.off")), "a disabled entry that is valid is no problem");
        assertEquals("some_title", r.get("ok").orElseThrow().title().id());
    }

    @Test
    void missingSectionsAreReported() {
        List<String> problems = new ArrayList<>();
        assertEquals(0, Fixtures.parse("other: 1\n", problems).size());
        assertEquals(1, problems.size());
    }
}
