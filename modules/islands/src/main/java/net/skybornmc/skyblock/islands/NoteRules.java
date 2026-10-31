package net.skybornmc.skyblock.islands;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Pure rules of the island notes (NOTES, owner N1-N9; no Bukkit, unit tested): who may do what, text cleaning and limits,
 * menu order, the newest open note, lore wrapping and the temporary book diff.
 *
 * <p>Rights come from three role permissions of the island matrix ({@link IslandPerm#NOTES_READ},
 * {@link IslandPerm#NOTES_WRITE}, {@link IslandPerm#NOTES_MANAGE}; the leader has all of them):</p>
 * <ul>
 *   <li>read: see the notes (default Admin, Mod, Member, Coop);</li>
 *   <li>write: add notes, tick any note done or open again, change/pin/unpin/delete your own (default Admin, Mod, Member);</li>
 *   <li>manage: change/pin/unpin/delete anyone's notes and clear all done notes (default Admin, Mod).</li>
 * </ul>
 */
final class NoteRules {

    /** Result of {@link #check}. */
    enum TextCheck { OK, EMPTY, TOO_LONG }

    /** What saving the temporary book changes: edited notes, new notes and pages that were emptied (ignored). */
    record BookDiff(List<Edit> edits, List<String> added, int emptied) {

        /** A page whose text changed. */
        record Edit(UUID note, String text) {
        }

        boolean isEmpty() {
            return edits.isEmpty() && added.isEmpty();
        }
    }

    private NoteRules() {
    }

    // ------------------------------------------------------------------ rights

    static boolean canRead(boolean read, boolean write, boolean manage) {
        return read || write || manage;
    }

    static boolean canAdd(boolean write) {
        return write;
    }

    /** Ticking done / opening again: anyone who may write (a shared to-do list). */
    static boolean canTick(boolean write, boolean manage) {
        return write || manage;
    }

    /** Change the text, pin, unpin or delete: your own note with write, anyone's with manage. */
    static boolean canChange(boolean author, boolean write, boolean manage) {
        return manage || (author && write);
    }

    static boolean canClearDone(boolean manage) {
        return manage;
    }

    // ------------------------------------------------------------------ text

    /**
     * Makes player text safe to store and show: no section signs with their legacy color code, no control characters (line breaks
     * become spaces), runs of spaces collapsed, trimmed. Null becomes empty.
     */
    static String clean(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(text.length());
        boolean space = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§') {
                if (i + 1 < text.length() && "0123456789abcdefklmnorxABCDEFKLMNORX".indexOf(text.charAt(i + 1)) >= 0) {
                    i++; // the legacy color code after it goes too
                }
                continue;
            }
            if (Character.isWhitespace(c) || Character.isISOControl(c)) {
                space = sb.length() > 0;
                continue;
            }
            if (net.skybornmc.skyblock.common.pack.PrivateUse.isPrivateUse(c)) {
                continue; // pack glyphs (badges, icons) can't be typed
            }
            if (space) {
                sb.append(' ');
                space = false;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /** Checks cleaned text against the length limit. */
    static TextCheck check(String cleaned, int maxLength) {
        if (cleaned == null || cleaned.isEmpty()) {
            return TextCheck.EMPTY;
        }
        return cleaned.length() > maxLength ? TextCheck.TOO_LONG : TextCheck.OK;
    }

    /** {@code text} cut to {@code max} characters, ending with "..." when cut. */
    static String shorten(String text, int max) {
        if (text == null) {
            return "";
        }
        if (text.length() <= max) {
            return text;
        }
        if (max <= 3) {
            return text.substring(0, Math.max(0, max));
        }
        return text.substring(0, max - 3).stripTrailing() + "...";
    }

    /** Word-wraps {@code text} into lines of at most {@code width} characters (long words are split). */
    static List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        int w = Math.max(4, width);
        StringBuilder line = new StringBuilder();
        for (String word : clean(text).split(" ")) {
            if (word.isEmpty()) {
                continue;
            }
            while (word.length() > w) {
                if (!line.isEmpty()) {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                lines.add(word.substring(0, w));
                word = word.substring(w);
            }
            if (line.isEmpty()) {
                line.append(word);
            } else if (line.length() + 1 + word.length() <= w) {
                line.append(' ').append(word);
            } else {
                lines.add(line.toString());
                line.setLength(0);
                line.append(word);
            }
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }

    // ------------------------------------------------------------------ order

    /** Menu and book order: open notes newest first, then done notes, the most recently ticked first. */
    static List<Note> order(List<Note> notes) {
        List<Note> list = new ArrayList<>(notes);
        list.sort(Comparator.comparing(Note::done)
                .thenComparing((Note n) -> n.done() ? n.doneAt() : n.createdAt(), Comparator.reverseOrder())
                .thenComparing(n -> n.id().toString()));
        return list;
    }

    /** The newest open note (scoreboard "Next: ..."), or null. */
    static Note next(List<Note> notes) {
        Note best = null;
        for (Note note : notes) {
            if (!note.done() && !note.deleted() && (best == null || note.createdAt() > best.createdAt())) {
                best = note;
            }
        }
        return best;
    }

    static int openCount(List<Note> notes) {
        int count = 0;
        for (Note note : notes) {
            if (!note.done()) {
                count++;
            }
        }
        return count;
    }

    static int pinCount(List<Note> notes) {
        int count = 0;
        for (Note note : notes) {
            if (note.pinned()) {
                count++;
            }
        }
        return count;
    }

    // ------------------------------------------------------------------ temporary book (N3)

    /**
     * Compares the saved book with what it showed: page {@code i < ids.size()} belongs to note {@code ids[i]} (shown with
     * {@code originals[i]}); a changed, non-empty page is an edit, an emptied page is ignored (deleting is done in the
     * menu, so a slip can't wipe notes), every non-empty page after them is a new note. Pages are {@link #clean}ed.
     */
    static BookDiff diff(List<UUID> ids, List<String> originals, List<String> pages) {
        List<BookDiff.Edit> edits = new ArrayList<>();
        List<String> added = new ArrayList<>();
        int emptied = 0;
        for (int i = 0; i < pages.size(); i++) {
            String page = clean(pages.get(i));
            if (i < ids.size()) {
                String original = i < originals.size() ? clean(originals.get(i)) : "";
                if (page.isEmpty()) {
                    if (!original.isEmpty()) {
                        emptied++;
                    }
                } else if (!page.equals(original)) {
                    edits.add(new BookDiff.Edit(ids.get(i), page));
                }
            } else if (!page.isEmpty()) {
                added.add(page);
            }
        }
        return new BookDiff(List.copyOf(edits), List.copyOf(added), emptied);
    }
}
