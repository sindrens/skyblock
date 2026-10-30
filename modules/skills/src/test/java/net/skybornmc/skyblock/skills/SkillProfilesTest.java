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

/** PROF: skills are stored per profile key; profile 1 (key = player UUID) keeps the rows from before profiles. */
class SkillProfilesTest {

    @TempDir
    Path dir;

    private Database db;
    private final SkillRepository repo = new SkillRepository();
    private final UUID player = UUID.randomUUID();
    private final UUID secondProfile = UUID.randomUUID();

    @BeforeEach
    void start() throws Exception {
        DatabaseConfig dbc = new DatabaseConfig();
        dbc.type = DatabaseConfig.Type.H2;
        dbc.h2File = "skills-profiles";
        dbc.poolSize = 2;
        dbc.slowQueryMs = 60_000;
        db = new Database(dbc, dir.toFile(), Logger.getLogger("t"));
        db.start();
        new Migrations(db, Logger.getLogger("t")).migrate("skills", getClass().getClassLoader());
    }

    @AfterEach
    void stop() {
        db.close();
    }

    @Test
    void eachProfileHasItsOwnSkillsAndDeletingOneKeepsTheOther() {
        db.blockingTransaction(c -> {
            repo.save(c, 0, List.of(new PlayerSkills.Row(player, SkillType.MINING, 5_000, 12)), 1);
            repo.save(c, 0, List.of(new PlayerSkills.Row(secondProfile, SkillType.MINING, 50, 1)), 1);
            return null;
        });
        Map<SkillType, Double> one = db.blocking(c -> repo.load(c, player, 0));
        Map<SkillType, Double> two = db.blocking(c -> repo.load(c, secondProfile, 0));
        assertEquals(5_000.0, one.get(SkillType.MINING));
        assertEquals(50.0, two.get(SkillType.MINING));

        db.blockingTransaction(c -> {
            repo.delete(c, secondProfile);
            return null;
        });
        assertTrue(db.blocking(c -> repo.load(c, secondProfile, 0)).isEmpty());
        assertEquals(5_000.0, db.blocking(c -> repo.load(c, player, 0)).get(SkillType.MINING));
    }
}
