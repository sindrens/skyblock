package net.skybornmc.skyblock.stacking;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.DatabaseConfig;
import net.skybornmc.skyblock.common.db.Migrations;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The spawner table on H2 in MySQL mode: conditional changes make dupes impossible. */
class SpawnerStoreTest {

    @TempDir
    Path dir;

    private Database db;
    private final SpawnerStore store = new SpawnerStore();
    private final UUID island = UUID.randomUUID();
    private final UUID player = UUID.randomUUID();

    @BeforeEach
    void start() throws Exception {
        DatabaseConfig config = new DatabaseConfig();
        config.type = DatabaseConfig.Type.H2;
        config.h2File = "stacking-test";
        config.poolSize = 2;
        db = new Database(config, dir.toFile(), Logger.getLogger("stacking-test"));
        db.start();
        new Migrations(db, Logger.getLogger("stacking-test")).migrate("stacking", getClass().getClassLoader());
    }

    @AfterEach
    void stop() {
        db.close();
    }

    private boolean ok(net.skybornmc.skyblock.common.db.Sql.SqlFunction<Boolean> work) {
        return db.blocking(work);
    }

    private SpawnerStore.Row row(int x, String type, int amount) {
        return new SpawnerStore.Row("islands", x, 70, -5, type, amount, island, player, 1000L);
    }

    @Test
    void insertLoadAndRefuseDoubleInsert() {
        assertTrue(ok(c -> store.insert(c, row(1, "zombie", 1), 1L)));
        assertFalse(ok(c -> store.insert(c, row(1, "pig", 1), 2L)));
        assertTrue(ok(c -> store.insert(c, new SpawnerStore.Row("islands", 2, 70, -5, "pig", 1, null, null, 5L), 2L)));
        List<SpawnerStore.Row> rows = db.blocking(store::loadAll);
        assertEquals(2, rows.size());
        SpawnerStore.Row zombie = rows.stream().filter(r -> r.x() == 1).findFirst().orElseThrow();
        assertEquals(row(1, "zombie", 1), zombie);
        SpawnerStore.Row pig = rows.stream().filter(r -> r.x() == 2).findFirst().orElseThrow();
        assertEquals(null, pig.island());
    }

    @Test
    void changeOnlyFromTheExpectedAmount() {
        db.blocking(c -> store.insert(c, row(1, "zombie", 1), 1L));
        assertTrue(ok(c -> store.change(c, "islands", 1, 70, -5, "zombie", 1, 8, 2L)));
        // A second change based on the old amount fails: two adds can never both count the same old value.
        assertFalse(ok(c -> store.change(c, "islands", 1, 70, -5, "zombie", 1, 2, 3L)));
        // Wrong type fails too.
        assertFalse(ok(c -> store.change(c, "islands", 1, 70, -5, "pig", 8, 9, 3L)));
        assertEquals(8, db.blocking(store::loadAll).getFirst().amount());
        assertTrue(ok(c -> store.change(c, "islands", 1, 70, -5, "zombie", 8, 7, 4L)));
        assertEquals(7, db.blocking(store::loadAll).getFirst().amount());
    }

    @Test
    void changeToZeroDeletesOnce() {
        db.blocking(c -> store.insert(c, row(1, "zombie", 3), 1L));
        assertTrue(ok(c -> store.change(c, "islands", 1, 70, -5, "zombie", 3, 0, 2L)));
        assertFalse(ok(c -> store.change(c, "islands", 1, 70, -5, "zombie", 3, 0, 2L)));
        assertTrue(db.blocking(store::loadAll).isEmpty());
    }

    @Test
    void deleteAnyRemovesOrphans() {
        db.blocking(c -> store.insert(c, row(1, "zombie", 3), 1L));
        assertTrue(ok(c -> store.deleteAny(c, "islands", 1, 70, -5)));
        assertFalse(ok(c -> store.deleteAny(c, "islands", 1, 70, -5)));
    }

    // ------------------------------------------------------------------ SECFIX1 F-07 / R3-13: frozen, never deleted

    @Test
    void aLostBlockIsFrozenWithEverythingKept() {
        db.blocking(c -> store.insert(c, row(1, "zombie", 1), 1L));
        assertTrue(ok(c -> store.change(c, "islands", 1, 70, -5, "zombie", 1, 64, 2L)));
        java.util.Optional<SpawnerStore.Frozen> frozen = db.blocking(c -> store.freeze(c, "islands", 1, 70, -5, "block_gone", 5L));
        assertTrue(frozen.isPresent());
        assertEquals(64, frozen.get().row().amount(), "all 64 spawners are kept for staff");
        assertEquals("zombie", frozen.get().row().type());
        assertEquals(player, frozen.get().row().placedBy());
        assertEquals(SpawnerStore.FROZEN, frozen.get().state());
        assertTrue(db.blocking(store::loadAll).isEmpty(), "a frozen spawner never loads, so it never spawns and can't be taken");
        List<SpawnerStore.Frozen> waiting = db.blocking(c -> store.frozen(c, 10));
        assertEquals(1, waiting.size());
        assertEquals("block_gone", waiting.getFirst().reason());
    }

    @Test
    void frozenSpawnersCanNotBeTakenOrAddedTo() {
        db.blocking(c -> store.insert(c, row(1, "zombie", 8), 1L));
        db.blocking(c -> store.freeze(c, "islands", 1, 70, -5, "block_gone", 5L));
        assertFalse(ok(c -> store.change(c, "islands", 1, 70, -5, "zombie", 8, 0, 6L)), "no take from a frozen stack");
        assertFalse(ok(c -> store.change(c, "islands", 1, 70, -5, "zombie", 8, 9, 6L)), "no add to a frozen stack");
        assertTrue(db.blocking(c -> store.freeze(c, "islands", 1, 70, -5, "block_gone", 7L)).isEmpty(), "frozen once only");
        assertEquals(1, db.blocking(c -> store.frozen(c, 10)).size());
        // The place is free again for a new spawner.
        assertTrue(ok(c -> store.insert(c, row(1, "pig", 1), 8L)));
    }

    @Test
    void freezingNothingDoesNothing() {
        assertTrue(db.blocking(c -> store.freeze(c, "islands", 9, 70, -5, "block_gone", 5L)).isEmpty());
        assertTrue(db.blocking(c -> store.frozen(c, 10)).isEmpty());
    }

    // ------------------------------------------------------------------ R3-13a: disband/reset clears; R3-13b: staff restore

    @Test
    void disbandOrResetClearsTheIslandsSpawnersInsteadOfFreezing() {
        UUID other = UUID.randomUUID();
        db.blocking(c -> store.insert(c, row(1, "zombie", 8), 1L));
        db.blocking(c -> store.insert(c, row(2, "pig", 3), 1L));
        db.blocking(c -> store.insert(c, row(3, "cow", 2), 1L));
        db.blocking(c -> store.freeze(c, "islands", 3, 70, -5, "block_gone", 2L)); // already frozen before the disband
        db.blocking(c -> store.insert(c, new SpawnerStore.Row("islands", 9, 70, -5, "pig", 5, other, player, 1L), 1L));
        SpawnerStore.Cleared cleared = db.blocking(c -> store.clearIsland(c, island));
        assertEquals(2, cleared.rows().size());
        assertEquals(11, cleared.spawners());
        assertEquals(1, cleared.frozenRows(), "a waiting frozen row of the island is cleared too");
        List<SpawnerStore.Row> left = db.blocking(store::loadAll);
        assertEquals(1, left.size(), "other islands keep their spawners");
        assertEquals(other, left.getFirst().island());
        assertTrue(db.blocking(c -> store.frozen(c, 10)).isEmpty(), "nothing waits for staff");
        assertTrue(db.blocking(c -> store.freeze(c, "islands", 1, 70, -5, "block_gone", 5L)).isEmpty(),
                "a spawn tick after the clear can't freeze a cleared spawner");
        assertTrue(db.blocking(c -> store.restore(c, 1L)).isEmpty(), "a cleared row can't be restored");
    }

    @Test
    void onlyDisbandAndResetClear() {
        assertTrue(SpawnerManager.clearsSpawners(net.skybornmc.skyblock.common.island.IslandRemovedEvent.DISBAND));
        assertTrue(SpawnerManager.clearsSpawners(net.skybornmc.skyblock.common.island.IslandRemovedEvent.RESET));
        assertFalse(SpawnerManager.clearsSpawners(net.skybornmc.skyblock.common.island.IslandRemovedEvent.DELETE),
                "a staff delete keeps them (frozen when the block is gone), like the island bank");
        assertFalse(SpawnerManager.clearsSpawners(null));
    }

    @Test
    void clearingANullIslandDoesNothing() {
        db.blocking(c -> store.insert(c, new SpawnerStore.Row("islands", 2, 70, -5, "pig", 1, null, null, 5L), 2L));
        assertEquals(0, db.blocking(c -> store.clearIsland(c, null)).rows().size());
        assertEquals(1, db.blocking(store::loadAll).size());
    }

    @Test
    void aFrozenSpawnerIsRestoredExactlyOnce() {
        db.blocking(c -> store.insert(c, row(1, "zombie", 70), 1L));
        long id = db.blocking(c -> store.freeze(c, "islands", 1, 70, -5, "block_gone", 5L)).orElseThrow().id();
        List<SpawnerStore.Frozen> mine = db.blocking(c -> store.frozenPlacedBy(c, player, 10));
        assertEquals(1, mine.size());
        assertTrue(db.blocking(c -> store.frozenPlacedBy(c, UUID.randomUUID(), 10)).isEmpty());
        java.util.Optional<SpawnerStore.Frozen> first = db.blocking(c -> store.restore(c, id));
        assertTrue(first.isPresent());
        assertEquals(70, first.get().row().amount());
        assertEquals(SpawnerStore.RESTORED, first.get().state());
        assertTrue(db.blocking(c -> store.restore(c, id)).isEmpty(), "a double click can't give the spawners twice");
        assertTrue(db.blocking(c -> store.frozenPlacedBy(c, player, 10)).isEmpty(), "restored rows leave the staff list");
        assertTrue(db.blocking(c -> store.restore(c, 999L)).isEmpty());
    }
}
