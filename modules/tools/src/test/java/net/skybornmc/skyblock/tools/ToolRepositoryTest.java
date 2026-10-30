package net.skybornmc.skyblock.tools;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.DatabaseConfig;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.tools.ToolKind;
import net.skybornmc.skyblock.common.tools.ToolPerk;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The tools tables on H2 in MySQL mode (same SQL as MariaDB). */
class ToolRepositoryTest {

    @TempDir
    Path dir;

    private Database db;
    private final ToolRepository repo = new ToolRepository();

    @BeforeEach
    void start() throws Exception {
        DatabaseConfig config = new DatabaseConfig();
        config.type = DatabaseConfig.Type.H2;
        config.h2File = "tools-test";
        config.poolSize = 2;
        db = new Database(config, dir.toFile(), Logger.getLogger("tools-test"));
        db.start();
        new Migrations(db, Logger.getLogger("tools-test")).migrate("tools", getClass().getClassLoader());
    }

    @AfterEach
    void stop() {
        db.close();
    }

    @Test
    void insertSaveLoadWithOwnerHistory() {
        UUID steve = UUID.randomUUID();
        UUID alex = UUID.randomUUID();
        ToolData tool = ToolData.fresh(UUID.randomUUID(), ToolKind.AXE, 2, true, null);
        db.blockingTransaction(c -> {
            repo.insert(c, tool, steve, "Steve", null, "starter", 0, 1L);
            return null;
        });
        ToolData levelled = tool.withProgress(7, 3000).withPerks(List.of(new ToolPerk(1, "treefeller", 1))).addStat(ToolData.STAT_LOGS, 40);
        db.blockingTransaction(c -> {
            repo.save(c, levelled, steve, "Steve", 0, 2L);
            repo.save(c, levelled, alex, "Alex", 0, 3L);
            return null;
        });
        ToolRepository.Row row = db.blocking(c -> repo.load(c, tool.id())).orElseThrow();
        assertEquals(levelled, row.tool());
        assertEquals(alex, row.owner());
        assertEquals(List.of("Steve", "Alex"), row.history().stream().map(ToolCodec.Owner::name).toList());
        assertEquals("starter", row.source());
    }

    @Test
    void saveInsertsAMissingRow() {
        ToolData tool = ToolData.fresh(UUID.randomUUID(), ToolKind.SWORD, 1, false, null);
        db.blockingTransaction(c -> {
            repo.save(c, tool, UUID.randomUUID(), "Steve", 0, 1L);
            return null;
        });
        assertEquals(tool, db.blocking(c -> repo.load(c, tool.id())).orElseThrow().tool());
    }

    @Test
    void upgradeIsCompareAndSet() {
        UUID steve = UUID.randomUUID();
        ToolData tool = ToolData.fresh(UUID.randomUUID(), ToolKind.PICKAXE, 1, true, null).withProgress(12, 9000);
        db.blockingTransaction(c -> {
            repo.insert(c, tool, steve, "Steve", null, "starter", 0, 1L);
            return null;
        });
        ToolData up = ToolProgress.upgrade(tool, 2);
        assertTrue((boolean) db.<Boolean>blockingTransaction(c -> repo.upgrade(c, up, 1, steve, "Steve", 0, 2L)));
        assertFalse((boolean) db.<Boolean>blockingTransaction(c -> repo.upgrade(c, up, 1, steve, "Steve", 0, 3L)), "a duplicated copy can't upgrade again");
        ToolData stored = db.blocking(c -> repo.load(c, tool.id())).orElseThrow().tool();
        assertEquals(2, stored.tier());
        assertEquals(12, stored.level(), "level kept");
        assertFalse(stored.starter());
    }

    @Test
    void blueprintCanCraftOnlyOnce() {
        UUID blueprint = UUID.randomUUID();
        UUID steve = UUID.randomUUID();
        db.blockingTransaction(c -> {
            repo.insert(c, ToolData.fresh(UUID.randomUUID(), ToolKind.PICKAXE, 4, false, "auto_smelt"), steve, "Steve", blueprint, "blueprint", 0, 1L);
            return null;
        });
        CompletionException e = assertThrows(CompletionException.class, () -> db.blockingTransaction(c -> {
            repo.insert(c, ToolData.fresh(UUID.randomUUID(), ToolKind.PICKAXE, 4, false, "auto_smelt"), steve, "Steve", blueprint, "blueprint", 0, 2L);
            return null;
        }));
        assertInstanceOf(ToolRepository.BlueprintUsedException.class, e.getCause());
        // tools without a blueprint never collide (NULL is allowed many times)
        db.blockingTransaction(c -> {
            repo.insert(c, ToolData.fresh(UUID.randomUUID(), ToolKind.AXE, 1, true, null), steve, "Steve", null, "starter", 0, 3L);
            repo.insert(c, ToolData.fresh(UUID.randomUUID(), ToolKind.AXE, 1, true, null), steve, "Steve", null, "starter", 0, 4L);
            return null;
        });
    }

    @Test
    void perksForEnchantTransactions() {
        ToolData tool = ToolData.fresh(UUID.randomUUID(), ToolKind.PICKAXE, 3, false, null);
        db.blockingTransaction(c -> {
            repo.insert(c, tool, null, null, null, "admin", 0, 1L);
            repo.savePerks(c, tool.id(), List.of(new ToolPerk(1, "vein_rend", 3)), 2L);
            return null;
        });
        assertEquals(List.of(new ToolPerk(1, "vein_rend", 3)), db.blocking(c -> repo.load(c, tool.id())).orElseThrow().tool().perks());
    }

    @Test
    void skinsAndBlueprintFinds() {
        UUID p = UUID.randomUUID();
        assertTrue((boolean) db.<Boolean>blockingTransaction(c -> repo.grantSkin(c, p, "frostbite", "admin", 1L)));
        assertFalse((boolean) db.<Boolean>blockingTransaction(c -> repo.grantSkin(c, p, "frostbite", "admin", 2L)), "already owned");
        assertTrue((boolean) db.<Boolean>blockingTransaction(c -> repo.grantSkin(c, p, "royal_gold", "crate", 3L)));
        assertEquals(Set.of("frostbite", "royal_gold"), db.blocking(c -> repo.skins(c, p)));

        db.blockingTransaction(c -> {
            repo.recordBlueprint(c, p, "magma_pickaxe", 0, 1L);
            repo.recordBlueprint(c, p, "magma_pickaxe", 0, 2L);
            repo.recordBlueprint(c, p, "sage_pickaxe", 1, 3L);
            return null;
        });
        assertEquals(Map.of("magma_pickaxe", 2), db.blocking(c -> repo.blueprintsFound(c, p, 0)));
        assertEquals(Map.of("sage_pickaxe", 1), db.blocking(c -> repo.blueprintsFound(c, p, 1)));
    }

    @Test
    void starterClaimCooldownIsAtomic() {
        UUID p = UUID.randomUUID();
        long day = Duration.ofHours(24).toMillis();
        assertEquals(0, (long) db.<Long>blockingTransaction(c -> repo.claim(c, p, 1_000, day)), "first claim");
        assertEquals(day - 500, (long) db.<Long>blockingTransaction(c -> repo.claim(c, p, 1_500, day)), "cooldown running");
        assertEquals(1_000L, db.blocking(c -> repo.lastClaim(c, p)).orElseThrow(), "refused claim changed nothing");
        assertEquals(0, (long) db.<Long>blockingTransaction(c -> repo.claim(c, p, 1_000 + day, day)), "24 h later");
        db.blockingTransaction(c -> {
            repo.unclaim(c, p, 1_000L);
            return null;
        });
        assertEquals(1_000L, db.blocking(c -> repo.lastClaim(c, p)).orElseThrow());
        db.blockingTransaction(c -> {
            repo.unclaim(c, p, null);
            return null;
        });
        assertTrue(db.blocking(c -> repo.lastClaim(c, p)).isEmpty());
    }
}
