package net.skybornmc.skyblock.enchants;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Counters keep counting while their row loads, and a book reset is never undone by the late load. */
class DropCountersTest {

    @Test
    void pityAddsTheSavedValueOnLoad() {
        DropCounters.Pity p = new DropCounters.Pity();
        p.add(Pool.MINING, 3);
        p.add(Pool.MINING, 0.5);
        assertFalse(p.loaded());
        p.load(Map.of("mining", 100.0, "combat", 7.0));
        assertTrue(p.loaded());
        assertEquals(103.5, p.since(Pool.MINING), 1e-9);
        assertEquals(7, p.since(Pool.COMBAT), 1e-9);
        assertTrue(p.dirty());
    }

    @Test
    void resetBeforeLoadIgnoresTheOldSavedValue() {
        DropCounters.Pity p = new DropCounters.Pity();
        p.add(Pool.MINING, 3);
        assertEquals(3, p.reset(Pool.MINING), 1e-9);
        p.add(Pool.MINING, 2);
        p.load(Map.of("mining", 4000.0));
        assertEquals(2, p.since(Pool.MINING), 1e-9);
    }

    @Test
    void restoreGivesBackAFailedReset() {
        DropCounters.Pity p = new DropCounters.Pity();
        p.load(Map.of("foraging", 10.0));
        double old = p.reset(Pool.FORAGING);
        p.add(Pool.FORAGING, 1);
        p.restore(Pool.FORAGING, old);
        assertEquals(11, p.since(Pool.FORAGING), 1e-9);
    }

    @Test
    void firstFindsStopCountingWhenAllAreGiven() {
        DropCounters.FirstFinds f = new DropCounters.FirstFinds();
        f.load(Map.of("mining", new DropStore.FirstFind(99, 0)));
        f.add(Pool.MINING, 1, 2);
        assertEquals(100, f.actions(Pool.MINING), 1e-9);
        f.begin(Pool.MINING);
        assertTrue(f.pending(Pool.MINING));
        f.finish(Pool.MINING, true, 0);
        assertFalse(f.pending(Pool.MINING));
        assertEquals(1, f.granted(Pool.MINING));
        f.skip(Pool.MINING, 1);
        assertEquals(2, f.granted(Pool.MINING));
        f.clean();
        f.add(Pool.MINING, 5, 2);
        assertEquals(100, f.actions(Pool.MINING), 1e-9);
        assertFalse(f.dirty());
    }

    @Test
    void firstFindsMergeCountedAndSaved() {
        DropCounters.FirstFinds f = new DropCounters.FirstFinds();
        f.add(Pool.COMBAT, 4, 2);
        f.load(Map.of("combat", new DropStore.FirstFind(50, 1)));
        assertEquals(54, f.actions(Pool.COMBAT), 1e-9);
        assertEquals(1, f.granted(Pool.COMBAT));
        f.finish(Pool.COMBAT, false, 1);
        assertEquals(1, f.granted(Pool.COMBAT), "a failed grant does not move on");
    }
}
