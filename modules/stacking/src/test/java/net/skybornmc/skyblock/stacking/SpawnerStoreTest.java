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
}
