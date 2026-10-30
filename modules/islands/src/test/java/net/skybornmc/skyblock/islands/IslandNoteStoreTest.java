package net.skybornmc.skyblock.islands;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.DatabaseConfig;
import net.skybornmc.skyblock.common.db.Migrations;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Island notes against a real H2 database (MySQL mode): limits, done/pin rules, soft delete, disband, purge, book save. */
class IslandNoteStoreTest {

    @TempDir
    Path dir;
    private Database db;
    private long now = 1_000_000L;
    private UUID owner;
    private UUID island;

    @BeforeEach
    void start() throws Exception {
        DatabaseConfig config = new DatabaseConfig();
        config.type = DatabaseConfig.Type.H2;
        config.h2File = "notes-test";
        config.poolSize = 4;
        db = new Database(config, dir.toFile(), Logger.getLogger("test-db"));
        db.start();
        new Migrations(db, Logger.getLogger("test")).migrate("islands", getClass().getClassLoader());
        owner = UUID.randomUUID();
        IslandStore.CreateResult result = db.blocking(c -> IslandStore.createWithRetry(c, owner, 1, 3, "islands", now));
        island = assertInstanceOf(IslandStore.CreateResult.Created.class, result).island().id();
    }

    @AfterEach
    void stop() {
        db.close();
    }

    private Note note(String text) {
        return Note.create(UUID.randomUUID(), island, owner, text, ++now);
    }

    private IslandNoteStore.AddResult add(Note note, int max) {
        return db.blockingTransaction(c -> IslandNoteStore.add(c, note, max));
    }

    private boolean yes(net.skybornmc.skyblock.common.db.Sql.SqlFunction<Boolean> work) {
        return db.blockingTransaction(work);
    }

    private int count(net.skybornmc.skyblock.common.db.Sql.SqlFunction<Integer> work) {
        return db.blockingTransaction(work);
    }

    private List<Note> load() {
        return db.blocking(c -> IslandNoteStore.load(c, island));
    }

    @Test
    void addRespectsTheLimitAndDeletedNotesFreeSpace() {
        Note a = note("fix the farm");
        assertEquals(IslandNoteStore.AddResult.ADDED, add(a, 2));
        assertEquals(IslandNoteStore.AddResult.ADDED, add(note("build a bridge"), 2));
        assertEquals(IslandNoteStore.AddResult.LIMIT, add(note("third"), 2), "owner N6: max notes");
        assertEquals(2, load().size());
        Note stored = load().stream().filter(n -> n.id().equals(a.id())).findFirst().orElseThrow();
        assertEquals("fix the farm", stored.text());
        assertEquals(owner, stored.author(), "author = profile key");

        assertTrue(yes(c -> IslandNoteStore.delete(c, island, a.id(), owner, IslandNoteStore.REASON_DELETE, ++now)));
        assertEquals(IslandNoteStore.AddResult.ADDED, add(note("third"), 2), "a deleted note doesn't count");
        assertFalse(yes(c -> IslandNoteStore.delete(c, island, a.id(), owner, IslandNoteStore.REASON_DELETE, ++now)),
                "deleting twice does nothing");
    }

    @Test
    void addOnARemovedIslandFails() {
        db.blockingTransaction(c -> IslandStore.disband(c, island, owner, IslandStore.REASON_DISBAND, owner, ++now));
        assertEquals(IslandNoteStore.AddResult.ISLAND_GONE, add(note("too late"), 50));
    }

    @Test
    void editDoneAndReopen() {
        Note a = note("first");
        add(a, 50);
        UUID member = UUID.randomUUID();
        assertTrue(yes(c -> IslandNoteStore.edit(c, island, a.id(), "first, changed", member, ++now)));
        assertTrue(yes(c -> IslandNoteStore.setDone(c, island, a.id(), member, ++now)));
        assertFalse(yes(c -> IslandNoteStore.setDone(c, island, a.id(), member, ++now)), "already done");
        Note done = load().getFirst();
        assertEquals("first, changed", done.text());
        assertEquals(member, done.updatedBy());
        assertEquals(member, done.doneBy());
        assertTrue(done.done());
        assertTrue(yes(c -> IslandNoteStore.setDone(c, island, a.id(), null, ++now)));
        assertFalse(load().getFirst().done());
        assertFalse(yes(c -> IslandNoteStore.edit(c, island, UUID.randomUUID(), "x", member, ++now)), "unknown note");
    }

    @Test
    void pinLimitMoveAndDoneRemovesPin() {
        Note a = note("a");
        Note b = note("b");
        Note c2 = note("c");
        add(a, 50);
        add(b, 50);
        add(c2, 50);
        Note.Pin here = new Note.Pin("islands", 1.5, 100, 2.5);
        Note.Pin there = new Note.Pin("islands", 5.5, 101, 6.5);
        assertEquals(IslandNoteStore.PinResult.PINNED, db.blockingTransaction(c -> IslandNoteStore.pin(c, island, a.id(), here, 2, ++now)));
        assertEquals(IslandNoteStore.PinResult.PINNED, db.blockingTransaction(c -> IslandNoteStore.pin(c, island, b.id(), here, 2, ++now)));
        assertEquals(IslandNoteStore.PinResult.LIMIT, db.blockingTransaction(c -> IslandNoteStore.pin(c, island, c2.id(), here, 2, ++now)),
                "notes.max-holograms");
        assertEquals(IslandNoteStore.PinResult.MOVED, db.blockingTransaction(c -> IslandNoteStore.pin(c, island, a.id(), there, 2, ++now)),
                "moving a pin is always allowed");
        Note moved = load().stream().filter(n -> n.id().equals(a.id())).findFirst().orElseThrow();
        assertEquals(there, moved.pin());

        assertTrue(yes(c -> IslandNoteStore.setDone(c, island, a.id(), owner, ++now)));
        Note done = load().stream().filter(n -> n.id().equals(a.id())).findFirst().orElseThrow();
        assertNull(done.pin(), "owner N9: the hologram goes when the note is checked off");
        assertEquals(IslandNoteStore.PinResult.DONE, db.blockingTransaction(c -> IslandNoteStore.pin(c, island, a.id(), here, 2, ++now)));
        assertEquals(IslandNoteStore.PinResult.PINNED, db.blockingTransaction(c -> IslandNoteStore.pin(c, island, c2.id(), here, 2, ++now)),
                "the done note freed a pin slot");
        assertTrue(yes(c -> IslandNoteStore.unpin(c, island, c2.id(), ++now)));
        assertFalse(yes(c -> IslandNoteStore.unpin(c, island, c2.id(), ++now)));
        assertEquals(IslandNoteStore.PinResult.GONE,
                db.blockingTransaction(c -> IslandNoteStore.pin(c, island, UUID.randomUUID(), here, 2, ++now)));
    }

    @Test
    void notesOfAnotherIslandCantBeTouched() {
        Note a = note("mine");
        add(a, 50);
        UUID otherOwner = UUID.randomUUID();
        UUID other = assertInstanceOf(IslandStore.CreateResult.Created.class,
                db.blocking(c -> IslandStore.createWithRetry(c, otherOwner, 1, 3, "islands", now))).island().id();
        assertFalse(yes(c -> IslandNoteStore.edit(c, other, a.id(), "hacked", otherOwner, ++now)));
        assertFalse(yes(c -> IslandNoteStore.delete(c, other, a.id(), otherOwner, IslandNoteStore.REASON_DELETE, ++now)));
        assertEquals(IslandNoteStore.PinResult.GONE, db.blockingTransaction(c ->
                IslandNoteStore.pin(c, other, a.id(), new Note.Pin("islands", 0, 0, 0), 5, ++now)));
        assertEquals("mine", load().getFirst().text());
    }

    @Test
    void deletedNotesStayForStaffUntilPurged() {
        Note a = note("scam proof");
        Note b = note("done thing");
        add(a, 50);
        add(b, 50);
        db.blockingTransaction(c -> IslandNoteStore.setDone(c, island, b.id(), owner, ++now));
        long deletedAt = ++now;
        db.blockingTransaction(c -> IslandNoteStore.delete(c, island, a.id(), owner, IslandNoteStore.REASON_DELETE, deletedAt));
        List<UUID> cleared = db.blockingTransaction(c -> IslandNoteStore.clearDone(c, island, owner, ++now));
        assertEquals(List.of(b.id()), cleared);
        assertTrue(load().isEmpty());
        List<Note> deleted = db.blocking(c -> IslandNoteStore.deleted(c, island, 0));
        assertEquals(2, deleted.size());
        assertEquals(IslandNoteStore.REASON_CLEAR, deleted.getFirst().deletedReason(), "newest first");
        assertEquals("scam proof", deleted.get(1).text());
        assertEquals(owner, deleted.get(1).deletedBy());
        assertEquals(1, db.blocking(c -> IslandNoteStore.deleted(c, island, deletedAt + 1)).size(), "only since the cutoff");

        assertEquals(1, count(c -> IslandNoteStore.purge(c, deletedAt + 1)), "owner N7: removed after the keep time");
        assertEquals(1, db.blocking(c -> IslandNoteStore.deleted(c, island, 0)).size());
    }

    @Test
    void disbandingSoftDeletesEveryNote() {
        Note a = note("a");
        add(a, 50);
        db.blockingTransaction(c -> IslandNoteStore.pin(c, island, a.id(), new Note.Pin("islands", 0, 0, 0), 5, ++now));
        db.blockingTransaction(c -> IslandStore.disband(c, island, owner, IslandStore.REASON_DISBAND, owner, ++now));
        assertTrue(load().isEmpty());
        List<Note> deleted = db.blocking(c -> IslandNoteStore.deleted(c, island, 0));
        assertEquals(1, deleted.size());
        assertEquals(IslandNoteStore.REASON_DISBAND, deleted.getFirst().deletedReason());
        assertNull(deleted.getFirst().pin());
    }

    @Test
    void bookSaveEditsAndAddsUpToTheLimit() {
        Note a = note("one");
        Note gone = note("gone");
        add(a, 3);
        add(gone, 3);
        db.blockingTransaction(c -> IslandNoteStore.delete(c, island, gone.id(), owner, IslandNoteStore.REASON_DELETE, ++now));
        List<NoteRules.BookDiff.Edit> edits = List.of(new NoteRules.BookDiff.Edit(a.id(), "one!"),
                new NoteRules.BookDiff.Edit(gone.id(), "revived?"));
        List<Note> adds = new ArrayList<>(List.of(note("new 1"), note("new 2"), note("new 3")));
        IslandNoteStore.BookResult result = db.blockingTransaction(c -> IslandNoteStore.saveBook(c, island, edits, adds, owner, 3, ++now));
        assertEquals(List.of(a.id()), result.edited(), "a deleted note is not revived");
        assertEquals(2, result.added().size());
        assertEquals(1, result.overLimit());
        assertEquals(3, load().size());
    }
}
