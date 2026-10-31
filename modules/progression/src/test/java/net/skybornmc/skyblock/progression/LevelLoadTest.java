package net.skybornmc.skyblock.progression;

import net.skybornmc.skyblock.common.progression.LevelSource;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PERF11: the level load builds its objects off the main thread; handing them over equals the old row-by-row restore. */
class LevelLoadTest {

    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();

    private static LevelStore.LevelRow row(UUID island, double total, int level, int best, long scannedAt) {
        Map<LevelSource, Double> points = new EnumMap<>(LevelSource.class);
        points.put(LevelSource.PILLARS, total);
        return new LevelStore.LevelRow(island, total, total * 2, level, best, 1_000L, points, 42.5, 7L, scannedAt, 2_000L);
    }

    @Test
    void preparedStatesCarryTheStoredValues() {
        LevelLoad.Prepared p = LevelLoad.prepare(List.of(row(a, 10, 3, 2, 99), row(b, 5, 1, 4, 0)), List.of(), List.of());
        assertEquals(2, p.levels());
        LevelManager.State s = p.states().get(a);
        assertEquals(10, s.total, 1e-9);
        assertEquals(20, s.earned, 1e-9);
        assertEquals(3, s.level);
        assertEquals(3, s.bestLevel, "best is never below the level (as before)");
        assertEquals(4, p.states().get(b).bestLevel);
        assertEquals(1_000L, s.reachedAt);
        assertEquals(2_000L, s.updatedAt);
        assertEquals(99, s.scannedAt);
        assertEquals(7L, s.bankLow);
        assertEquals(42.5, s.blockWorth, 1e-9);
        assertTrue(s.calculated);
        assertEquals(10, s.points.get(LevelSource.PILLARS), 1e-9);
    }

    @Test
    void laterRowOfTheSameIslandWins() {
        LevelLoad.Prepared p = LevelLoad.prepare(List.of(row(a, 10, 3, 3, 1), row(a, 12, 4, 4, 2)), List.of(), List.of());
        assertEquals(12, p.states().get(a).total, 1e-9);
        assertEquals(2, p.levels(), "the log line still counts rows");
    }

    @Test
    void existingStateKeepsItsObjectAndLiveFields() {
        Map<UUID, LevelManager.State> states = new HashMap<>();
        LevelManager.State live = new LevelManager.State();
        live.members = 3;
        live.averageSkill = 12.5;
        live.raw.put(LevelSource.PILLARS, 8.0);
        live.bankObserved = true;
        states.put(a, live);
        LevelLoad.Prepared p = LevelLoad.prepare(List.of(row(a, 10, 3, 3, 5), row(b, 1, 1, 1, 0)), List.of(), List.of());
        LevelManager.State preparedB = p.states().get(b);
        LevelLoad.adoptStates(states, p.states());

        assertSame(live, states.get(a), "an event created it before the load finished: same object");
        assertEquals(10, live.total, 1e-9);
        assertEquals(3, live.level);
        assertEquals(5, live.scannedAt);
        assertTrue(live.calculated);
        assertEquals(3, live.members, "fields the load never set stay");
        assertEquals(12.5, live.averageSkill, 1e-9);
        assertEquals(8.0, live.raw.get(LevelSource.PILLARS), 1e-9);
        assertTrue(live.bankObserved);
        assertSame(preparedB, states.get(b), "a new island takes the prepared state as it is");
    }

    @Test
    void bankSamplesAreAddedToExistingOnes() {
        Map<UUID, BankSamples> banks = new HashMap<>();
        BankSamples live = new BankSamples();
        live.add(5_000, 300, 0);
        banks.put(a, live);
        LevelLoad.Prepared p = LevelLoad.prepare(List.of(), List.of(), List.of(
                new LevelStore.BankRow(a, 1_000, 100), new LevelStore.BankRow(a, 2_000, -5), new LevelStore.BankRow(b, 1_000, 50)));
        LevelLoad.adoptBanks(banks, p.banks());
        assertSame(live, banks.get(a));
        assertEquals(3, live.size());
        assertEquals(0, live.lowest(5_000, 4_000).orElseThrow(), "negative stored balances still count as 0");
        assertEquals(1, banks.get(b).size());

        // Same result as the old row-by-row restore.
        BankSamples old = new BankSamples();
        old.add(5_000, 300, 0);
        old.restore(1_000, 100);
        old.restore(2_000, -5);
        assertEquals(old.lowest(5_000, 4_000), live.lowest(5_000, 4_000));
        assertEquals(old.lowest(5_000, 3_500), live.lowest(5_000, 3_500));
    }

    @Test
    void worthAdoptEqualsRestore() {
        List<LevelStore.WorthRow> rows = List.of(
                new LevelStore.WorthRow(a, 0, 0, 100, 0),
                new LevelStore.WorthRow(a, 1, -1, 50, 58_500),
                new LevelStore.WorthRow(a, 0, 0, 120, 0), // later row of the same chunk wins
                new LevelStore.WorthRow(b, 3, 3, -10, 5));

        BlockWorthLedger old = new BlockWorthLedger();
        old.add(a, 2, 2, 7); // live change before the load finished
        old.beginScan(a, 0, 0);
        for (LevelStore.WorthRow r : rows) {
            old.restore(r.island(), r.chunkX(), r.chunkZ(), r.blocks(), r.spawners());
        }

        BlockWorthLedger now = new BlockWorthLedger();
        now.add(a, 2, 2, 7);
        now.beginScan(a, 0, 0);
        BlockWorthLedger.Restored prepared = BlockWorthLedger.prepare(rows);
        assertEquals(2, prepared.islands());
        now.adopt(prepared);

        for (UUID island : List.of(a, b)) {
            assertEquals(old.worth(island), now.worth(island), 1e-9);
        }
        assertEquals(120, now.blocks(a, 0, 0), 1e-9);
        assertEquals(58_500, now.spawners(a, 1, -1), 1e-9);
        assertEquals(0, now.blocks(b, 3, 3), 1e-9);
        assertEquals(7, now.blocks(a, 2, 2), 1e-9);
        // The running scan of chunk 0,0 survives the hand-over: a live change lands on top of the scanned value.
        now.add(a, 0, 0, 5);
        old.add(a, 0, 0, 5);
        now.finishScan(a, 0, 0, 200, 0);
        old.finishScan(a, 0, 0, 200, 0);
        assertEquals(old.blocks(a, 0, 0), now.blocks(a, 0, 0), 1e-9);
        assertEquals(205, now.blocks(a, 0, 0), 1e-9);
        assertEquals(old.drainDirty().size(), now.drainDirty().size(), "restoring marks nothing dirty, as before");
    }
}
