package net.skybornmc.skyblock.progression;

import net.skybornmc.skyblock.progression.TaskCatalog.TaskDef;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Shared island progress, exactly-once completion, unlock keys and hand-in reservations. */
class TaskProgressTest {

    static TaskCatalog catalog() {
        YamlConfiguration y = new YamlConfiguration();
        task(y, "coal_a", "mine", "coal_ore", 100, "upgrade.size.1");
        task(y, "coal_b", "mine", "coal_ore", 300, "upgrade.size.2");
        task(y, "logs", "chop", "oak_log", 50, "upgrade.size.1");
        task(y, "mobs", "kill", "any", 10, null);
        task(y, "hand_in", "deliver", "custom:depths_coal", 64, "upgrade.size.2");
        task(y, "pillar", "pillar-level", "mining", 10, "upgrade.size.2");
        return TaskCatalog.load(y, TaskCatalog.Checks.all(), p -> { throw new AssertionError(p); });
    }

    static void task(YamlConfiguration y, String id, String type, String target, long amount, String unlock) {
        y.set("tasks." + id + ".tier", 1);
        y.set("tasks." + id + ".type", type);
        y.set("tasks." + id + ".target", target);
        y.set("tasks." + id + ".amount", amount);
        if (unlock != null) {
            y.set("tasks." + id + ".unlocks", List.of(unlock));
        }
    }

    private static List<String> ids(List<TaskDef> defs) {
        return defs.stream().map(TaskDef::id).toList();
    }

    @Test
    void membersShareOneCounterThatEveryTaskReads() {
        TaskProgress p = new TaskProgress(catalog());
        UUID island = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        // Two members of the same island mine: one counter, both tasks read it.
        assertTrue(p.add(island, "mine:coal_ore", 60));
        assertTrue(p.add(island, "mine:coal_ore", 50));
        assertEquals(110, p.counter(island, "mine:coal_ore"));
        assertEquals(0, p.counter(other, "mine:coal_ore"), "other islands are separate");
        assertEquals(ids(List.of(p.catalog().get("coal_a").orElseThrow())), ids(p.readyFor(island, "mine:coal_ore")));
        assertEquals(110, p.counted(island, p.catalog().get("coal_b").orElseThrow()));
        // Untracked keys are never stored.
        assertFalse(p.add(island, "mine:dirt", 5));
        assertEquals(0, p.counter(island, "mine:dirt"));
        assertFalse(p.add(island, "mine:coal_ore", 0));
    }

    @Test
    void aTaskCompletesExactlyOnce() {
        TaskProgress p = new TaskProgress(catalog());
        UUID island = UUID.randomUUID();
        TaskDef coal = p.catalog().get("coal_a").orElseThrow();
        p.add(island, "mine:coal_ore", 100);
        assertEquals(List.of(coal), p.readyFor(island, "mine:coal_ore"));
        p.add(island, "mine:coal_ore", 5);
        assertEquals(List.of(), p.readyFor(island, "mine:coal_ore"), "claimed: not handed out again while saving");
        assertEquals(List.of(), p.readyAll(island, d -> 0));
        // Save failed: released, handed out again on the next check.
        p.finish(island, coal, false);
        assertFalse(p.done(island, "coal_a"));
        assertEquals(List.of(coal), p.readyAll(island, d -> 0));
        p.finish(island, coal, true);
        assertTrue(p.done(island, "coal_a"));
        p.add(island, "mine:coal_ore", 1000);
        assertEquals(List.of("coal_b"), ids(p.readyFor(island, "mine:coal_ore")), "only the bigger task is left");
        assertEquals(1, p.doneCount(island));
    }

    @Test
    void storedProgressAndDoneTasksSurviveALoad() {
        TaskProgress p = new TaskProgress(catalog());
        UUID island = UUID.randomUUID();
        p.add(island, "chop:oak_log", 10); // counted before the load finished
        p.addStored(island, "chop:oak_log", 45);
        p.markDone(island, "coal_a");
        assertEquals(55, p.counter(island, "chop:oak_log"));
        assertEquals(List.of("logs"), ids(p.readyAll(island, d -> 0)));
        assertTrue(p.done(island, "coal_a"));
        assertEquals(100, p.counted(island, p.catalog().get("coal_a").orElseThrow()), "done tasks show full progress");
    }

    @Test
    void unlockKeysOpenWhenAllTheirTasksAreDone() {
        TaskProgress p = new TaskProgress(catalog());
        UUID island = UUID.randomUUID();
        TaskDef coal = p.catalog().get("coal_a").orElseThrow();
        TaskDef logs = p.catalog().get("logs").orElseThrow();
        assertFalse(p.unlocked(island, "upgrade.size.1"));
        assertTrue(p.unlocked(island, "upgrade.unknown.1"), "no task lists it");
        p.island(island).completing.add("coal_a");
        assertEquals(List.of(), p.finish(island, coal, true));
        p.island(island).completing.add("logs");
        assertEquals(List.of("upgrade.size.1"), p.finish(island, logs, true));
        assertTrue(p.unlocked(island, "upgrade.size.1"));
        assertFalse(p.unlocked(island, "upgrade.size.2"));
    }

    @Test
    void reachTasksUseTheLiveValue() {
        TaskProgress p = new TaskProgress(catalog());
        UUID island = UUID.randomUUID();
        assertEquals(List.of(), p.readyAll(island, d -> d.id().equals("pillar") ? 9 : 0));
        assertEquals(List.of("pillar"), ids(p.readyAll(island, d -> d.id().equals("pillar") ? 10 : 0)));
    }

    @Test
    void reservationsNeverOverfillADeliverTask() {
        TaskProgress p = new TaskProgress(catalog());
        UUID island = UUID.randomUUID();
        TaskDef hand = p.catalog().get("hand_in").orElseThrow();
        assertEquals(64, p.room(island, hand));
        long a = p.reserve(island, hand, 50);   // member A clicks
        long b = p.reserve(island, hand, 50);   // member B clicks while A is saving
        assertEquals(50, a);
        assertEquals(14, b);
        assertEquals(0, p.reserve(island, hand, 10));
        // A's write failed: room comes back. B's write worked.
        assertEquals(List.of(), p.release(island, hand, a, 0));
        assertEquals(50, p.room(island, hand));
        assertEquals(List.of(), p.release(island, hand, b, 14));
        assertEquals(14, p.counter(island, "deliver:hand_in"));
        long c = p.reserve(island, hand, 100);
        assertEquals(50, c);
        assertEquals(List.of(hand), p.release(island, hand, c, 50));
        assertEquals(0, p.room(island, hand), "completing: no more hand-ins");
        p.finish(island, hand, true);
        assertEquals(0, p.room(island, hand));
        assertEquals(0, p.reserve(island, hand, 1));
    }

    @Test
    void forgetDropsARemovedIsland() {
        TaskProgress p = new TaskProgress(catalog());
        UUID island = UUID.randomUUID();
        p.add(island, "mine:coal_ore", 5);
        p.forget(island);
        assertEquals(0, p.counter(island, "mine:coal_ore"));
        assertEquals(Long.MAX_VALUE, TaskProgress.saturatedAdd(Long.MAX_VALUE - 1, 5));
    }
}
