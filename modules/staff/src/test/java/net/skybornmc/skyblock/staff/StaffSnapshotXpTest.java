package net.skybornmc.skyblock.staff;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.DatabaseConfig;
import net.skybornmc.skyblock.common.db.Migrations;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Owner 2 Nov 2026: the staff-mode snapshot keeps the player's XP so a crash gives it back too. */
class StaffSnapshotXpTest {

    @TempDir
    Path dir;

    private Database db;
    private StaffStore store;

    @BeforeEach
    void start() throws Exception {
        DatabaseConfig dbc = new DatabaseConfig();
        dbc.type = DatabaseConfig.Type.H2;
        dbc.h2File = "staff-xp-test";
        dbc.poolSize = 4;
        dbc.slowQueryMs = 60_000;
        Logger log = Logger.getLogger("staff-xp-test");
        db = new Database(dbc, dir.toFile(), log);
        db.start();
        new Migrations(db, log).migrate("core", getClass().getClassLoader());
        new Migrations(db, log).migrate("staff", getClass().getClassLoader());
        store = new StaffStore(db);
    }

    @AfterEach
    void stop() {
        db.close();
    }

    @Test
    void xpRoundTrips() {
        UUID id = UUID.randomUUID();
        store.saveSnapshot(new StaffStore.Snapshot(id, new byte[]{1, 2}, "SURVIVAL", false, false, false, 37, 0.5f)).join();
        StaffStore.Snapshot s = store.snapshot(id).join().orElseThrow();
        assertEquals(37, s.xpLevel());
        assertEquals(0.5f, s.xpProgress(), 1e-6);
    }

    @Test
    void oldShapeHasNoXp() {
        UUID id = UUID.randomUUID();
        store.saveSnapshot(new StaffStore.Snapshot(id, new byte[]{1}, "SURVIVAL", false, false, false)).join();
        StaffStore.Snapshot s = store.snapshot(id).join().orElseThrow();
        assertEquals(0, s.xpLevel());
        assertEquals(0f, s.xpProgress(), 1e-6);
    }
}
