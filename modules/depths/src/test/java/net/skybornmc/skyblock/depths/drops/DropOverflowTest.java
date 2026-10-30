package net.skybornmc.skyblock.depths.drops;

import net.skybornmc.skyblock.depths.drops.DropOverflow.Route;
import net.skybornmc.skyblock.depths.satchel.SatchelMath;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** R3-22: satchel → inventory → /box, mining stops only at the /box hard cap; combat drops are never lost. */
class DropOverflowTest {

    @Test
    void miningGoesToSatchelWhileItHasRoom() {
        assertEquals(Route.SATCHEL, DropOverflow.forBreak(true, false, false));
        assertEquals(Route.SATCHEL, DropOverflow.forBreak(true, false, true), "box state does not matter while the satchel has room");
    }

    @Test
    void fullSatchelNoLongerRefusesTheBreak() {
        assertEquals(Route.OVERFLOW, DropOverflow.forBreak(true, true, false));
    }

    @Test
    void miningStopsOnlyAtTheBoxHardCap() {
        assertEquals(Route.REFUSE_BOX_FULL, DropOverflow.forBreak(true, true, true));
    }

    @Test
    void loadingSatchelStillWaits() {
        assertEquals(Route.REFUSE_LOADING, DropOverflow.forBreak(false, false, false));
        assertEquals(Route.REFUSE_LOADING, DropOverflow.forBreak(false, true, true));
    }

    @Test
    void combatDropsAreNeverRefused() {
        assertEquals(Route.SATCHEL, DropOverflow.forKill(true, true, false));
        assertEquals(Route.OVERFLOW, DropOverflow.forKill(true, true, true), "full satchel: inventory, then /box (was lost)");
        assertEquals(Route.OVERFLOW, DropOverflow.forKill(true, false, false), "satchel loading: inventory, then /box");
        assertEquals(Route.OVERFLOW, DropOverflow.forKill(false, true, false), "satchel off: inventory, then /box");
    }

    @Test
    void bufferCollectsPerProfileAndDrainsOnce() {
        DropOverflow buffer = new DropOverflow();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        buffer.add(a, "depths_coal", 40);
        buffer.add(a, "depths_coal", 30);
        buffer.add(a, "depths_iron", 5);
        buffer.add(b, "depths_coal", 1);
        buffer.add(b, "depths_coal", 0);
        buffer.add(null, "depths_coal", 9);
        assertEquals(70, buffer.waiting(a, "depths_coal"));
        assertEquals(Map.of("depths_coal", 70L, "depths_iron", 5L), buffer.drain(a));
        assertEquals(Map.of(), buffer.drain(a), "a drained batch is never delivered twice");
        assertFalse(buffer.isEmpty());
        Map<UUID, Map<String, Long>> all = buffer.drainAll();
        assertEquals(Map.of(b, Map.of("depths_coal", 1L)), all);
        assertTrue(buffer.isEmpty());
    }

    @Test
    void batchBecomesFullStacks() {
        assertEquals(java.util.List.of(64, 6), java.util.Arrays.stream(SatchelMath.stacks(70, 64)).boxed().toList());
    }

    @Test
    void warningIsThrottledToTheConfiguredInterval() {
        assertTrue(SatchelMath.warnDue(0, 10_000, 3_000));
        assertFalse(SatchelMath.warnDue(10_000, 12_999, 3_000));
        assertTrue(SatchelMath.warnDue(10_000, 13_000, 3_000));
        assertTrue(SatchelMath.warnDue(10_000, 10_000, 0), "0 = always");
    }
}
