package net.skybornmc.skyblock.achievements;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PERF5: island progress drains in O(1) when nothing changed; deltas and gauges are never lost or written twice. */
class IslandProgressSaveTest {

    @Test
    void cleanIslandsDrainNothing() {
        IslandProgress p = new IslandProgress(UUID.randomUUID());
        p.load(Map.of(IslandMetric.LIKES, 5L, IslandMetric.ISLAND_LEVEL, 3L), Map.of());
        assertTrue(p.drain().isEmpty(), "loaded values are not written back");
        p.apply(IslandMetric.ISLAND_LEVEL, 2); // lower gauge: no change
        p.apply(IslandMetric.LIKES, 0);        // no counter change
        assertTrue(p.drain().isEmpty());
    }

    @Test
    void changesDrainOnceAndFailedWritesComeBack() {
        IslandProgress p = new IslandProgress(UUID.randomUUID());
        p.load(Map.of(), Map.of());
        p.apply(IslandMetric.LIKES, 2);
        p.apply(IslandMetric.LIKES, 3);
        p.apply(IslandMetric.ISLAND_LEVEL, 7);
        List<IslandProgress.Row> rows = p.drain();
        assertEquals(List.of(new IslandProgress.Row(IslandMetric.ISLAND_LEVEL, 7), new IslandProgress.Row(IslandMetric.LIKES, 5)), rows);
        assertTrue(p.drain().isEmpty(), "drained once");
        // The write failed: everything comes back, plus what happened meanwhile.
        p.apply(IslandMetric.LIKES, 1);
        p.restore(rows);
        assertEquals(List.of(new IslandProgress.Row(IslandMetric.ISLAND_LEVEL, 7), new IslandProgress.Row(IslandMetric.LIKES, 6)), p.drain());
        p.restore(List.of());
        assertTrue(p.drain().isEmpty(), "restoring nothing marks nothing");
    }

    @Test
    void earnedBeforeTheLoadIsStillSaved() {
        IslandProgress p = new IslandProgress(UUID.randomUUID());
        p.apply(IslandMetric.LIKES, 4);
        p.load(Map.of(IslandMetric.LIKES, 10L), Map.of());
        assertEquals(14, p.value(IslandMetric.LIKES));
        assertEquals(List.of(new IslandProgress.Row(IslandMetric.LIKES, 4)), p.drain());
    }
}
