package net.skybornmc.skyblock.tutorial;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The bundled tutorial.yml: seven chapters as planned (owner 4 Nov 2026), every step and reward parses. */
class CatalogTest {

    static Catalog bundled(List<String> warnings) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(
                Objects.requireNonNull(CatalogTest.class.getClassLoader().getResourceAsStream(Catalog.FILE)), StandardCharsets.UTF_8));
        return Catalog.load(yaml, warnings::add);
    }

    @Test
    void bundledFileHasTheSevenPlannedChapters() {
        List<String> warnings = new ArrayList<>();
        Catalog c = bundled(warnings);
        assertEquals(List.of(), warnings);
        assertEquals(List.of("welcome", "island", "farm", "mine", "money", "together", "next", "farm_pro", "mine_pro", "fish_pro", "pve_pro"),
                c.chapters().stream().map(Catalog.Chapter::id).toList());
        assertEquals("Welcome to Phoenix", c.chapters().getFirst().name());
        assertEquals("Hale's Recruit", c.chapters().getLast().name());
    }

    @Test
    void everyRewardParsesAndMatchesThePlan() {
        Catalog c = bundled(new ArrayList<>());
        for (Catalog.Chapter ch : c.chapters()) {
            List<String> warnings = new ArrayList<>();
            List<Rewards.Reward> r = Rewards.parse(ch.rewards(), warnings::add);
            assertEquals(List.of(), warnings, ch.id());
            assertFalse(r.isEmpty(), ch.id() + " pays something");
        }
        assertEquals(List.of("money 50000"), c.chapter("welcome").orElseThrow().rewards());
        assertEquals(List.of("shards 100"), c.chapter("farm").orElseThrow().rewards());
        assertEquals(List.of("mining_shards 100"), c.chapter("mine").orElseThrow().rewards());
        assertEquals(List.of("key ember 1"), c.chapter("together").orElseThrow().rewards());
    }

    @Test
    void stepTextsFitTheScoreboardAndDialogsAreShort() {
        Catalog c = bundled(new ArrayList<>());
        for (Catalog.Chapter ch : c.chapters()) {
            for (Catalog.Step s : ch.steps()) {
                assertTrue(s.text().length() <= 32, s.key() + " text is too long for the scoreboard: " + s.text());
                assertTrue(s.dialog().size() <= 4, s.key());
                if (s.type() == StepType.NPC) {
                    assertFalse(s.dialog().isEmpty(), s.key() + " is an NPC step without dialog");
                }
            }
        }
    }

    @Test
    void brokenEntriesAreSkippedWithAWarning() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("chapters.a.name", "A");
        y.set("chapters.a.steps.ok.type", "menu");
        y.set("chapters.a.steps.ok.target", "hub");
        y.set("chapters.a.steps.bad.type", "teleport");
        y.set("chapters.a.steps.notarget.type", "npc");
        y.set("chapters.a.steps.audit.type", "audit");
        y.set("chapters.a.steps.audit.target", "nocolon");
        y.set("chapters.Bad-Id.steps.x.type", "island");
        y.set("idle.guide", List.of("Hello"));
        List<String> warnings = new ArrayList<>();
        Catalog c = Catalog.load(y, warnings::add);
        assertEquals(1, c.chapters().size());
        assertEquals(List.of("ok"), c.chapters().getFirst().steps().stream().map(Catalog.Step::id).toList());
        assertEquals(4, warnings.size(), warnings.toString());
        assertEquals(List.of("Hello"), c.idle("GUIDE"));
        assertEquals(List.of(), c.idle("bank"));
    }

    @Test
    void stepsMatchTheirSignals() {
        Catalog c = bundled(new ArrayList<>());
        Catalog.Step coal = c.step("mine.coal").orElseThrow();
        assertTrue(coal.matches(new Signal(StepType.GRIND, "mine", 1, Map.of("block", "COAL_ORE", "drop", "COAL"))));
        assertFalse(coal.matches(new Signal(StepType.GRIND, "mine", 1, Map.of("block", "STONE", "drop", "COBBLESTONE"))));
        assertFalse(coal.matches(new Signal(StepType.GRIND, "farm", 1, Map.of("drop", "COAL"))));
        Catalog.Step harvest = c.step("farm.harvest").orElseThrow();
        assertTrue(harvest.matches(new Signal(StepType.GRIND, "farm", 1, Map.of("block", "WHEAT"))));
        Catalog.Step hoe = c.step("farm.hoe_upgrade").orElseThrow();
        assertTrue(hoe.matches(new Signal(StepType.AUDIT, "item:island_tool_upgrade", 1, Map.of("kind", "hoe"))));
        assertFalse(hoe.matches(new Signal(StepType.AUDIT, "item:island_tool_upgrade", 1, Map.of("kind", "pickaxe"))));
        Catalog.Step invite = c.step("together.invite").orElseThrow();
        assertTrue(invite.matches(Signal.of(StepType.AUDIT, "island:island_coop", 1)));
        assertTrue(invite.matches(Signal.of(StepType.AUDIT, "island:island_invite", 1)));
        assertFalse(invite.matches(Signal.of(StepType.AUDIT, "island:island_kick", 1)));
        Catalog.Step plant = c.step("island.plant").orElseThrow();
        assertTrue(plant.matches(Signal.of(StepType.PLANT, "CARROTS", 1)));
        assertFalse(plant.matches(Signal.of(StepType.PLANT, "STONE", 1)));
        Catalog.Step rowan = c.step("welcome.rowan").orElseThrow();
        assertTrue(rowan.matches(Signal.of(StepType.NPC, "GUIDE", 1)));
        assertFalse(rowan.matches(Signal.of(StepType.MENU, "guide", 1)));
    }
}
