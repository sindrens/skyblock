package net.skybornmc.skyblock.depths.engine;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** SECFIX1 F-11: relog and world change keep running respawn timers; ended ones are dropped; memory stays bounded. */
class DepletionMemoryTest {

    private final UUID alex = UUID.randomUUID();
    private final UUID steve = UUID.randomUUID();

    private static DepletionMemory.Entry<String> entry(long key, long endsAt) {
        return new DepletionMemory.Entry<>(key, "bedrock", "mine", endsAt);
    }

    @Test
    void aRelogKeepsTheTimersThatStillRun() {
        DepletionMemory<String> memory = new DepletionMemory<>(100);
        memory.park(alex, "depths", List.of(entry(1, 10_000), entry(2, 20_000), entry(3, 500)), 1_000);
        List<DepletionMemory.Entry<String>> back = memory.take(alex, "depths", 5_000);
        assertEquals(List.of(1L, 2L), back.stream().map(DepletionMemory.Entry::key).toList(), "the ended timer (500) is gone");
        assertTrue(memory.take(alex, "depths", 5_000).isEmpty(), "taken once");
    }

    @Test
    void timersEndingWhileAwayAreDropped() {
        DepletionMemory<String> memory = new DepletionMemory<>(100);
        memory.park(alex, "depths", List.of(entry(1, 10_000)), 1_000);
        assertTrue(memory.take(alex, "depths", 10_000).isEmpty());
    }

    @Test
    void worldsAndPlayersAreKeptApart() {
        DepletionMemory<String> memory = new DepletionMemory<>(100);
        memory.park(alex, "depths", List.of(entry(1, 10_000)), 0);
        memory.park(alex, "depths_2", List.of(entry(2, 10_000)), 0);
        memory.park(steve, "depths", List.of(entry(3, 10_000)), 0);
        assertTrue(memory.take(alex, "islands", 0).isEmpty());
        assertEquals(2L, memory.take(alex, "depths_2", 0).getFirst().key());
        assertEquals(1L, memory.take(alex, "depths", 0).getFirst().key());
        assertEquals(3L, memory.take(steve, "depths", 0).getFirst().key());
        assertEquals(0, memory.size());
    }

    @Test
    void parkingAgainReplacesTheOldList() {
        DepletionMemory<String> memory = new DepletionMemory<>(100);
        memory.park(alex, "depths", List.of(entry(1, 10_000)), 0);
        memory.park(alex, "depths", List.of(entry(2, 10_000)), 0);
        assertEquals(List.of(2L), memory.take(alex, "depths", 0).stream().map(DepletionMemory.Entry::key).toList());
    }

    @Test
    void memoryIsBoundedPerPlayerKeepingTheLongestTimers() {
        DepletionMemory<String> memory = new DepletionMemory<>(2);
        memory.park(alex, "depths", List.of(entry(1, 5_000), entry(2, 9_000), entry(3, 7_000)), 0);
        assertEquals(2, memory.size());
        assertEquals(List.of(2L, 3L), memory.take(alex, "depths", 0).stream().map(DepletionMemory.Entry::key).sorted().toList());
    }

    @Test
    void pruneDropsEndedTimersOfPlayersWhoNeverCameBack() {
        DepletionMemory<String> memory = new DepletionMemory<>(100);
        memory.park(alex, "depths", List.of(entry(1, 5_000)), 0);
        memory.park(steve, "depths", List.of(entry(2, 50_000)), 0);
        memory.prune(10_000);
        assertEquals(1, memory.size());
    }

    @Test
    void ticksLeftRoundsUpAndIsAtLeastOne() {
        assertEquals(1, DepletionMemory.ticksLeft(1_000, 1_000));
        assertEquals(1, DepletionMemory.ticksLeft(1_001, 1_000));
        assertEquals(20, DepletionMemory.ticksLeft(2_000, 1_000));
        assertEquals(21, DepletionMemory.ticksLeft(2_001, 1_000));
    }
}
