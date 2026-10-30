package net.skybornmc.skyblock.progression;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Live block worth per chunk and rescans (PHASE-5 M5.2). */
class BlockWorthLedgerTest {

    private final UUID island = UUID.randomUUID();

    @Test
    void placeAndBreakMoveTheWorth() {
        BlockWorthLedger l = new BlockWorthLedger();
        l.add(island, 0, 0, 100);
        l.add(island, 1, 0, 50);
        assertEquals(150, l.worth(island), 1e-9);
        l.add(island, 0, 0, -100);
        assertEquals(50, l.worth(island), 1e-9);
        l.add(island, 1, 0, -500);
        assertEquals(0, l.worth(island), 1e-9, "never below 0 (blocks from before tracking)");
    }

    @Test
    void spawnersAddToTheBlocks() {
        BlockWorthLedger l = new BlockWorthLedger();
        l.add(island, 0, 0, 10);
        l.setSpawners(island, 0, 0, 58_500);
        assertEquals(58_510, l.worth(island), 1e-9);
        l.setSpawners(island, 0, 0, 0);
        assertEquals(10, l.worth(island), 1e-9);
    }

    @Test
    void changesDuringARescanAreKept() {
        BlockWorthLedger l = new BlockWorthLedger();
        l.add(island, 0, 0, 999); // drifted value
        l.beginScan(island, 0, 0);
        l.add(island, 0, 0, 30);  // placed after the copy was taken
        l.finishScan(island, 0, 0, 200, 5);
        assertEquals(235, l.worth(island), 1e-9);
        l.add(island, 0, 0, 10);
        assertEquals(245, l.worth(island), 1e-9, "after the scan, live changes apply normally");
    }

    @Test
    void dirtyRowsCarryCurrentValuesAndCanBeRetried() {
        BlockWorthLedger l = new BlockWorthLedger();
        l.add(island, 2, -3, 40);
        l.add(island, 2, -3, 2);
        List<LevelStore.WorthRow> rows = l.drainDirty();
        assertEquals(1, rows.size());
        assertEquals(42, rows.getFirst().blocks(), 1e-9);
        assertEquals(-3, rows.getFirst().chunkZ());
        assertFalse(l.hasDirty());
        l.markDirty(rows);
        assertTrue(l.hasDirty());
    }

    @Test
    void chunksOutsideTheBorderAreCleared() {
        BlockWorthLedger l = new BlockWorthLedger();
        l.add(island, 0, 0, 10);
        l.add(island, 9, 9, 99);
        l.clearOutside(island, Set.of(BlockWorthLedger.chunkKey(0, 0)));
        assertEquals(10, l.worth(island), 1e-9);
    }

    @Test
    void chunkKeyRoundTrip() {
        long k = BlockWorthLedger.chunkKey(-12, 345);
        assertEquals(-12, BlockWorthLedger.chunkX(k));
        assertEquals(345, BlockWorthLedger.chunkZ(k));
    }

    @Test
    void touchedIslandsAreDrainedForTheNightlyRescan() {
        BlockWorthLedger l = new BlockWorthLedger();
        l.add(island, 0, 0, 1);
        assertEquals(Set.of(island), l.drainTouched());
        assertEquals(Set.of(), l.drainTouched());
        l.forget(island);
        assertFalse(l.known(island));
    }
}
