package net.skybornmc.skyblock.enchants;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.DatabaseConfig;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.db.Sql;
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

/** Island stack books (EN15): the daily cap per profile and the book row are written together, or not at all. */
class StackBookStoreTest {

    @TempDir
    Path dir;

    private Database db;
    private final BookStore store = new BookStore();
    private final UUID owner = UUID.randomUUID();
    private final UUID profile = UUID.randomUUID();

    @BeforeEach
    void start() throws Exception {
        DatabaseConfig config = new DatabaseConfig();
        config.type = DatabaseConfig.Type.H2;
        config.h2File = "stack-books-test";
        config.poolSize = 2;
        db = new Database(config, dir.toFile(), Logger.getLogger("stack-books-test"));
        db.start();
        new Migrations(db, Logger.getLogger("stack-books-test")).migrate("enchants", getClass().getClassLoader());
    }

    @AfterEach
    void stop() {
        db.close();
    }

    private boolean mint(UUID profileKey, String day) {
        UUID id = UUID.randomUUID();
        return db.<Boolean>blockingTransaction(c -> store.mintCapped(c, id, "slaughter", 1, 0, StackEnchants.ORIGIN, owner, profileKey, 1L, day,
                StackEnchants.DAILY_BOOKS, 5));
    }

    private int rows(UUID profileKey) {
        return db.<Integer>blocking(c -> Sql.queryOne(c, "SELECT COUNT(*) FROM enchant_books WHERE profile_id = ? AND origin = ?",
                rs -> rs.getInt(1), profileKey, StackEnchants.ORIGIN).orElse(0));
    }

    @Test
    void fiveBooksPerProfilePerDay() {
        for (int i = 0; i < 5; i++) {
            assertTrue(mint(profile, "2026-10-30"), "book " + (i + 1));
        }
        assertFalse(mint(profile, "2026-10-30"), "the 6th is refused");
        assertEquals(5, rows(profile), "no row for the refused book");
        assertTrue(mint(profile, "2026-10-31"), "a new day");
        assertEquals(6, rows(profile));
    }

    @Test
    void otherProfilesHaveTheirOwnCap() {
        for (int i = 0; i < 5; i++) {
            mint(profile, "2026-10-30");
        }
        UUID second = UUID.randomUUID();
        assertTrue(mint(second, "2026-10-30"), "the owner's second profile counts on its own");
        assertEquals(1, rows(second));
        int seen = db.<Integer>blocking(c -> Sql.queryOne(c, "SELECT COUNT(*) FROM enchant_seen WHERE player = ? AND enchant_id = ?",
                rs -> rs.getInt(1), owner, "slaughter").orElse(0));
        assertEquals(1, seen, "collection entry once per account");
    }

    @Test
    void stackBooksDoNotUseRollLimits() {
        for (int i = 0; i < 5; i++) {
            mint(profile, "2026-10-30");
        }
        boolean rolled = db.<Boolean>blocking(c -> store.useDaily(c, profile, "2026-10-30", "apprentice", 5));
        assertTrue(rolled, "rolls are counted apart");
    }
}
