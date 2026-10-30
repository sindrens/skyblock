package net.skybornmc.skyblock.depths.tiers;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.DatabaseConfig;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.db.Sql;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** PROF: Depths tier unlocks are stored per profile key. */
class TierUnlockProfilesTest {

    @TempDir
    Path dir;

    private Database db;
    private final UUID profileOne = UUID.randomUUID();
    private final UUID profileTwo = UUID.randomUUID();

    @BeforeEach
    void start() throws Exception {
        DatabaseConfig dbc = new DatabaseConfig();
        dbc.type = DatabaseConfig.Type.H2;
        dbc.h2File = "depths-profiles";
        dbc.poolSize = 2;
        dbc.slowQueryMs = 60_000;
        db = new Database(dbc, dir.toFile(), Logger.getLogger("t"));
        db.start();
        new Migrations(db, Logger.getLogger("t")).migrate("depths", getClass().getClassLoader());
    }

    @AfterEach
    void stop() {
        db.close();
    }

    private List<String> zones(UUID key) {
        return db.blocking(c -> Sql.query(c, "SELECT zone FROM depths_tier_unlocks WHERE player = ? ORDER BY zone",
                rs -> rs.getString(1), key));
    }

    @Test
    void unlocksAreSeparatePerProfile() {
        db.blocking(c -> {
            TierProgress.upsert(c, profileOne, 0, "mine_2", 1, null);
            TierProgress.upsert(c, profileOne, 0, "mine_3", 1, 2L);
            TierProgress.upsert(c, profileTwo, 0, "mine_2", 5, null);
            return null;
        });
        assertEquals(List.of("mine_2", "mine_3"), zones(profileOne));
        assertEquals(List.of("mine_2"), zones(profileTwo));
    }
}
