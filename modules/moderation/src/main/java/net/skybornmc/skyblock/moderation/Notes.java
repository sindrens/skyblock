package net.skybornmc.skyblock.moderation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Staff notes on a player (spec 12.5): rules and the row type. Pure. Notes are only ever shown to staff. */
final class Notes {

    /** One row of {@code staff_notes}. Removed notes stay in the database ({@code removed}, who and when). */
    record Note(long id, UUID target, String targetName, UUID author, String authorName, String text, long created,
                boolean removed, Long removedAt, UUID removedBy, String removedByName) {
    }

    enum Problem { EMPTY, TOO_LONG }

    private Notes() {
    }

    /** The text as stored: one line, single spaces, no control characters. */
    static String clean(String raw) {
        return raw == null ? "" : Evidence.clean(raw, Integer.MAX_VALUE).replaceAll("\\s+", " ");
    }

    /** Why a cleaned text can't be saved, if anything. */
    static Optional<Problem> check(String cleaned, int maxLength) {
        if (cleaned.isEmpty()) {
            return Optional.of(Problem.EMPTY);
        }
        if (cleaned.length() > maxLength) {
            return Optional.of(Problem.TOO_LONG);
        }
        return Optional.empty();
    }

    /** Text for the /p head: cut to {@code max} characters ("..." included). */
    static String cut(String text, int max) {
        return Evidence.cut(text, max);
    }

    /** Active (not removed) notes, newest first. */
    static List<Note> active(List<Note> all) {
        List<Note> out = new ArrayList<>();
        for (Note n : all) {
            if (!n.removed()) {
                out.add(n);
            }
        }
        out.sort(Comparator.comparingLong(Note::created).thenComparingLong(Note::id).reversed());
        return out;
    }

    /** The newest {@code n} active notes. */
    static List<Note> latest(List<Note> all, int n) {
        List<Note> active = active(all);
        return List.copyOf(active.subList(0, Math.min(Math.max(0, n), active.size())));
    }
}
