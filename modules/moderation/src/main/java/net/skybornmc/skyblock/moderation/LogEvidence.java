package net.skybornmc.skyblock.moderation;

import net.skybornmc.skyblock.common.audit.AuditCategory;
import net.skybornmc.skyblock.common.audit.AuditEntry;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Punish from logs (spec 13.11), the moderation side. Pure: no server, no database.
 *
 * <p>A {@code /logs} chat line opens {@code /p <player> --evidence <log-entry-id>}. The entry (fetched through the logs
 * module's {@code LogLookup}, which also checks the staff member may see chat logs) must be a chat line written by the
 * punished player. The punishment then saves that line plus the lines before it (the normal evidence settings: the
 * player's last {@code chat-lines}, {@code context-lines} of others) as its evidence snapshot, instead of the player's
 * newest lines at punish time; also for bans and kicks, because staff picked the line on purpose.</p>
 */
final class LogEvidence {

    /** Chat module audit actions that can be evidence. */
    static final Set<String> CHAT_ACTIONS = Set.of("chat", "chat_blocked");
    /** The flag on {@code /p}. */
    static final String FLAG = "--evidence";

    /** Why an entry can't be evidence for a target (message keys {@code log-evidence.<kebab>}). */
    enum Problem {
        /** No such entry, or the staff member may not see chat logs. */
        NOT_FOUND,
        /** Not a chat line (e.g. an economy entry). */
        NOT_CHAT,
        /** A chat line of another player. */
        OTHER_PLAYER;

        String key() {
            return "log-evidence." + name().toLowerCase(Locale.ROOT).replace('_', '-');
        }
    }

    /**
     * The chosen line: entry id, time, who wrote it and a short preview for the {@code /p} head.
     *
     * @param line {@code [14:03] Steve: hello} (cut, IP addresses hidden)
     */
    record Anchor(long id, long time, UUID player, String playerName, String line) {
    }

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);

    private LogEvidence() {
    }

    /** Empty if {@code entry} is a chat line written by {@code target}. */
    static Optional<Problem> check(AuditEntry entry, UUID target) {
        if (entry == null) {
            return Optional.of(Problem.NOT_FOUND);
        }
        if (entry.category() != AuditCategory.CHAT || !CHAT_ACTIONS.contains(entry.action())) {
            return Optional.of(Problem.NOT_CHAT);
        }
        if (entry.actor() == null || entry.actor().uuid() == null || !entry.actor().uuid().equals(target)) {
            return Optional.of(Problem.OTHER_PLAYER);
        }
        return Optional.empty();
    }

    /** The anchor of a checked entry: preview line cut to {@code max} characters, IPs replaced by {@code ipHidden}. */
    static Anchor anchor(AuditEntry entry, ZoneId zone, int max, String ipHidden) {
        Object message = entry.details().get("message");
        String text = Evidence.redact(Evidence.clean(message == null ? "" : String.valueOf(message), Integer.MAX_VALUE), ipHidden);
        String line = "[" + TIME.withZone(zone).format(Instant.ofEpochMilli(entry.time())) + "] "
                + Evidence.clean(entry.actor().name(), 32) + ": " + text;
        return new Anchor(entry.id(), entry.time(), entry.actor().uuid(), entry.actor().name(), Evidence.cut(line, Math.max(10, max)));
    }

    /** A log entry id as typed ({@code 1234} or {@code #1234}), or {@code null}. */
    static Long parseId(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.strip();
        if (s.startsWith("#")) {
            s = s.substring(1);
        }
        if (s.isEmpty() || s.length() > 18 || !s.chars().allMatch(Character::isDigit)) {
            return null;
        }
        long id = Long.parseLong(s);
        return id > 0 ? id : null;
    }
}
