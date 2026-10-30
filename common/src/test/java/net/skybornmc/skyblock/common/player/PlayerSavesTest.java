package net.skybornmc.skyblock.common.player;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** SECFIX1 F-01: the player file is written before the database, and a failed save means no database write at all. */
class PlayerSavesTest {

    @Test
    void savesBeforeTheDatabaseWriteStarts() {
        List<String> order = new ArrayList<>();
        CompletableFuture<String> f = PlayerSaves.commitAfterSave(() -> {
            order.add("save");
            return true;
        }, "test", () -> {
            order.add("commit");
            return CompletableFuture.completedFuture("ok");
        });
        assertEquals("ok", f.join());
        assertEquals(List.of("save", "commit"), order);
    }

    @Test
    void aFailedSaveSkipsTheDatabase() {
        List<String> order = new ArrayList<>();
        CompletableFuture<String> f = PlayerSaves.commitAfterSave(() -> false, "test", () -> {
            order.add("commit");
            return CompletableFuture.completedFuture("ok");
        });
        CompletionException e = assertThrows(CompletionException.class, f::join);
        assertInstanceOf(PlayerSaves.SaveFailed.class, e.getCause());
        assertTrue(order.isEmpty(), "the database must not be written next to an unsaved removal");
    }

    @Test
    void aThrowingSaveOrCommitBecomesAFailedFuture() {
        CompletableFuture<String> saveThrows = PlayerSaves.commitAfterSave(() -> {
            throw new IllegalStateException("disk");
        }, "test", () -> CompletableFuture.completedFuture("ok"));
        assertInstanceOf(PlayerSaves.SaveFailed.class, assertThrows(CompletionException.class, saveThrows::join).getCause());
        CompletableFuture<String> commitThrows = PlayerSaves.commitAfterSave(() -> true, "test", () -> {
            throw new IllegalStateException("db stopped");
        });
        assertInstanceOf(IllegalStateException.class, assertThrows(CompletionException.class, commitThrows::join).getCause());
    }

    @Test
    void offlinePlayersCountAsSaved() {
        assertTrue(PlayerSaves.save(null, "test"));
    }
}
