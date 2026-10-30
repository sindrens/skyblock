package net.skybornmc.skyblock.progression;

import net.skybornmc.skyblock.progression.TaskCatalog.TaskDef;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PERF5: the periodic task check is spread over seconds and reads only reach tasks once an island had its full check. */
class TaskCheckPlanTest {

    @Test
    void everyIslandIsCheckedExactlyOncePerPeriod() {
        int slices = 30;
        List<UUID> islands = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            islands.add(UUID.randomUUID());
        }
        for (long start : new long[]{0, 7, 1_000_001}) {
            int biggest = 0;
            for (UUID island : islands) {
                int hits = 0;
                for (long second = start; second < start + slices; second++) {
                    if (TaskCheckPlan.inSlice(island, second, slices)) {
                        hits++;
                    }
                }
                assertEquals(1, hits, "once per " + slices + " seconds");
            }
            for (long second = start; second < start + slices; second++) {
                int n = 0;
                for (UUID island : islands) {
                    if (TaskCheckPlan.inSlice(island, second, slices)) {
                        n++;
                    }
                }
                biggest = Math.max(biggest, n);
            }
            assertTrue(biggest < 60, "spread out, not all in one second: " + biggest);
        }
        assertTrue(TaskCheckPlan.inSlice(islands.getFirst(), 3, 0), "0 slices = every second");
    }

    @Test
    void fullCheckOnceAfterLoadReloadAndFailedSave() {
        TaskCheckPlan plan = new TaskCheckPlan();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        assertTrue(plan.needsFull(a), "never checked");
        plan.fullDone(a);
        assertFalse(plan.needsFull(a));
        assertTrue(plan.needsFull(b));
        // A failed completion save: only that island.
        plan.fullDone(b);
        plan.invalidate(b);
        assertTrue(plan.needsFull(b));
        assertFalse(plan.needsFull(a));
        // Load or reload: every island.
        plan.fullDone(b);
        plan.invalidateAll();
        assertTrue(plan.needsFull(a));
        assertTrue(plan.needsFull(b));
        plan.fullDone(a);
        plan.forget(a);
        assertTrue(plan.needsFull(a));
        assertEquals(0, plan.tracked());
    }

    @Test
    void reachOnlyCheckSkipsCounterAndSettledTasks() {
        TaskProgress p = new TaskProgress(TaskProgressTest.catalog());
        UUID island = UUID.randomUUID();
        TaskDef pillar = p.catalog().get("pillar").orElseThrow();
        // A counter task that is ready but was never claimed (e.g. loaded from the database) is not a reach task.
        p.addStored(island, "mine:coal_ore", 500);
        AtomicInteger reads = new AtomicInteger();
        assertEquals(List.of(), p.readyReach(island, d -> {
            reads.incrementAndGet();
            return 9;
        }));
        assertEquals(1, reads.get(), "one reach task read");
        assertEquals(List.of(pillar), p.readyReach(island, d -> 10));
        reads.set(0);
        assertEquals(List.of(), p.readyReach(island, d -> {
            reads.incrementAndGet();
            return 10;
        }), "claimed while saving");
        assertEquals(0, reads.get(), "a completing task's value is not read");
        p.finish(island, pillar, true);
        assertEquals(List.of(), p.readyReach(island, d -> {
            reads.incrementAndGet();
            return 99;
        }));
        assertEquals(0, reads.get(), "a done task's value is not read");
        // The full check still finds the counter tasks.
        assertEquals(List.of("coal_a", "coal_b"), p.readyAll(island, d -> 0).stream().map(TaskDef::id).toList());
    }

    @Test
    void reachOnlyCheckOfAnUnknownIslandStillClaims() {
        TaskProgress p = new TaskProgress(TaskProgressTest.catalog());
        UUID island = UUID.randomUUID();
        assertEquals(List.of(), p.readyReach(island, d -> 0));
        assertEquals(List.of("pillar"), p.readyReach(island, d -> 50).stream().map(TaskDef::id).toList());
        assertTrue(p.completing(island, "pillar"));
    }
}
