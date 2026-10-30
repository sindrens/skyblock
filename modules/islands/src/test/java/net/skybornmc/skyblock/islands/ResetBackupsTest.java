package net.skybornmc.skyblock.islands;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.DatabaseConfig;
import net.skybornmc.skyblock.common.db.Migrations;
import net.skybornmc.skyblock.common.island.IslandService.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Owner answer 591: disband/reset backups (bank money, restore without membership, items never restored twice). */
class ResetBackupsTest {

    @TempDir
    Path dir;
    private Database db;
    private final long now = 1_000_000_000L;

    @BeforeEach
    void start() throws Exception {
        DatabaseConfig config = new DatabaseConfig();
        config.type = DatabaseConfig.Type.H2;
        config.h2File = "reset-test";
        config.poolSize = 2;
        db = new Database(config, dir.toFile(), Logger.getLogger("test-db"));
        db.start();
        new Migrations(db, Logger.getLogger("test")).migrate("islands", getClass().getClassLoader());
    }

    @AfterEach
    void stop() {
        db.close();
    }

    private long save(String removal, Long bank, byte[] items) {
        UUID profile = UUID.randomUUID();
        return db.blocking(c -> KickBackupStore.save(c, new KickBackupStore.NewBackup(UUID.randomUUID(), "Island", profile, profile, "Alex",
                Role.LEADER, 500L, items, items == null ? null : new byte[]{7}, removal, "Steve", now, bank)));
    }

    private KickBackupStore.Backup get(long id) {
        return db.blocking(c -> KickBackupStore.get(c, id)).orElseThrow();
    }

    @Test
    void disbandBackupKeepsTheBankMoneyAndRestoresOnceWithoutMembership() {
        long id = save(KickBackupStore.REMOVAL_DISBAND, 12_000L, new byte[]{1, 2});
        KickBackupStore.Backup backup = get(id);
        assertTrue(backup.islandRemoval());
        assertEquals(12_000L, backup.bankMoney());
        assertTrue(backup.open());
        assertTrue(db.<Boolean>blocking(c -> KickBackupStore.claimRemoval(c, id, "Admin", now + 1)));
        assertFalse(db.<Boolean>blocking(c -> KickBackupStore.claimRemoval(c, id, "Admin", now + 2)), "at most once");
        assertFalse(get(id).open());
        assertTrue(get(id).itemsOpen(), "items are given back separately");
    }

    @Test
    void kickBackupsAreNotClaimedAsRemovals() {
        long id = save(MemberStore.LEFT_KICK, null, null);
        assertFalse(get(id).islandRemoval());
        assertNull(get(id).bankMoney());
        assertFalse(db.<Boolean>blocking(c -> KickBackupStore.claimRemoval(c, id, "Admin", now)), "kicks restore with membership");
    }

    @Test
    void itemsTheResetCouldNotTakeAreNeverRestored() {
        long id = save(KickBackupStore.REMOVAL_RESET, null, new byte[]{1});
        assertTrue(get(id).itemsOpen());
        assertTrue(db.<Boolean>blocking(c -> KickBackupStore.dropItems(c, id)));
        KickBackupStore.Backup dropped = get(id);
        assertFalse(dropped.itemsOpen(), "the player kept them, so a restore must not give them twice");
        assertNull(dropped.inventory());
    }

    @Test
    void pureRules() {
        assertEquals(KickBackupStore.REMOVAL_DISBAND, KickBackups.removalOf(IslandStore.REASON_DISBAND));
        assertEquals(KickBackupStore.REMOVAL_RESET, KickBackups.removalOf(IslandStore.REASON_RESET));
        assertEquals(KickBackupStore.REMOVAL_RESET, KickBackups.removalOf(IslandStore.REASON_DELETE));
        assertEquals(500, KickBackups.resetAmount(500L, 800), "never more than was backed up");
        assertEquals(300, KickBackups.resetAmount(500L, 300), "never more than the profile has");
        assertEquals(0, KickBackups.resetAmount(null, 300));
        assertEquals(0, KickBackups.resetAmount(500L, -5));
        IslandsConfig config = new IslandsConfig();
        assertTrue(config.resetsProfiles(IslandStore.REASON_DISBAND));
        assertTrue(config.resetsProfiles(IslandStore.REASON_RESET));
        assertFalse(config.resetsProfiles(IslandStore.REASON_DELETE), "a staff delete keeps profiles by default");
    }
}
