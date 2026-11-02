package net.skybornmc.skyblock.economy.sell;

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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A3: the Hot Item is announced once per day (whoever inserts first), and each player gets one join line per day. */
class HotAnnouncerTest {

    @TempDir
    Path dir;

    private Database db;

    @BeforeEach
    void start() throws Exception {
        DatabaseConfig dbc = new DatabaseConfig();
        dbc.type = DatabaseConfig.Type.H2;
        dbc.h2File = "hot-test";
        dbc.poolSize = 2;
        dbc.slowQueryMs = 60_000;
        Logger log = Logger.getLogger("hot-test");
        db = new Database(dbc, dir.toFile(), log);
        db.start();
        new Migrations(db, log).migrate("core", getClass().getClassLoader());
        new Migrations(db, log).migrate("economy", getClass().getClassLoader());
    }

    @AfterEach
    void stop() {
        db.close();
    }

    @Test
    void announcedOncePerDay() {
        assertTrue(db.blocking(c -> HotAnnouncer.claimDay(c, 20_000, "coal", 1)));
        assertFalse(db.blocking(c -> HotAnnouncer.claimDay(c, 20_000, "coal", 2)), "a restart must not announce again");
        assertTrue(db.blocking(c -> HotAnnouncer.claimDay(c, 20_001, "wheat", 3)), "the next day announces");
    }

    @Test
    void joinLineOncePerDay() {
        UUID p = UUID.randomUUID();
        assertTrue(db.blocking(c -> HotAnnouncer.firstToday(c, p, 20_000)));
        assertFalse(db.blocking(c -> HotAnnouncer.firstToday(c, p, 20_000)));
        assertTrue(db.blocking(c -> HotAnnouncer.firstToday(c, p, 20_001)));
        assertFalse(db.blocking(c -> HotAnnouncer.firstToday(c, p, 20_000)), "an older day never shows again");
        assertTrue(db.blocking(c -> HotAnnouncer.firstToday(c, UUID.randomUUID(), 20_001)));
    }
}
