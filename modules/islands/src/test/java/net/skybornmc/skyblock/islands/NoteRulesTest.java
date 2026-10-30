package net.skybornmc.skyblock.islands;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Island notes (NOTES): rights, text cleaning and limits, order, next note, wrapping and the book diff. */
class NoteRulesTest {

    private static final UUID ISLAND = UUID.randomUUID();
    private static final UUID AUTHOR = UUID.randomUUID();

    private static Note note(String text, long created) {
        return Note.create(UUID.randomUUID(), ISLAND, AUTHOR, text, created);
    }

    @Test
    void rightsFollowTheThreeRolePermissions() {
        assertTrue(NoteRules.canRead(true, false, false));
        assertTrue(NoteRules.canRead(false, true, false), "writing implies reading");
        assertFalse(NoteRules.canRead(false, false, false));
        assertTrue(NoteRules.canAdd(true));
        assertFalse(NoteRules.canAdd(false), "coop players read only (owner N1)");
        assertTrue(NoteRules.canTick(true, false), "members tick any note");
        assertFalse(NoteRules.canTick(false, false));
        assertTrue(NoteRules.canChange(true, true, false), "own note with write");
        assertFalse(NoteRules.canChange(false, true, false), "someone else's note needs manage");
        assertTrue(NoteRules.canChange(false, false, true), "manage changes any note");
        assertFalse(NoteRules.canChange(true, false, false), "author without write (e.g. demoted to coop) can't");
        assertTrue(NoteRules.canClearDone(true));
        assertFalse(NoteRules.canClearDone(false));
    }

    @Test
    void rightsRecordCombinesStaffAndRole() {
        IslandNotes.Rights coop = new IslandNotes.Rights(true, false, false, false);
        assertTrue(coop.canView());
        assertFalse(coop.canAdd());
        assertFalse(coop.canTick());
        assertFalse(coop.canChange(true));
        IslandNotes.Rights staff = new IslandNotes.Rights(false, false, false, true);
        assertTrue(staff.canView(), "staff can read any island's notes");
        assertFalse(staff.canAdd(), "but staff never write on other islands");
        IslandNotes.Rights member = new IslandNotes.Rights(true, true, false, false);
        assertTrue(member.canAdd());
        assertTrue(member.canTick());
        assertTrue(member.canChange(true));
        assertFalse(member.canChange(false));
        assertFalse(member.canClearDone());
        assertFalse(NoteActionsMenu.hasActions(coop, true, note("x", 1)));
        assertTrue(NoteActionsMenu.hasActions(member, false, note("x", 1)), "tick");
    }

    @Test
    void cleanRemovesColorsControlCharactersAndExtraSpaces() {
        assertEquals("fix the farm", NoteRules.clean("  fix   the\n§cfarm\t "));
        assertEquals("", NoteRules.clean(null));
        assertEquals("", NoteRules.clean(" \n\t "));
        assertEquals("a b", NoteRules.clean("a\u0000\u0007b"));
        assertEquals("<red>hi</red>", NoteRules.clean("<red>hi</red>"), "tags stay text; they are never parsed");
    }

    @Test
    void lengthLimit() {
        assertEquals(NoteRules.TextCheck.EMPTY, NoteRules.check("", 200));
        assertEquals(NoteRules.TextCheck.OK, NoteRules.check("x".repeat(200), 200));
        assertEquals(NoteRules.TextCheck.TOO_LONG, NoteRules.check("x".repeat(201), 200));
    }

    @Test
    void shortenAndWrap() {
        assertEquals("hello", NoteRules.shorten("hello", 10));
        assertEquals("hello w...", NoteRules.shorten("hello world again", 10));
        assertEquals(List.of("build a big", "farm next to", "the spawner"), NoteRules.wrap("build a big farm next to the spawner", 12));
        assertEquals(List.of("aaaa", "aaaa", "aa b"), NoteRules.wrap("aaaaaaaaaa b", 4));
        assertEquals(List.of(), NoteRules.wrap("   ", 10));
        for (String line : NoteRules.wrap("x".repeat(200), 32)) {
            assertTrue(line.length() <= 32);
        }
    }

    @Test
    void openNotesNewestFirstThenDoneByTickTime() {
        Note old = note("old", 10);
        Note young = note("young", 20);
        Note doneEarly = note("done early", 5).withDone(AUTHOR, 30);
        Note doneLate = note("done late", 6).withDone(AUTHOR, 40);
        assertEquals(List.of(young, old, doneLate, doneEarly), NoteRules.order(List.of(doneEarly, old, doneLate, young)));
        assertEquals(young, NoteRules.next(List.of(old, doneLate, young)));
        assertNull(NoteRules.next(List.of(doneEarly)));
        assertEquals(2, NoteRules.openCount(List.of(old, young, doneEarly)));
    }

    @Test
    void tickingDoneRemovesThePin() {
        Note pinned = note("farm", 1).withPin(new Note.Pin("islands", 10.5, 100, -3.5), 2);
        assertTrue(pinned.pinned());
        assertEquals(1, NoteRules.pinCount(List.of(pinned)));
        Note done = pinned.withDone(AUTHOR, 3);
        assertTrue(done.done());
        assertFalse(done.pinned(), "owner N9: removed when checked off");
        Note open = done.withDone(null, 4);
        assertFalse(open.done());
        assertEquals(0, new Note.Pin("islands", 15.9, 0, 16.0).chunkX());
        assertEquals(1, new Note.Pin("islands", 15.9, 0, 16.0).chunkZ());
        assertEquals(-1, new Note.Pin("islands", -0.5, 0, 0).chunkX());
    }

    @Test
    void bookDiffFindsEditsAndNewPagesAndIgnoresEmptiedPages() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        NoteRules.BookDiff diff = NoteRules.diff(List.of(a, b, c), List.of("one", "two", "three"),
                List.of("one", "two changed", "", "new note", "  ", "another"));
        assertEquals(List.of(new NoteRules.BookDiff.Edit(b, "two changed")), diff.edits());
        assertEquals(List.of("new note", "another"), diff.added());
        assertEquals(1, diff.emptied(), "page c was emptied: ignored, never a delete");
        assertFalse(diff.isEmpty());

        NoteRules.BookDiff same = NoteRules.diff(List.of(a), List.of("one"), List.of(" one ", ""));
        assertTrue(same.isEmpty(), "whitespace only changes nothing");

        NoteRules.BookDiff shorter = NoteRules.diff(List.of(a, b), List.of("one", "two"), List.of("one"));
        assertTrue(shorter.isEmpty(), "missing pages are not deletes either");
    }
}
