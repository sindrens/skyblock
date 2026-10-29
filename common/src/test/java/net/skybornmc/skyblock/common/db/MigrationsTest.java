package net.skybornmc.skyblock.common.db;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationsTest {

    private final Logger log = Logger.getLogger("test");

    @Test
    void splitsStatementsAndDropsComments() {
        List<String> parts = Migrations.split("-- comment\nCREATE TABLE a (x INT);\n\nCREATE TABLE b (y INT);\n");
        assertEquals(List.of("CREATE TABLE a (x INT)", "CREATE TABLE b (y INT)"), parts);
    }

    @Test
    void findsMigrationsInOrder() throws Exception {
        List<Migrations.Migration> found = Migrations.find("testmod", getClass().getClassLoader());
        assertEquals(2, found.size());
        assertEquals(1, found.get(0).version());
        assertEquals("add note", found.get(1).description());
    }

    @Test
    void appliesOnceAndIsIdempotent(@TempDir Path dir) throws Exception {
        try (Database db = H2.start(dir)) {
            Migrations migrations = new Migrations(db, log);
            assertEquals(2, migrations.migrate("testmod", getClass().getClassLoader()));
            assertEquals(0, migrations.migrate("testmod", getClass().getClassLoader()));
            UUID owner = UUID.randomUUID();
            db.blocking(c -> Sql.update(c, "INSERT INTO test_items (owner, season_id, amount, note) VALUES (?, ?, ?, ?)",
                    owner, 1, 5_000_000_000L, "hi"));
            long amount = db.blocking(c -> Sql.queryOne(c, "SELECT amount FROM test_items WHERE owner = ?", rs -> rs.getLong(1), owner))
                    .orElseThrow();
            assertEquals(5_000_000_000L, amount);
        }
    }

    @Test
    void failingMigrationNamesFileAndStatement(@TempDir Path dir) throws Exception {
        try (Database db = H2.start(dir)) {
            Migrations.MigrationException e = assertThrows(Migrations.MigrationException.class,
                    () -> new Migrations(db, log).migrate("broken", getClass().getClassLoader()));
            assertTrue(e.getMessage().contains("db/broken/V001__bad.sql"), e.getMessage());
            assertTrue(e.getMessage().contains("statement 2/2"), e.getMessage());
        }
    }

    @Test
    void uuidRoundTrip() {
        UUID uuid = UUID.randomUUID();
        assertEquals(uuid, Sql.toUuid(Sql.toBytes(uuid)));
    }

    @Test
    void transactionRollsBack(@TempDir Path dir) throws Exception {
        try (Database db = H2.start(dir)) {
            new Migrations(db, log).migrate("testmod", getClass().getClassLoader());
            assertThrows(Exception.class, () -> db.transaction(c -> {
                Sql.update(c, "INSERT INTO test_items (owner, season_id, amount) VALUES (?, ?, ?)", UUID.randomUUID(), 1, 1L);
                throw new java.sql.SQLException("boom");
            }).join());
            long count = db.blocking(c -> Sql.queryOne(c, "SELECT COUNT(*) FROM test_items", rs -> rs.getLong(1)).orElse(0L));
            assertEquals(0, count);
        }
    }
}
