package net.skybornmc.skyblock.npcs;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlacerStepTest {

    @Test
    void leftOverWalkGoesFirstAndAlone() {
        assertEquals(Placer.Step.PURGE, Placer.step(true, false, false), "before the old sets are removed");
        assertEquals(Placer.Step.PURGE, Placer.step(true, true, false), "before anything new spawns");
        assertEquals(Placer.Step.PURGE, Placer.step(true, true, true), "even when nothing waits (left-overs still go)");
    }

    @Test
    void thenRetireThenSpawnThenStop() {
        assertEquals(Placer.Step.RETIRE, Placer.step(false, false, false), "PERF5: the old placement goes before new sets");
        assertEquals(Placer.Step.SPAWN, Placer.step(false, true, false));
        assertEquals(Placer.Step.DONE, Placer.step(false, true, true));
    }
}
