package net.skybornmc.skyblock.moderation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Watchlist (spec 13.5), pure parts. A watched player joining sends one line to staff with {@code skyblock.staff.notify};
 * the player is never told (also when the watched player is a staff member with that permission themselves).
 */
final class Watchlist {

    private Watchlist() {
    }

    /** One row of {@code watchlist}. {@code expires == null} = until removed. */
    record Entry(long id, UUID target, String targetName, UUID author, String authorName, String reason, long created,
                 Long expires, boolean removed, Long removedAt, String removedByName) {

        boolean active(long now) {
            return !removed && (expires == null || expires > now);
        }
    }

    /** An online player who could get the join line; {@code notified} = has skyblock.staff.notify. */
    record Viewer(UUID id, boolean notified) {
    }

    /** Who gets the "Watching: ..." line when {@code joined} joins: staff with notify, never the watched player. */
    static List<UUID> recipients(List<Viewer> online, UUID joined) {
        List<UUID> out = new ArrayList<>();
        for (Viewer v : online) {
            if (v.notified() && !v.id().equals(joined)) {
                out.add(v.id());
            }
        }
        return out;
    }

    /** The reason as stored: trimmed, at most {@code max} characters, {@code null} when empty. */
    static String cleanReason(String raw, int max) {
        if (raw == null) {
            return null;
        }
        String s = Notes.clean(raw);
        if (s.isEmpty()) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
