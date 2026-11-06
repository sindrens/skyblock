package net.skybornmc.skyblock;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.DatabaseConfig;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.db.Sql;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Money x100 (batch 10): every module's migrations, the money_x100 ones included, run on H2 in MySQL mode; the account
 * update scales money only, never shards or crystals, and caps instead of overflowing.
 */
class MoneyScaleSqlTest {

    static final List<String> MODULES = List.of("core", "economy", "auction", "progression", "islands", "profiles", "achievements",
            "quests", "moderation", "pass", "rewards", "crates");

    @TempDir
    Path dir;
    private Database db;

    @BeforeEach
    void start() throws Exception {
        DatabaseConfig config = new DatabaseConfig();
        config.type = DatabaseConfig.Type.H2;
        config.h2File = "money-scale-test";
        config.poolSize = 2;
        db = new Database(config, dir.toFile(), Logger.getLogger("money-scale-test"));
        db.start();
    }

    @AfterEach
    void stop() {
        db.close();
    }

    @Test
    void allMigrationsRunAndOnlyMoneyIsScaled() throws Exception {
        Migrations migrations = new Migrations(db, Logger.getLogger("money-scale-test"));
        for (String module : MODULES) {
            migrations.migrate(module, getClass().getClassLoader());
        }
        String sql;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("db/economy/V013__money_x100_eco_accounts.sql")) {
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        String update = sql.lines().filter(l -> !l.startsWith("--")).reduce("", String::concat).replace(";", "");
        db.blockingTransaction(c -> {
            Sql.update(c, "DELETE FROM eco_accounts");
            String insert = "INSERT INTO eco_accounts (owner_type, owner_id, currency, balance, updated_at) VALUES ('player', ?, ?, ?, 0)";
            Sql.update(c, insert, Sql.toBytes(A), "money", 1500L);
            Sql.update(c, insert, Sql.toBytes(A), "shards", 70L);
            Sql.update(c, insert, Sql.toBytes(B), "money", 922337203685477580L);
            Sql.update(c, update);
            return true;
        });
        assertEquals(150_000L, balance(A, "money"));
        assertEquals(70L, balance(A, "shards"), "shards are not money");
        assertEquals(Long.MAX_VALUE, balance(B, "money"), "capped, never an overflow error");
        assertTrue(update.startsWith("UPDATE eco_accounts"));
    }

    private static final java.util.UUID A = java.util.UUID.randomUUID();
    private static final java.util.UUID B = java.util.UUID.randomUUID();

    private long balance(java.util.UUID owner, String currency) {
        return db.blocking(c -> Sql.queryOne(c, "SELECT balance FROM eco_accounts WHERE owner_id = ? AND currency = ?",
                rs -> rs.getLong(1), Sql.toBytes(owner), currency).orElseThrow());
    }
}
