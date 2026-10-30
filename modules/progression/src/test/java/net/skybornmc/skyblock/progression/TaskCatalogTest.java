package net.skybornmc.skyblock.progression;

import net.skybornmc.skyblock.common.progression.Pillar;
import net.skybornmc.skyblock.common.progression.TaskType;
import net.skybornmc.skyblock.progression.TaskCatalog.TaskDef;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The bundled tasks.yml loads without problems; broken entries are skipped with a readable reason. */
class TaskCatalogTest {

    static YamlConfiguration bundled() throws Exception {
        try (var in = TaskCatalogTest.class.getClassLoader().getResourceAsStream("tasks.yml")) {
            assertNotNull(in, "tasks.yml missing");
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    @Test
    void bundledCatalogLoadsCleanly() throws Exception {
        List<String> problems = new ArrayList<>();
        TaskCatalog catalog = TaskCatalog.load(bundled(), TaskCatalog.Checks.all(), problems::add);
        assertEquals(List.of(), problems);
        assertEquals(180, catalog.size());
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12), catalog.tiers());
        // Plan example: Island Size I = mine 300 Coal Ore, chop 200 Oak Logs, harvest 100 Wheat.
        List<TaskDef> size1 = catalog.byUnlock("upgrade.island_size.1");
        assertEquals(List.of("t1_mine", "t1_chop", "t1_farm"), size1.stream().map(TaskDef::id).toList());
        assertEquals("coal_ore", size1.get(0).info().target());
        assertEquals(300, size1.get(0).info().amount());
        for (String key : catalog.unlockKeys()) {
            int n = catalog.byUnlock(key).size();
            assertTrue(n >= 1 && n <= 3, key + " needs 1-3 tasks, has " + n);
        }
        // Every planned level has tasks (M5.4 / M5.5 rely on the keys).
        assertEquals(3, catalog.byUnlock("upgrade.island_size.3").size());
        assertFalse(catalog.byUnlock("upgrade.member_slots.4").isEmpty());
        assertFalse(catalog.byUnlock("upgrade.generator.10").isEmpty());
        assertFalse(catalog.byUnlock("mastery.treasury.3").isEmpty());
        assertFalse(catalog.byUnlock("mastery.wings.1").isEmpty());
        assertTrue(catalog.byUnlock("upgrade.island_size.4").isEmpty());
    }

    @Test
    void indexesFollowTheCounters() throws Exception {
        TaskCatalog catalog = TaskCatalog.load(bundled(), TaskCatalog.Checks.all(), p -> { });
        assertTrue(catalog.tracks("mine:coal_ore"));
        assertTrue(catalog.tracks("earn:any"));
        assertTrue(catalog.tracks("kill:any"), "tier 12 kills any mob");
        assertTrue(catalog.tracks("deliver:t1_deliver"), "deliver tasks have their own counter");
        assertFalse(catalog.tracks("mine:dirt"));
        TaskDef pillar = catalog.get("t2_pillar_mining").orElseThrow();
        assertNull(pillar.info().counterKey());
        assertTrue(catalog.reach().contains(pillar));
        TaskDef mine = catalog.get("t1_mine").orElseThrow();
        assertEquals(Pillar.MINING, mine.info().pillar());
        assertEquals(50, mine.info().pillarXp(), 1e-9);
        assertEquals(20, mine.info().crystals());
        assertEquals(15, catalog.inTier(7).size());
    }

    @Test
    void brokenEntriesAreSkipped() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("tasks.ok.tier", 1);
        yaml.set("tasks.ok.type", "mine");
        yaml.set("tasks.ok.target", "minecraft:Coal_Ore");
        yaml.set("tasks.ok.amount", 10);
        yaml.set("tasks.no_type.tier", 1);
        yaml.set("tasks.no_type.amount", 10);
        yaml.set("tasks.bad_tier.tier", 0);
        yaml.set("tasks.bad_tier.type", "kill");
        yaml.set("tasks.bad_tier.amount", 1);
        yaml.set("tasks.zero.tier", 1);
        yaml.set("tasks.zero.type", "earn");
        yaml.set("tasks.zero.amount", 0);
        yaml.set("tasks.earn_target.tier", 1);
        yaml.set("tasks.earn_target.type", "earn");
        yaml.set("tasks.earn_target.target", "coal");
        yaml.set("tasks.earn_target.amount", 5);
        yaml.set("tasks.skill.tier", 1);
        yaml.set("tasks.skill.type", "skill-level");
        yaml.set("tasks.skill.target", "cooking");
        yaml.set("tasks.skill.amount", 5);
        yaml.set("tasks.deliver_any.tier", 1);
        yaml.set("tasks.deliver_any.type", "deliver");
        yaml.set("tasks.deliver_any.amount", 5);
        yaml.set("tasks.reward.tier", 1);
        yaml.set("tasks.reward.type", "chop");
        yaml.set("tasks.reward.amount", 5);
        yaml.set("tasks.reward.rewards.pillar-xp", 10);
        yaml.set("tasks.off.tier", 1);
        yaml.set("tasks.off.type", "craft");
        yaml.set("tasks.off.target", "bread");
        yaml.set("tasks.off.amount", 5);
        yaml.set("tasks.off.enabled", false);
        yaml.set("tasks.BadId.tier", 1);
        yaml.set("tasks.BadId.type", "sell");
        yaml.set("tasks.BadId.amount", 5);
        List<String> problems = new ArrayList<>();
        TaskCatalog catalog = TaskCatalog.load(yaml, new TaskCatalog.Checks(id -> id.endsWith("_ore"), id -> true, id -> true), problems::add);
        assertEquals(List.of("ok"), catalog.all().stream().map(TaskDef::id).toList());
        assertEquals("coal_ore", catalog.get("ok").orElseThrow().info().target());
        assertEquals(TaskType.MINE, catalog.get("ok").orElseThrow().info().type());
        assertEquals(8, problems.size(), String.join("\n", problems));
        assertTrue(problems.stream().allMatch(p -> p.startsWith("tasks.yml → tasks.") && p.endsWith("(skipped)")));
    }

    @Test
    void missingSectionIsReported() {
        List<String> problems = new ArrayList<>();
        assertEquals(0, TaskCatalog.load(new YamlConfiguration(), TaskCatalog.Checks.all(), problems::add).size());
        assertEquals(1, problems.size());
    }
}
