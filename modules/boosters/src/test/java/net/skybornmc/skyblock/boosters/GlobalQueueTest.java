package net.skybornmc.skyblock.boosters;

import net.skybornmc.skyblock.common.booster.BoosterType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalQueueTest {

    private static GlobalQueue.Entry entry(long id, BoosterType type, int percent, long ms, boolean active) {
        return new GlobalQueue.Entry(id, type, percent, ms, ms, UUID.randomUUID(), UUID.randomUUID(), "P" + id, active);
    }

    @Test
    void oneRunsPerTypeAndTheNextStartsWhenItEnds() {
        GlobalQueue q = new GlobalQueue();
        q.add(entry(1, BoosterType.SELL, 50, 1_000, true));
        q.add(entry(2, BoosterType.SELL, 50, 2_000, false));
        q.add(entry(3, BoosterType.SKILL_XP, 25, 5_000, true));
        assertEquals(50, q.percent(BoosterType.SELL));
        assertEquals(25, q.percent(BoosterType.SKILL_XP));
        assertEquals(0, q.percent(BoosterType.CRYSTALS));
        GlobalQueue.Tick t = q.tick(600, 10);
        assertFalse(t.changed());
        t = q.tick(600, 20);
        assertEquals(List.of(1L), t.ended().stream().map(e -> e.id).toList());
        assertEquals(List.of(2L), t.started().stream().map(e -> e.id).toList());
        assertEquals(20, t.ended().getFirst().endedAt);
        assertEquals(2_000, q.active(BoosterType.SELL).orElseThrow().remaining, "a queued booster keeps its full time");
        assertEquals(3_800, q.active(BoosterType.SKILL_XP).orElseThrow().remaining);
    }

    @Test
    void promoteFixesATypeWithoutARunningBooster() {
        GlobalQueue q = new GlobalQueue();
        q.add(entry(5, BoosterType.CRYSTALS, 50, 1_000, false));
        q.add(entry(4, BoosterType.CRYSTALS, 50, 1_000, false));
        List<GlobalQueue.Entry> started = q.promote();
        assertEquals(List.of(4L), started.stream().map(e -> e.id).toList(), "oldest first");
        assertTrue(q.promote().isEmpty());
        assertEquals(List.of(5L), q.queued(BoosterType.CRYSTALS).stream().map(e -> e.id).toList());
    }
}
