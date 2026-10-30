package net.skybornmc.skyblock.perks;

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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** perks_freerank on H2 in MySQL mode: one claim per player, one tag pick, rollbacks. */
class FreeRankStoreTest {

    @TempDir
    Path dir;

    private Database db;
    private FreeRankStore store;

    @BeforeEach
    void start() throws Exception {
        DatabaseConfig config = new DatabaseConfig();
        config.type = DatabaseConfig.Type.H2;
        config.h2File = "perks-test";
        config.poolSize = 2;
        db = new Database(config, dir.toFile(), Logger.getLogger("perks-test"));
        db.start();
        new Migrations(db, Logger.getLogger("perks-test")).migrate("perks", getClass().getClassLoader());
        store = new FreeRankStore(db);
    }

    @AfterEach
    void stop() {
        db.close();
    }

    @Test
    void claimOnlyOnce() {
        UUID p = UUID.randomUUID();
        assertTrue(store.load(p).join().isEmpty());
        assertTrue(store.claim(p, "Steve", "ember", 100).join());
        assertFalse(store.claim(p, "Steve", "ember", 200).join(), "second claim refused by the primary key");
        FreeRankRules.Claim claim = store.load(p).join().orElseThrow();
        assertEquals("ember", claim.group());
        assertEquals(100, claim.claimedAt());
        assertFalse(claim.tagPicked());
    }

    @Test
    void tagPickedOnceAndRollbacks() {
        UUID p = UUID.randomUUID();
        assertFalse(store.pickTag(p, "spark", 1).join(), "no claim, no tag");
        store.claim(p, "Alex", "ember", 1).join();
        assertTrue(store.pickTag(p, "spark", 2).join());
        assertFalse(store.pickTag(p, "ash", 3).join(), "only one tag");
        assertEquals("spark", store.load(p).join().orElseThrow().tag());
        assertFalse(store.unclaim(p).join(), "a claim with a tag is never undone");
        assertTrue(store.clearTag(p, "spark").join());
        assertTrue(store.pickTag(p, "ash", 4).join(), "after a failed grant the player can pick again");
    }

    @Test
    void failedRankGrantUndoesTheClaim() {
        UUID p = UUID.randomUUID();
        store.claim(p, "Bob", "ember", 1).join();
        assertTrue(store.unclaim(p).join());
        assertTrue(store.load(p).join().isEmpty());
        assertTrue(store.claim(p, "Bob", "ember", 2).join());
    }
}
