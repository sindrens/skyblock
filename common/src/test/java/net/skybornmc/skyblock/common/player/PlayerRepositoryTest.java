package net.skybornmc.skyblock.common.player;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.H2;
import net.skybornmc.skyblock.common.db.Migrations;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerRepositoryTest {

    private final PlayerRepository repo = new PlayerRepository();

    private Database start(Path dir) throws Exception {
        Database db = H2.start(dir);
        new Migrations(db, Logger.getLogger("test")).migrate("core", getClass().getClassLoader());
        return db;
    }

    @Test
    void createsThenLoadsAndTracksNameChanges(@TempDir Path dir) throws Exception {
        try (Database db = start(dir)) {
            UUID uuid = UUID.randomUUID();
            PlayerRepository.Loaded first = db.blockingTransaction(c -> repo.loadOrCreate(c, uuid, "Vanzu_", 1000));
            assertTrue(first.isNew());
            assertNull(first.previousName());

            PlayerRepository.Loaded second = db.blockingTransaction(c -> repo.loadOrCreate(c, uuid, "Vanzu", 2000));
            assertFalse(second.isNew());
            assertEquals("Vanzu_", second.previousName());
            assertEquals(1000, second.snapshot().firstSeen());

            List<PlayerRepository.NameRecord> history = db.blocking(c -> repo.nameHistory(c, uuid));
            assertEquals(List.of("Vanzu_", "Vanzu"), history.stream().map(PlayerRepository.NameRecord::name).toList());
            assertTrue(db.blocking(c -> repo.findByName(c, "vanzu")).isPresent());
        }
    }

    @Test
    void savesPlaytimeSettingsAndBedrock(@TempDir Path dir) throws Exception {
        try (Database db = start(dir)) {
            UUID uuid = UUID.randomUUID();
            PlayerRepository.Loaded loaded = db.blockingTransaction(c -> repo.loadOrCreate(c, uuid, ".BedrockGuy", 1000));
            PlayerData data = new PlayerData(loaded.snapshot(), loaded.isNew());
            data.bedrock(true);
            data.startSession(1000);
            data.setSetting("chat", "off");
            data.endSession(61_000);
            db.blocking(c -> {
                repo.save(c, List.of(data.snapshot()));
                return null;
            });
            PlayerData.Snapshot back = db.blocking(c -> repo.findByUuid(c, uuid)).orElseThrow();
            assertEquals(60_000, back.playtimeMillis());
            assertTrue(back.bedrock());
            assertEquals(Map.of("chat", "off"), back.settings());
            assertEquals(List.of(".BedrockGuy"), db.blocking(c -> repo.recentNames(c, 10)));
        }
    }
}
