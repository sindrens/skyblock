package net.skybornmc.skyblock.skills;

import net.skybornmc.skyblock.common.skills.SkillType;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillTablesTest {

    @Test
    void defaultsFollowTheTierTables() {
        SkillTables t = SkillTables.defaults();
        // WORLD.md §4.2 / §4.3: tier XP.
        assertEquals(1, t.xp(SkillType.MINING, "coal_ore"));
        assertEquals(2.5, t.xp(SkillType.MINING, "iron_ore"));
        assertEquals(85, t.xp(SkillType.MINING, "crying_obsidian"));
        assertEquals(58, t.xp(SkillType.FORAGING, "warped_stem"));
        assertEquals(0, t.xp(SkillType.FORAGING, "coal_ore"));
        assertEquals(5, t.xp(SkillType.COMBAT, "zombie"));
        assertEquals(5, t.xp(SkillType.COMBAT, "minecraft:ZOMBIE"), "ids are normalized");
        assertEquals(0, t.xp(SkillType.COMBAT, "creeper"));
        // Mine XP grows about x1.45 per tier.
        List<Double> mine = new ArrayList<>(t.table(SkillType.MINING).values());
        for (int i = 2; i < mine.size(); i++) {
            double ratio = mine.get(i) / mine.get(i - 1);
            assertTrue(ratio > 1.3 && ratio < 1.75, "tier " + (i + 1) + " ratio " + ratio);
        }
        // GUI pass 7 Nov 2026: no unlocks by level (the Depths tiers are gone).
        assertTrue(t.unlocks(SkillType.COMBAT).isEmpty());
        assertTrue(t.unlocks(SkillType.MINING).isEmpty());
    }

    @Test
    void nextUnlock() {
        SkillTables t = new SkillTables(java.util.Map.of(), SkillTables.RETIRED_DEPTHS_UNLOCKS);
        assertEquals(5, t.nextUnlock(SkillType.MINING, 0));
        assertEquals(10, t.nextUnlock(SkillType.MINING, 5));
        assertEquals(42, t.nextUnlock(SkillType.MINING, 36));
        assertEquals(-1, t.nextUnlock(SkillType.MINING, 66));
        assertEquals(-1, t.nextUnlock(SkillType.FARMING, 0));
        assertEquals(-1, SkillTables.defaults().nextUnlock(SkillType.MINING, 0), "no unlocks by default");
    }

    @Test
    void writesDefaultsIntoAnEmptyFile() {
        YamlConfiguration yaml = new YamlConfiguration();
        List<String> problems = new ArrayList<>();
        SkillTables.Loaded loaded = SkillTables.load(yaml, "skills.yml", (s, id) -> true, problems::add);
        assertTrue(loaded.changed());
        assertTrue(problems.isEmpty(), problems.toString());
        assertEquals(40, loaded.tables().xp(SkillType.MINING, "nether_quartz_ore"));
        assertEquals(1.5, yaml.getDouble("xp.mining.copper_ore"));
        assertEquals(List.of(), yaml.getIntegerList("unlocks.combat"));
    }

    @Test
    void untouchedDepthsUnlocksAreEmptiedOnce() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.createSection("xp");
        yaml.set("unlocks.mining", List.of(5, 10, 15, 20, 25, 30, 35, 42, 50, 58, 66));
        yaml.set("unlocks.combat", List.of(10, 20));
        SkillTables.Loaded loaded = SkillTables.load(yaml, "skills.yml", (s, id) -> true, p -> { });
        assertTrue(loaded.changed());
        assertTrue(loaded.tables().unlocks(SkillType.MINING).isEmpty());
        assertEquals(List.of(10, 20), loaded.tables().unlocks(SkillType.COMBAT), "an owner list stays");
        assertFalse(SkillTables.load(yaml, "skills.yml", (s, id) -> true, p -> { }).changed(), "once");
    }

    @Test
    void reportsAndSkipsBrokenEntries() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString("""
                xp:
                  mining:
                    iron_ore: 3
                    "minecraft:gold_ore": 4.5
                    stone: lots
                    not_a_block: 2
                    diamond_ore: -1
                  wizardry:
                    wand: 1
                  combat: 5
                unlocks:
                  mining: [10, 5, 5, zero, -2]
                  nope: [1]
                """);
        List<String> problems = new ArrayList<>();
        SkillTables.Loaded loaded = SkillTables.load(yaml, "skills.yml", (s, id) -> !id.equals("not_a_block"), problems::add);
        assertFalse(loaded.changed());
        SkillTables t = loaded.tables();
        assertEquals(3, t.xp(SkillType.MINING, "iron_ore"));
        assertEquals(4.5, t.xp(SkillType.MINING, "gold_ore"));
        assertEquals(0, t.xp(SkillType.MINING, "stone"));
        assertEquals(0, t.xp(SkillType.MINING, "not_a_block"));
        assertEquals(0, t.xp(SkillType.MINING, "coal_ore"), "a written table replaces the defaults");
        assertEquals(0, t.xp(SkillType.COMBAT, "zombie"));
        assertEquals(List.of(5, 10), t.unlocks(SkillType.MINING), "sorted, unique, valid only");
        assertEquals(8, problems.size(), problems.toString());
    }
}
