package net.skybornmc.skyblock.skills;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.DatabaseConfig;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.skills.SkillType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The skills table on H2 in MySQL mode (same SQL as MariaDB). */
class SkillRepositoryTest {

    @TempDir
    Path dir;

    private Database db;
    private final SkillRepository repo = new SkillRepository();
    private final SkillCurve curve = new SkillCurve(600, 1.2, 1.13, 35, 66);

    @BeforeEach
    void start() throws Exception {
        DatabaseConfig config = new DatabaseConfig();
        config.type = DatabaseConfig.Type.H2;
        config.h2File = "skills-test";
        config.poolSize = 2;
        db = new Database(config, dir.toFile(), Logger.getLogger("skills-test"));
        db.start();
        new Migrations(db, Logger.getLogger("skills-test")).migrate("skills", getClass().getClassLoader());
    }

    @AfterEach
    void stop() {
        db.close();
    }

    @Test
    void saveLoadAndUpdate() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        assertTrue(db.blocking(c -> repo.load(c, a, 0)).isEmpty());
        db.blockingTransaction(c -> {
            repo.save(c, 0, List.of(new PlayerSkills.Row(a, SkillType.MINING, 700.5, 1),
                    new PlayerSkills.Row(a, SkillType.COMBAT, 5, 0),
                    new PlayerSkills.Row(b, SkillType.MINING, 1, 0)), 1L);
            return null;
        });
        assertEquals(Map.of(SkillType.MINING, 700.5, SkillType.COMBAT, 5.0), db.blocking(c -> repo.load(c, a, 0)));
        db.blockingTransaction(c -> {
            repo.save(c, 0, List.of(new PlayerSkills.Row(a, SkillType.MINING, 900, 1)), 2L);
            return null;
        });
        assertEquals(900, db.blocking(c -> repo.load(c, a, 0)).get(SkillType.MINING));
        assertTrue(db.blocking(c -> repo.load(c, a, 1)).isEmpty(), "other season is separate");
        assertEquals(Map.of(SkillType.MINING, 1.0), db.blocking(c -> repo.load(c, b, 0)));
    }

    @Test
    void deltasAddToStoredXp() {
        UUID a = UUID.randomUUID();
        db.blockingTransaction(c -> {
            repo.save(c, 0, List.of(new PlayerSkills.Row(a, SkillType.MINING, 500, 0)), 1L);
            return null;
        });
        db.blockingTransaction(c -> {
            repo.addDeltas(c, a, 0, Map.of(SkillType.MINING, 200.0, SkillType.FORAGING, 3.0), curve, 2L);
            return null;
        });
        assertEquals(Map.of(SkillType.MINING, 700.0, SkillType.FORAGING, 3.0), db.blocking(c -> repo.load(c, a, 0)));
        int level = db.blocking(c -> net.skybornmc.skyblock.common.db.Sql.queryOne(c,
                "SELECT level FROM skills WHERE player = ? AND season_id = 0 AND skill = 'mining'", rs -> rs.getInt("level"), a)).orElseThrow();
        assertEquals(1, level, "level follows the curve");
    }

    @Test
    void topListsBestProfilePerPlayerWithNames() {
        db.blocking(c -> {
            net.skybornmc.skyblock.common.db.Sql.update(c, "CREATE TABLE IF NOT EXISTS players (uuid BINARY(16) NOT NULL PRIMARY KEY, name VARCHAR(32) NOT NULL)");
            return null;
        });
        UUID steve = UUID.randomUUID();
        UUID steveAlt = UUID.randomUUID(); // second profile of steve
        UUID alex = UUID.randomUUID();
        UUID nobody = UUID.randomUUID();
        Map<UUID, UUID> owners = Map.of(steve, steve, steveAlt, steve, alex, alex, nobody, nobody);
        db.blockingTransaction(c -> {
            net.skybornmc.skyblock.common.db.Sql.update(c, "INSERT INTO players (uuid, name) VALUES (?, ?)", steve, "Steve");
            net.skybornmc.skyblock.common.db.Sql.update(c, "INSERT INTO players (uuid, name) VALUES (?, ?)", alex, "Alex");
            repo.save(c, 0, List.of(new PlayerSkills.Row(steve, SkillType.MINING, 100.9, 1),
                    new PlayerSkills.Row(steveAlt, SkillType.MINING, 5_000, 9),
                    new PlayerSkills.Row(alex, SkillType.MINING, 900, 3),
                    new PlayerSkills.Row(nobody, SkillType.MINING, 50, 1),
                    new PlayerSkills.Row(alex, SkillType.COMBAT, 99_999, 20)), 1L);
            return null;
        });
        var top = db.blocking(c -> repo.top(c, 0, SkillType.MINING, 10,
                values -> net.skybornmc.skyblock.common.profile.BestProfile.collapse(values, owners::get, k -> true)));
        assertEquals(3, top.size());
        assertEquals(new net.skybornmc.skyblock.common.leaderboard.TopLine(steve, "Steve", 5_000), top.get(0), "best profile counts once");
        assertEquals(new net.skybornmc.skyblock.common.leaderboard.TopLine(alex, "Alex", 900), top.get(1));
        assertEquals(new net.skybornmc.skyblock.common.leaderboard.TopLine(nobody, null, 50), top.get(2), "unknown name stays null");
        assertEquals(2, db.blocking(c -> repo.top(c, 0, SkillType.MINING, 2,
                values -> net.skybornmc.skyblock.common.profile.BestProfile.collapse(values, owners::get, k -> true))).size());
        assertTrue(db.blocking(c -> repo.top(c, 1, SkillType.MINING, 10,
                values -> net.skybornmc.skyblock.common.profile.BestProfile.collapse(values, owners::get, k -> true))).isEmpty());
    }
}
