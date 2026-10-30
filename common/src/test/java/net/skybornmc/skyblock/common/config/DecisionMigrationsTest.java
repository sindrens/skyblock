package net.skybornmc.skyblock.common.config;

import net.skybornmc.skyblock.common.config.DecisionMigrations.Decision;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** REVIEW3 §6: owner answers reach live configs once, and never overwrite a value the owner chose. */
class DecisionMigrationsTest {

    private static final Logger LOG = Logger.getLogger("decisions-test");

    private static final Decision SPAWNER = new Decision("641", "stacking.yml", "spawners.mobs-per-spawner", 1.0, null, 4.0);
    private static final Decision JOINS = new Decision("N2-1", "islands.yml", "members.max-joins-per-season", 0, null, 3);
    private static final Decision KEYS = new Decision("R3-15", "afk.yml", "keys.milestones", null, "480:cinder:1", "480:blaze:1");

    @Test
    void oldDefaultIsReplaced(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("stacking.yml"), "# owner comment\nspawners:\n  mobs-per-spawner: 4.0\n  other: 7\n");
        Files.writeString(dir.resolve("islands.yml"), "members:\n  max-joins-per-season: 3\n  join-cooldown-hours: 24\n");
        List<String> ran = new DecisionMigrations(dir.toFile(), LOG).apply(List.of(SPAWNER, JOINS));
        assertEquals(List.of("641", "N2-1"), ran);
        YamlConfiguration stacking = YamlConfiguration.loadConfiguration(dir.resolve("stacking.yml").toFile());
        assertEquals(1.0, stacking.getDouble("spawners.mobs-per-spawner"));
        assertEquals(7, stacking.getInt("spawners.other"), "other keys stay");
        YamlConfiguration islands = YamlConfiguration.loadConfiguration(dir.resolve("islands.yml").toFile());
        assertEquals(0, islands.getInt("members.max-joins-per-season"));
        assertEquals(24, islands.getInt("members.join-cooldown-hours"), "join-cooldown-hours is kept (open question)");
    }

    @Test
    void ownerValueIsKept(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("stacking.yml"), "spawners:\n  mobs-per-spawner: 2.5\n");
        assertEquals(List.of("641"), new DecisionMigrations(dir.toFile(), LOG).apply(List.of(SPAWNER)), "marked applied");
        assertEquals(2.5, YamlConfiguration.loadConfiguration(dir.resolve("stacking.yml").toFile()).getDouble("spawners.mobs-per-spawner"));
    }

    @Test
    void eachDecisionRunsOnlyOnce(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("stacking.yml"), "spawners:\n  mobs-per-spawner: 4.0\n");
        DecisionMigrations m = new DecisionMigrations(dir.toFile(), LOG);
        m.apply(List.of(SPAWNER));
        // The owner sets the old value back on purpose later: it stays.
        Files.writeString(dir.resolve("stacking.yml"), "spawners:\n  mobs-per-spawner: 4.0\n");
        assertEquals(List.of(), m.apply(List.of(SPAWNER)));
        assertEquals(4.0, YamlConfiguration.loadConfiguration(dir.resolve("stacking.yml").toFile()).getDouble("spawners.mobs-per-spawner"));
        assertTrue(Files.exists(dir.resolve(DecisionMigrations.APPLIED_FILE)));
    }

    @Test
    void listItemIsReplacedInPlace(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("afk.yml"), "keys:\n  milestones:\n  - 60:ember:1\n  - 360:cinder:1\n  - 480:blaze:1\n");
        new DecisionMigrations(dir.toFile(), LOG).apply(List.of(KEYS));
        assertEquals(List.of("60:ember:1", "360:cinder:1", "480:cinder:1"),
                YamlConfiguration.loadConfiguration(dir.resolve("afk.yml").toFile()).getStringList("keys.milestones"));
    }

    @Test
    void ownerListWithoutTheOldItemIsKept() {
        assertNull(DecisionMigrations.migrated(KEYS, List.of("60:ember:1", "480:cinder:1")), "already applied live (30 Oct)");
        assertNull(DecisionMigrations.migrated(KEYS, "not a list"));
        assertNull(DecisionMigrations.migrated(SPAWNER, 1.0), "already the decided value");
        assertEquals(1.0, DecisionMigrations.migrated(SPAWNER, 4), "numbers compare by value (4 equals 4.0)");
    }

    @Test
    void missingFileOrKeyIsMarkedAppliedAndBrokenFileIsRetried(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("islands.yml"), "members: [broken\n");
        DecisionMigrations m = new DecisionMigrations(dir.toFile(), LOG);
        assertEquals(List.of("641"), m.apply(List.of(SPAWNER, JOINS)), "no stacking.yml yet: the new default is written later");
        assertEquals("members: [broken\n", Files.readString(dir.resolve("islands.yml")), "a broken file is never touched");
        Files.writeString(dir.resolve("islands.yml"), "members:\n  max-joins-per-season: 3\n");
        assertEquals(List.of("N2-1"), m.apply(List.of(SPAWNER, JOINS)), "tried again once the file is fixed");
    }

    @Test
    void parseSkipsDocumentationOnlyEntries() {
        String ledger = """
                decisions:
                  - id: "641"
                    file: stacking.yml
                    path: spawners.mobs-per-spawner
                    value: 1.0
                    migrate-from: 4.0
                  - id: "R3-9b"
                    file: audit.yml
                    path: retention.short-categories
                    item: dm
                  - id: "F12"
                    file: spigot.yml
                    check: false
                    migrate-from: 1
                """;
        List<Decision> parsed = new DecisionMigrations(Path.of(".").toFile(), LOG).parse(new StringReader(ledger));
        assertEquals(1, parsed.size());
        assertEquals("641", parsed.getFirst().id());
        assertEquals(4.0, parsed.getFirst().migrateFrom());
    }
}
