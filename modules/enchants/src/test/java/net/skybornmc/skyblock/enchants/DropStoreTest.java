package net.skybornmc.skyblock.enchants;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.DatabaseConfig;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.db.Sql;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** E2 drop SQL on H2: pity per profile, first finds per account and season, and the atomic book drop. */
class DropStoreTest {

    @TempDir
    Path dir;

    private Database db;
    private final DropStore store = new DropStore(new BookStore());
    private final UUID account = UUID.randomUUID();
    private final UUID profile = UUID.randomUUID();

    @BeforeEach
    void start() throws Exception {
        DatabaseConfig config = new DatabaseConfig();
        config.type = DatabaseConfig.Type.H2;
        config.h2File = "drop-store-test";
        config.poolSize = 2;
        db = new Database(config, dir.toFile(), Logger.getLogger("drop-store-test"));
        db.start();
        new Migrations(db, Logger.getLogger("drop-store-test")).migrate("enchants", getClass().getClassLoader());
    }

    @AfterEach
    void stop() {
        db.close();
    }

    @Test
    void pitySavesAndLoadsPerProfile() {
        db.blockingTransaction(c -> {
            store.savePity(c, profile, "mining", 1240.5, 1L);
            store.savePity(c, profile, "mining", 1300, 2L);
            store.savePity(c, profile, "combat", 7, 2L);
            store.savePity(c, UUID.randomUUID(), "mining", 9999, 2L);
            return null;
        });
        Map<String, Double> loaded = db.blocking(c -> store.loadPity(c, profile));
        assertEquals(Map.of("mining", 1300.0, "combat", 7.0), loaded);
    }

    @Test
    void firstFindActionsNeverGoDown() {
        db.blockingTransaction(c -> {
            store.saveFirstActions(c, account, 0, "mining", 120, 1L);
            store.saveFirstActions(c, account, 0, "mining", 80, 2L);
            return null;
        });
        assertEquals(120, db.blocking(c -> store.loadFirstFinds(c, account, 0)).get("mining").actions(), 1e-9);
        assertTrue(db.blocking(c -> store.loadFirstFinds(c, account, 1)).isEmpty(), "first finds are per season");
    }

    @Test
    void aMilestonePaysOnlyOnce() {
        assertTrue(db.<Boolean>blockingTransaction(c -> store.grantFirst(c, account, 0, "mining", 0, 100, 1L)));
        assertFalse(db.<Boolean>blockingTransaction(c -> store.grantFirst(c, account, 0, "mining", 0, 100, 1L)));
        assertTrue(db.<Boolean>blockingTransaction(c -> store.grantFirst(c, account, 0, "mining", 1, 1000, 1L)));
        DropStore.FirstFind f = db.blocking(c -> store.loadFirstFinds(c, account, 0)).get("mining");
        assertEquals(2, f.granted());
        assertEquals(1000, f.actions(), 1e-9);
    }

    @Test
    void dropWritesBookCollectionAndResetsPityTogether() {
        db.blockingTransaction(c -> {
            store.savePity(c, profile, "mining", 4999, 1L);
            return null;
        });
        UUID book = UUID.randomUUID();
        assertTrue(db.<Boolean>blockingTransaction(c -> store.mintDrop(c, book, "swiftness", 1, 0, "drop:mining", account, profile, "mining",
                -1, 0, true, 5L)));
        assertEquals(0, db.blocking(c -> store.loadPity(c, profile)).get("mining"), 1e-9);
        assertEquals(1, books("drop:mining"));
        assertEquals(1, db.<Integer>blocking(c -> Sql.queryOne(c, "SELECT COUNT(*) FROM enchant_seen WHERE player = ?",
                rs -> rs.getInt(1), account).orElse(0)));
    }

    @Test
    void aFirstFindDropAlreadyGivenWritesNothing() {
        assertTrue(db.<Boolean>blockingTransaction(c -> store.mintDrop(c, UUID.randomUUID(), "swiftness", 1, 0, "drop:mining", account, profile,
                "mining", 0, 100, true, 5L)));
        assertFalse(db.<Boolean>blockingTransaction(c -> store.mintDrop(c, UUID.randomUUID(), "swiftness", 1, 0, "drop:mining", account,
                UUID.randomUUID(), "mining", 0, 100, true, 6L)), "a second profile of the same account gets no second first find");
        assertEquals(1, books("drop:mining"));
    }

    private int books(String origin) {
        return db.<Integer>blocking(c -> Sql.queryOne(c, "SELECT COUNT(*) FROM enchant_books WHERE origin = ?", rs -> rs.getInt(1), origin)
                .orElse(0));
    }
}
