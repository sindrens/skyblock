package net.skybornmc.skyblock.depths.satchel;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SatchelStateTest {

    private SatchelState loaded() {
        SatchelState s = new SatchelState(UUID.randomUUID());
        s.loaded(Map.of(), 0);
        return s;
    }

    @Test
    void dropsAreHeldPendingAndCounted() {
        SatchelState s = loaded();
        s.add("depths_coal", 3);
        s.add("depths_coal", 2);
        s.add("depths_iron", 1);
        assertEquals(5, s.amount("depths_coal"));
        assertEquals(6, s.used());
        assertEquals(Map.of("depths_coal", 5L, "depths_iron", 1L), s.takePending());
        assertFalse(s.hasPending());
        assertEquals(Map.of("depths_coal", 5L, "depths_iron", 1L), s.takeGains());
        assertFalse(s.hasGains());
    }

    @Test
    void escrowIsAllOrNothingAndReleaseRestores() {
        SatchelState s = loaded();
        s.add("depths_coal", 10);
        s.takePending();
        assertFalse(s.escrow(Map.of("depths_coal", 5L, "depths_iron", 1L)));
        assertEquals(10, s.amount("depths_coal"));
        assertTrue(s.escrow(Map.of("depths_coal", 10L)));
        assertEquals(0, s.amount("depths_coal"));
        assertEquals(0, s.used());
        assertFalse(s.escrow(Map.of("depths_coal", 1L)), "escrowed items can't be taken twice");
        assertFalse(s.hasPending(), "escrow never writes by itself");
        s.release(Map.of("depths_coal", 10L));
        assertEquals(10, s.amount("depths_coal"));
        assertEquals(10, s.used());
    }

    @Test
    void notLoadedRefusesEscrow() {
        SatchelState s = new SatchelState(UUID.randomUUID());
        s.add("depths_coal", 5);
        assertFalse(s.escrow(Map.of("depths_coal", 1L)));
    }

    @Test
    void consumeWritesANegativeChange() {
        SatchelState s = loaded();
        s.add("depths_coal", 300);
        s.takePending();
        assertTrue(s.consume(Map.of("depths_coal", 256L)));
        assertEquals(44, s.amount("depths_coal"));
        assertEquals(Map.of("depths_coal", -256L), s.takePending());
        assertFalse(s.consume(Map.of("depths_coal", 45L)));
        // Drops and use in the same batch cancel out.
        s.add("depths_coal", 6);
        assertTrue(s.consume(Map.of("depths_coal", 6L)));
        assertFalse(s.hasPending());
    }

    @Test
    void failedWriteHandsChangesBack() {
        SatchelState s = loaded();
        s.add("depths_coal", 4);
        Map<String, Long> taken = s.takePending();
        s.add("depths_coal", 1);
        s.returnPending(taken);
        assertEquals(Map.of("depths_coal", 5L), s.takePending());
        assertEquals(5, s.amount("depths_coal"), "memory is never changed by a failed write");
    }

    @Test
    void loadMergesRowsWithUnwrittenDrops() {
        SatchelState s = new SatchelState(UUID.randomUUID());
        s.add("depths_coal", 3); // mined during the few ms of loading
        s.loaded(Map.of("depths_coal", 100L, "depths_gold", 7L), 2);
        assertTrue(s.loaded());
        assertEquals(103, s.amount("depths_coal"));
        assertEquals(110, s.used());
        assertEquals(2, s.upgradeLevel());
        assertEquals(Map.of("depths_coal", 3L), s.takePending());
    }

    @Test
    void creditIsNotAGain() {
        SatchelState s = loaded();
        s.credit("depths_coal", 9);
        assertEquals(9, s.amount("depths_coal"));
        assertFalse(s.hasGains());
        assertEquals(Map.of("depths_coal", 9L), s.takePending());
    }

    @Test
    void idleOnlyWithoutWork() {
        SatchelState s = loaded();
        assertTrue(s.idle());
        s.add("depths_coal", 1);
        assertFalse(s.idle());
        s.takePending();
        assertFalse(s.idle(), "gains still need their audit row");
        s.takeGains();
        assertTrue(s.idle());
        s.inFlight = 1;
        assertFalse(s.idle());
    }
}
