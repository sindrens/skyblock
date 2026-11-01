package net.skybornmc.skyblock.rewards;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.DatabaseConfig;
import net.skybornmc.skyblock.common.db.Migrations;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** reward_claims and profile_active_week on H2 (MySQL mode, same SQL as MariaDB). */
class PeriodicStoreTest {

    @TempDir
    Path dir;
    private Database db;
    private final UUID player = UUID.randomUUID();
    private final UUID profileA = UUID.randomUUID();
    private final UUID profileB = UUID.randomUUID();

    @BeforeEach
    void start() throws Exception {
        DatabaseConfig config = new DatabaseConfig();
        config.type = DatabaseConfig.Type.H2;
        config.h2File = "periodic-test";
        config.poolSize = 4;
        db = new Database(config, dir.toFile(), Logger.getLogger("periodic-test"));
        db.start();
        new Migrations(db, Logger.getLogger("periodic-test")).migrate("rewards", getClass().getClassLoader());
    }

    @AfterEach
    void stop() {
        db.close();
    }

    @Test
    void weekSecondsAddUpPerProfileAndWeek() {
        db.blocking(c -> {
            RewardStore.addWeekSeconds(c, profileA, "2026-W45", 3_000, 1);
            RewardStore.addWeekSeconds(c, profileA, "2026-W45", 1_000, 2);
            RewardStore.addWeekSeconds(c, profileA, "2026-W46", 50, 3);
            RewardStore.addWeekSeconds(c, profileB, "2026-W45", 0, 4);
            return null;
        });
        assertEquals(4_000L, db.blocking(c -> RewardStore.weekSeconds(c, profileA, "2026-W45")));
        assertEquals(50L, db.blocking(c -> RewardStore.weekSeconds(c, profileA, "2026-W46")));
        assertEquals(0L, db.blocking(c -> RewardStore.weekSeconds(c, profileB, "2026-W45")), "never summed over profiles");
    }

    @Test
    void aPeriodCanBeClaimedOncePerProfile() {
        db.blocking(c -> {
            RewardStore.insertPeriodClaim(c, "weekly", profileA, "2026-W45", player, "weekly:a:45", 1);
            return null;
        });
        assertTrue(db.blocking(c -> RewardStore.periodClaimed(c, "weekly", profileA, "2026-W45")));
        assertFalse(db.blocking(c -> RewardStore.periodClaimed(c, "weekly", profileB, "2026-W45")), "other profile");
        assertFalse(db.blocking(c -> RewardStore.periodClaimed(c, "weekly", profileA, "2026-W46")), "next week");
        assertFalse(db.blocking(c -> RewardStore.periodClaimed(c, "monthly", profileA, "2026-W45")), "other source");
        assertThrows(RuntimeException.class, () -> db.blocking(c -> {
            RewardStore.insertPeriodClaim(c, "weekly", profileA, "2026-W45", player, "weekly:a:45:again", 2);
            return null;
        }), "the unique key is the backstop");
    }

    @Test
    void deletingAProfileRemovesItsClaimsAndWeeks() throws SQLException {
        db.blocking(c -> {
            RewardStore.insertPeriodClaim(c, "monthly", profileA, "2026-11", player, "monthly:a:11", 1);
            RewardStore.addWeekSeconds(c, profileA, "2026-W45", 10, 1);
            RewardStore.deleteProfile(c, profileA);
            return null;
        });
        assertFalse(db.blocking(c -> RewardStore.periodClaimed(c, "monthly", profileA, "2026-11")));
        assertEquals(0L, db.blocking(c -> RewardStore.weekSeconds(c, profileA, "2026-W45")));
    }
}
