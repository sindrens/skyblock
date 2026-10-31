package net.skybornmc.skyblock.production;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.DatabaseConfig;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.production.FuelState;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Island Fuel tables on H2 in MySQL mode (same SQL as MariaDB): capped grants, cells, rollback, save and load. */
class FuelStoreTest {

    private static final long MIN = FuelMath.MINUTE;
    private static final long H = FuelMath.HOUR;
    private static final long CAP = 12 * H;

    @TempDir
    Path dir;
    private Database db;
    private final FuelStore store = new FuelStore();

    @BeforeEach
    void start() throws Exception {
        DatabaseConfig config = new DatabaseConfig();
        config.type = DatabaseConfig.Type.H2;
        config.h2File = "production-test";
        config.poolSize = 2;
        db = new Database(config, dir.toFile(), Logger.getLogger("production-test"));
        db.start();
        new Migrations(db, Logger.getLogger("production-test")).migrate("production", getClass().getClassLoader());
    }

    @AfterEach
    void stop() {
        db.close();
    }

    private static FuelStore.Row row(UUID island, long fuel) {
        return new FuelStore.Row(island, 1, fuel, 0, fuel > 0 ? FuelState.FUELED : FuelState.ASLEEP, 1L, 1L);
    }

    private long fuel(UUID island) {
        return db.blockingTransaction(c -> store.fuel(c, island));
    }

    @Test
    void grantAddsAndIsCappedAtTheTank() {
        UUID island = UUID.randomUUID();
        FuelStore.Added a = db.blockingTransaction(c -> store.grant(c, row(island, H), 15 * MIN, CAP, 2L));
        assertEquals(15 * MIN, a.addedMs());
        assertEquals(H + 15 * MIN, fuel(island));
        FuelStore.Added b = db.blockingTransaction(c -> store.grant(c, row(island, CAP - 5 * MIN), 15 * MIN, CAP, 3L));
        assertEquals(5 * MIN, b.addedMs(), "LEAST(fuel + add, cap)");
        assertEquals(CAP, fuel(island));
    }

    @Test
    void aFullTankRefusesAGrant() {
        UUID island = UUID.randomUUID();
        FuelStore.Added a = db.blockingTransaction(c -> store.grant(c, row(island, CAP), 15 * MIN, CAP, 2L));
        assertEquals(0, a.addedMs());
        assertEquals(CAP, fuel(island));
    }

    @Test
    void aCellIsMarkedUsedAndAddsItsFuel() {
        UUID island = UUID.randomUUID();
        UUID item = UUID.randomUUID();
        FuelStore.Added a = db.blockingTransaction(c -> store.useCell(c, row(island, H), item, UUID.randomUUID(), 3 * H, CAP, 2L));
        assertEquals(3 * H, a.addedMs());
        assertEquals(4 * H, fuel(island));
        assertTrue(db.<Boolean>blockingTransaction(c -> store.cellUsed(c, item)));
    }

    @Test
    void aCellThatDoesNotFitRollsBackCompletely() {
        UUID island = UUID.randomUUID();
        UUID item = UUID.randomUUID();
        db.blockingTransaction(c -> {
            store.save(c, List.of(row(island, 10 * H)));
            return null;
        });
        assertThrows(RuntimeException.class,
                () -> db.blockingTransaction(c -> store.useCell(c, row(island, 10 * H), item, UUID.randomUUID(), 3 * H, CAP, 2L)));
        assertFalse(db.<Boolean>blockingTransaction(c -> store.cellUsed(c, item)), "the cell stays unused, so it can be given back and used later");
        assertEquals(10 * H, fuel(island));
    }

    @Test
    void theSameCellCanNeverBeUsedTwice() {
        UUID island = UUID.randomUUID();
        UUID item = UUID.randomUUID();
        db.blockingTransaction(c -> store.useCell(c, row(island, 0), item, UUID.randomUUID(), H, CAP, 2L));
        assertThrows(RuntimeException.class,
                () -> db.blockingTransaction(c -> store.useCell(c, row(island, H), item, UUID.randomUUID(), H, CAP, 3L)),
                "primary key on item_uuid");
        assertEquals(H, fuel(island));
    }

    @Test
    void savedRowsLoadBackAndRemovedIslandsGo() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        db.blockingTransaction(c -> {
            store.save(c, List.of(new FuelStore.Row(a, 3, 5 * H, 4, FuelState.ACTIVE, 10L, 20L), row(b, 0)));
            store.save(c, List.of(new FuelStore.Row(a, 3, 6 * H, 4, FuelState.FUELED, 30L, 40L)));
            return null;
        });
        List<FuelStore.Row> rows = db.blockingTransaction(store::loadAll);
        assertEquals(2, rows.size());
        FuelStore.Row ra = rows.stream().filter(r -> r.island().equals(a)).findFirst().orElseThrow();
        assertEquals(new FuelStore.Row(a, 3, 6 * H, 4, FuelState.FUELED, 30L, 40L), ra);
        db.blockingTransaction(c -> {
            store.delete(c, a);
            return null;
        });
        assertEquals(1, db.blockingTransaction(store::loadAll).size());
    }
}
