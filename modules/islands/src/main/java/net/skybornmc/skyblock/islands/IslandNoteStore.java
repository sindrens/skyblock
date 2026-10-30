package net.skybornmc.skyblock.islands;

import net.skybornmc.skyblock.common.db.Database;
import net.skybornmc.skyblock.common.db.Sql;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Island notes in the database ({@code island_notes}, NOTES). Every write is one transaction; adding and pinning lock the
 * island row first ({@code SELECT … FOR UPDATE}) so the note and hologram limits hold against double clicks and several
 * servers. Deleting is soft (staff read deleted notes for {@code notes.deleted-keep-days}, owner N7); {@link #purge}
 * removes them for good. The static methods take a connection so tests run them against H2.
 */
final class IslandNoteStore {

    /** Result of {@link #add}. */
    enum AddResult { ADDED, LIMIT, ISLAND_GONE }

    /** Result of {@link #pin}. */
    enum PinResult { PINNED, MOVED, LIMIT, DONE, GONE }

    /** Result of {@link #saveBook}: the notes changed and added, and new pages skipped because the island was full. */
    record BookResult(List<UUID> edited, List<Note> added, int overLimit) {
    }

    static final String REASON_DELETE = "delete";
    static final String REASON_CLEAR = "clear-done";
    static final String REASON_DISBAND = "disband";

    private static final String COLUMNS = "id, island_id, author, note_text, created_at, updated_at, updated_by, done_by, done_at, "
            + "pin_world, pin_x, pin_y, pin_z, deleted_at, deleted_by, deleted_reason";

    private final Database db;

    IslandNoteStore(Database db) {
        this.db = db;
    }

    // ------------------------------------------------------------------ async wrappers

    CompletableFuture<List<Note>> load(UUID islandId) {
        return db.async(c -> load(c, islandId));
    }

    CompletableFuture<List<Note>> deleted(UUID islandId, long since) {
        return db.async(c -> deleted(c, islandId, since));
    }

    CompletableFuture<AddResult> add(Note note, int maxNotes) {
        return db.transaction(c -> add(c, note, maxNotes));
    }

    CompletableFuture<Boolean> edit(UUID islandId, UUID noteId, String text, UUID by, long now) {
        return db.transaction(c -> edit(c, islandId, noteId, text, by, now));
    }

    CompletableFuture<Boolean> setDone(UUID islandId, UUID noteId, UUID by, long now) {
        return db.transaction(c -> setDone(c, islandId, noteId, by, now));
    }

    CompletableFuture<PinResult> pin(UUID islandId, UUID noteId, Note.Pin pin, int maxPins, long now) {
        return db.transaction(c -> pin(c, islandId, noteId, pin, maxPins, now));
    }

    CompletableFuture<Boolean> unpin(UUID islandId, UUID noteId, long now) {
        return db.transaction(c -> unpin(c, islandId, noteId, now));
    }

    CompletableFuture<Boolean> delete(UUID islandId, UUID noteId, UUID by, String reason, long now) {
        return db.transaction(c -> delete(c, islandId, noteId, by, reason, now));
    }

    CompletableFuture<List<UUID>> clearDone(UUID islandId, UUID by, long now) {
        return db.transaction(c -> clearDone(c, islandId, by, now));
    }

    CompletableFuture<BookResult> saveBook(UUID islandId, List<NoteRules.BookDiff.Edit> edits, List<Note> adds, UUID by, int maxNotes,
                                           long now) {
        return db.transaction(c -> saveBook(c, islandId, edits, adds, by, maxNotes, now));
    }

    CompletableFuture<Integer> purge(long before) {
        return db.transaction(c -> purge(c, before));
    }

    // ------------------------------------------------------------------ SQL (static, testable)

    /** The island's notes that are not deleted. */
    static List<Note> load(Connection c, UUID islandId) throws SQLException {
        return Sql.query(c, "SELECT " + COLUMNS + " FROM island_notes WHERE island_id = ? AND deleted_at IS NULL ORDER BY created_at",
                IslandNoteStore::row, islandId);
    }

    /** The island's notes deleted at or after {@code since}, newest first (staff view, N7). */
    static List<Note> deleted(Connection c, UUID islandId, long since) throws SQLException {
        return Sql.query(c, "SELECT " + COLUMNS + " FROM island_notes WHERE island_id = ? AND deleted_at IS NOT NULL AND deleted_at >= ? "
                + "ORDER BY deleted_at DESC", IslandNoteStore::row, islandId, since);
    }

    /** True if the island exists and is not deleted; locks its row until the transaction ends. */
    private static boolean lockIsland(Connection c, UUID islandId) throws SQLException {
        return Sql.queryOne(c, "SELECT 1 FROM islands WHERE id = ? AND deleted_at IS NULL FOR UPDATE", rs -> Boolean.TRUE, islandId)
                .isPresent();
    }

    static AddResult add(Connection c, Note note, int maxNotes) throws SQLException {
        if (!lockIsland(c, note.islandId())) {
            return AddResult.ISLAND_GONE;
        }
        int count = Sql.queryOne(c, "SELECT COUNT(*) FROM island_notes WHERE island_id = ? AND deleted_at IS NULL",
                rs -> rs.getInt(1), note.islandId()).orElse(0);
        if (count >= maxNotes) {
            return AddResult.LIMIT;
        }
        Sql.update(c, "INSERT INTO island_notes (id, island_id, author, note_text, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
                note.id(), note.islandId(), note.author(), note.text(), note.createdAt(), note.updatedAt());
        return AddResult.ADDED;
    }

    static boolean edit(Connection c, UUID islandId, UUID noteId, String text, UUID by, long now) throws SQLException {
        return Sql.update(c, "UPDATE island_notes SET note_text = ?, updated_at = ?, updated_by = ? "
                + "WHERE id = ? AND island_id = ? AND deleted_at IS NULL", text, now, by, noteId, islandId) == 1;
    }

    /** Ticks a note done ({@code by} = who; also removes its pin, owner N9) or opens it again ({@code by} = null). */
    static boolean setDone(Connection c, UUID islandId, UUID noteId, UUID by, long now) throws SQLException {
        if (by == null) {
            return Sql.update(c, "UPDATE island_notes SET done_by = NULL, done_at = NULL, updated_at = ? "
                    + "WHERE id = ? AND island_id = ? AND deleted_at IS NULL AND done_at IS NOT NULL", now, noteId, islandId) == 1;
        }
        return Sql.update(c, "UPDATE island_notes SET done_by = ?, done_at = ?, updated_at = ?, pin_world = NULL, pin_x = NULL, "
                + "pin_y = NULL, pin_z = NULL WHERE id = ? AND island_id = ? AND deleted_at IS NULL AND done_at IS NULL",
                by, now, now, noteId, islandId) == 1;
    }

    static PinResult pin(Connection c, UUID islandId, UUID noteId, Note.Pin pin, int maxPins, long now) throws SQLException {
        if (!lockIsland(c, islandId)) {
            return PinResult.GONE;
        }
        Optional<Object[]> row = Sql.queryOne(c, "SELECT done_at, pin_world FROM island_notes WHERE id = ? AND island_id = ? "
                + "AND deleted_at IS NULL FOR UPDATE", rs -> new Object[]{Sql.nullableLong(rs, "done_at"), rs.getString("pin_world")},
                noteId, islandId);
        if (row.isEmpty()) {
            return PinResult.GONE;
        }
        if (row.get()[0] != null) {
            return PinResult.DONE;
        }
        boolean moving = row.get()[1] != null;
        if (!moving) {
            int pins = Sql.queryOne(c, "SELECT COUNT(*) FROM island_notes WHERE island_id = ? AND deleted_at IS NULL "
                    + "AND pin_world IS NOT NULL", rs -> rs.getInt(1), islandId).orElse(0);
            if (pins >= maxPins) {
                return PinResult.LIMIT;
            }
        }
        Sql.update(c, "UPDATE island_notes SET pin_world = ?, pin_x = ?, pin_y = ?, pin_z = ?, updated_at = ? WHERE id = ?",
                pin.world(), pin.x(), pin.y(), pin.z(), now, noteId);
        return moving ? PinResult.MOVED : PinResult.PINNED;
    }

    static boolean unpin(Connection c, UUID islandId, UUID noteId, long now) throws SQLException {
        return Sql.update(c, "UPDATE island_notes SET pin_world = NULL, pin_x = NULL, pin_y = NULL, pin_z = NULL, updated_at = ? "
                + "WHERE id = ? AND island_id = ? AND deleted_at IS NULL AND pin_world IS NOT NULL", now, noteId, islandId) == 1;
    }

    /** Soft delete (kept for staff); also removes the pin. */
    static boolean delete(Connection c, UUID islandId, UUID noteId, UUID by, String reason, long now) throws SQLException {
        return Sql.update(c, "UPDATE island_notes SET deleted_at = ?, deleted_by = ?, deleted_reason = ?, pin_world = NULL, "
                + "pin_x = NULL, pin_y = NULL, pin_z = NULL WHERE id = ? AND island_id = ? AND deleted_at IS NULL",
                now, by, reason, noteId, islandId) == 1;
    }

    /** Soft-deletes every done note of the island; returns their ids. */
    static List<UUID> clearDone(Connection c, UUID islandId, UUID by, long now) throws SQLException {
        List<UUID> ids = Sql.query(c, "SELECT id FROM island_notes WHERE island_id = ? AND deleted_at IS NULL AND done_at IS NOT NULL "
                + "FOR UPDATE", rs -> Sql.uuid(rs, "id"), islandId);
        if (!ids.isEmpty()) {
            Sql.update(c, "UPDATE island_notes SET deleted_at = ?, deleted_by = ?, deleted_reason = ?, pin_world = NULL, pin_x = NULL, "
                    + "pin_y = NULL, pin_z = NULL WHERE island_id = ? AND deleted_at IS NULL AND done_at IS NOT NULL",
                    now, by, REASON_CLEAR, islandId);
        }
        return ids;
    }

    /** With the island (disband, staff delete, reset): every note is soft-deleted, so staff can still read them (N7). */
    static int deleteIsland(Connection c, UUID islandId, UUID by, long now) throws SQLException {
        return Sql.update(c, "UPDATE island_notes SET deleted_at = ?, deleted_by = ?, deleted_reason = ?, pin_world = NULL, pin_x = NULL, "
                + "pin_y = NULL, pin_z = NULL WHERE island_id = ? AND deleted_at IS NULL", now, by, REASON_DISBAND, islandId);
    }

    /**
     * The temporary book (N3) in one transaction: the island row is locked, changed pages update their notes (skipped when
     * a note was deleted meanwhile), new pages are added until the island has {@code maxNotes}.
     */
    static BookResult saveBook(Connection c, UUID islandId, List<NoteRules.BookDiff.Edit> edits, List<Note> adds, UUID by, int maxNotes,
                               long now) throws SQLException {
        if (!lockIsland(c, islandId)) {
            return new BookResult(List.of(), List.of(), adds.size());
        }
        List<UUID> edited = new java.util.ArrayList<>();
        for (NoteRules.BookDiff.Edit edit : edits) {
            if (edit(c, islandId, edit.note(), edit.text(), by, now)) {
                edited.add(edit.note());
            }
        }
        int count = Sql.queryOne(c, "SELECT COUNT(*) FROM island_notes WHERE island_id = ? AND deleted_at IS NULL",
                rs -> rs.getInt(1), islandId).orElse(0);
        List<Note> added = new java.util.ArrayList<>();
        int over = 0;
        for (Note note : adds) {
            if (count >= maxNotes) {
                over++;
                continue;
            }
            Sql.update(c, "INSERT INTO island_notes (id, island_id, author, note_text, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
                    note.id(), islandId, note.author(), note.text(), note.createdAt(), note.updatedAt());
            added.add(note);
            count++;
        }
        return new BookResult(List.copyOf(edited), List.copyOf(added), over);
    }

    /** Removes notes deleted before {@code before} for good. */
    static int purge(Connection c, long before) throws SQLException {
        return Sql.update(c, "DELETE FROM island_notes WHERE deleted_at IS NOT NULL AND deleted_at < ?", before);
    }

    private static Note row(ResultSet rs) throws SQLException {
        String world = rs.getString("pin_world");
        Note.Pin pin = world == null ? null : new Note.Pin(world, rs.getDouble("pin_x"), rs.getDouble("pin_y"), rs.getDouble("pin_z"));
        return new Note(Sql.uuid(rs, "id"), Sql.uuid(rs, "island_id"), Sql.uuid(rs, "author"), rs.getString("note_text"),
                rs.getLong("created_at"), rs.getLong("updated_at"), Sql.uuid(rs, "updated_by"), Sql.uuid(rs, "done_by"),
                Sql.nullableLong(rs, "done_at"), pin, Sql.nullableLong(rs, "deleted_at"), Sql.uuid(rs, "deleted_by"),
                rs.getString("deleted_reason"));
    }
}
