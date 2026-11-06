package net.skybornmc.skyblock.milestones;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Spec §7.11: counting in memory, flushed in batches; nothing lost on a failed write. */
class ProgressBufferTest {

    @Test
    void addsUpPerScopeAndMemberAndDrainsPerProfile() {
        ProgressBuffer b = new ProgressBuffer();
        UUID island = UUID.randomUUID();
        UUID alex = UUID.randomUUID();
        UUID steve = UUID.randomUUID();
        b.add(ProgressBuffer.Scope.ISLAND, island, "w", alex, 5);
        b.add(ProgressBuffer.Scope.ISLAND, island, "w", steve, 3);
        b.add(ProgressBuffer.Scope.PROFILE, alex, "d", alex, 2);
        b.add(ProgressBuffer.Scope.ISLAND, island, "w", alex, 0);
        assertEquals(8, b.pending(ProgressBuffer.Scope.ISLAND, island, "w"));
        var alexs = b.drain(alex);
        assertEquals(2, alexs.size(), "Alex's island share and personal chain leave on quit");
        assertEquals(3, b.pending(ProgressBuffer.Scope.ISLAND, island, "w"));
        b.putBack(alexs);
        assertEquals(3, b.drain().size());
        assertTrue(b.isEmpty());
    }
}
