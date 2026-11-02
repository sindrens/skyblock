package net.skybornmc.skyblock.islandtools;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.DatabaseConfig;
import net.skybornmc.skyblock.common.db.Migrations;
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

/** Island tools against a real (H2) database: no double purchase, storage never goes below zero. */
class ToolStoreTest {

    @TempDir
    Path dir;

    private Database db;
    private ToolStore store;
    private final UUID profile = UUID.randomUUID();

    @BeforeEach
    void start() throws Exception {
        DatabaseConfig dbc = new DatabaseConfig();
        dbc.type = DatabaseConfig.Type.H2;
        dbc.h2File = "islandtools-test";
        dbc.poolSize = 4;
        dbc.slowQueryMs = 60_000;
        Logger log = Logger.getLogger("islandtools-test");
        db = new Database(dbc, dir.toFile(), log);
        db.start();
        new Migrations(db, log).migrate("core", getClass().getClassLoader());
        new Migrations(db, log).migrate("islandtools", getClass().getClassLoader());
        store = new ToolStore(db);
    }

    @AfterEach
    void stop() {
        db.close();
    }

    @Test
    void starterSetOnceAndPurchasesNeverTwice() {
        ToolStore.Rec wood = new ToolStore.Rec(profile, Kind.PICKAXE, 0, "", "copy-1", -1, false);
        store.createMissing(Map.of(Kind.PICKAXE, wood)).join();
        store.createMissing(Map.of(Kind.PICKAXE, wood.with(3, "x=1"))).join();
        assertEquals(0, store.load(profile).join().get(Kind.PICKAXE).tier(), "an existing tool is never replaced");
        ToolStore.Rec stone = wood.with(1, "");
        assertTrue(db.<Boolean>transaction(c -> ToolStore.change(c, wood, stone)).join());
        assertFalse(db.<Boolean>transaction(c -> ToolStore.change(c, wood, stone)).join(), "the second click sees the old row");
        assertEquals(1, store.load(profile).join().get(Kind.PICKAXE).tier());
    }

    @Test
    void storageAddsAndTakesSafely() {
        store.add(profile, Map.of("wheat", 100L)).join();
        store.add(profile, Map.of("wheat", 28L)).join();
        assertEquals(128L, (long) store.storage(profile).join().get("wheat"));
        assertTrue(db.<Boolean>transaction(c -> ToolStore.take(c, profile, "wheat", 64)).join());
        assertFalse(db.<Boolean>transaction(c -> ToolStore.take(c, profile, "wheat", 65)).join(), "never below zero");
        assertEquals(64L, (long) store.storage(profile).join().get("wheat"));
    }
}
