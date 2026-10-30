package net.skybornmc.skyblock.progression;

import net.skybornmc.skyblock.common.progression.TaskInfo;
import net.skybornmc.skyblock.common.progression.TaskType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskRulesTest {

    @Test
    void onlyCountingMembersOnTheirOwnIslandCount() {
        UUID own = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        assertEquals(own, TaskRules.countingIsland(own, true, true, own), "member on own island");
        assertEquals(own, TaskRules.countingIsland(own, true, false, null), "member in the Depths");
        assertNull(TaskRules.countingIsland(own, true, true, other), "visiting another island");
        assertNull(TaskRules.countingIsland(own, true, true, null), "islands world outside any island");
        assertNull(TaskRules.countingIsland(own, false, true, own), "coop players never count");
        assertNull(TaskRules.countingIsland(null, true, false, null), "no island");
    }

    @Test
    void keysAddToTheObjectAndAny() {
        assertArrayEquals(new String[]{"mine:coal_ore", "mine:any"}, TaskRules.keys("mine", "Coal_Ore"));
        assertArrayEquals(new String[]{"earn:any"}, TaskRules.keys("earn", "any"));
        assertTrue(TaskRules.excludedSource("AFK", Set.of("afk")));
        assertFalse(TaskRules.excludedSource("satchel", Set.of("afk")));
        assertFalse(TaskRules.excludedSource(null, Set.of("afk")));
    }

    @Test
    void counterKeys() {
        assertEquals("mine:coal_ore", new TaskInfo("a", TaskType.MINE, "coal_ore", 1, 1, List.of(), 0, null, 0).counterKey());
        assertEquals("deliver:a", new TaskInfo("a", TaskType.DELIVER, "coal", 1, 1, List.of(), 0, null, 0).counterKey());
        assertNull(new TaskInfo("a", TaskType.ISLAND_LEVEL, "any", 1, 1, null, 0, null, 0).counterKey());
        assertEquals(TaskType.SKILL_LEVEL, TaskType.byId("skill_level").orElseThrow());
    }

    @Test
    void idempotencyKeysAreShortAndDistinct() {
        UUID island = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        String key = TaskRules.idempotencyKey(12, island, "a_task_id_that_is_very_long_and_nearly_forty_eight_c", member);
        assertTrue(key.length() <= 96, key);
        assertEquals(key, TaskRules.idempotencyKey(12, island, "a_task_id_that_is_very_long_and_nearly_forty_eight_c", member));
        assertNotEquals(TaskRules.idempotencyKey(0, island, "t1_mine", member), TaskRules.idempotencyKey(0, island, "t1_chop", member));
        assertNotEquals(TaskRules.idempotencyKey(0, island, "t1_mine", member), TaskRules.idempotencyKey(1, island, "t1_mine", member));
        assertNotEquals(TaskRules.idempotencyKey(0, island, "t1_mine", member),
                TaskRules.idempotencyKey(0, island, "t1_mine", UUID.randomUUID()));
    }

    @Test
    void handInPlanTakesInSlotOrderUpToTheNeed() {
        assertArrayEquals(new int[]{0, 64, 6, 0}, TaskRules.plan(new int[]{0, 64, 32, 10}, 70));
        assertArrayEquals(new int[]{5, 3}, TaskRules.plan(new int[]{5, 3}, 100));
        assertArrayEquals(new int[]{0, 0}, TaskRules.plan(new int[]{5, 3}, 0));
        assertEquals(70, TaskRules.sum(TaskRules.plan(new int[]{0, 64, 32, 10}, 70)));
    }

    @Test
    void names() {
        assertEquals("Coal Ore", TaskRules.pretty("coal_ore"));
        assertEquals("Depths Coal", TaskRules.pretty("custom:depths_coal"));
        assertEquals("Zombies", TaskRules.plural("Zombie"));
        assertEquals("Witches", TaskRules.plural("Witch"));
        assertEquals("Drowned", TaskRules.plural("Drowned"));
        assertEquals("Oak Logs", TaskRules.plural("Oak Log"));
        assertEquals("Wither Skeletons", TaskRules.plural("Wither Skeleton"));
        assertEquals(41, TaskRules.percent(412, 1000));
        assertEquals(100, TaskRules.percent(5, 0));
        assertEquals(100, TaskRules.percent(2000, 1000));
    }
}
