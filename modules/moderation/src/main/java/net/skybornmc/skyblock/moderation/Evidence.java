package net.skybornmc.skyblock.moderation;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Evidence snapshot (spec 12.4): the target's last chat lines (plus a few lines of other players for context), copied
 * from the audit log into {@code punishment_evidence} when a mute or warn is given. Pure: no server, no database.
 *
 * <p>Privacy: IP addresses are never stored (any IPv4/IPv6 address in a message is replaced), lines are one line each
 * (no control characters), cut to {@code max-line-length}, and the whole snapshot never exceeds {@code max-chars}
 * (the oldest lines are dropped first).</p>
 */
final class Evidence {

    /** One chat line from the audit log. {@code blocked} = the chat filter stopped it (others never saw it). */
    record Line(long time, UUID player, String name, String message, boolean blocked) {
    }

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ENGLISH);
    private static final Pattern IPV4 = Pattern.compile(
            "(?<![\\d.])(?:(?:25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\s*\\.\\s*){3}(?:25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(?::\\d{1,5})?(?![\\d.])");
    /** Full form (5+ groups) or compressed with "::" (also IPv4-mapped). */
    private static final Pattern IPV6 = Pattern.compile("(?i)(?<![0-9a-f:])(?:"
            + "(?:[0-9a-f]{1,4}:){4,7}[0-9a-f]{1,4}"
            + "|(?:[0-9a-f]{1,4}:){1,6}:(?:[0-9a-f]{1,4}(?::[0-9a-f]{1,4}){0,5})?"
            + "|::(?:[0-9a-f]{1,4}:){0,5}[0-9a-f]{1,4}"
            + ")(?![0-9a-f:])");
    private static final Pattern CONTROL = Pattern.compile("[\\p{Cntrl}\\u2028\\u2029]+");
    static final String SEPARATOR = "\n";

    private Evidence() {
    }

    /**
     * The lines to store, oldest first, formatted {@code [14:03:12] Steve: hello}.
     *
     * @param all          chat lines around the punishment (any order, may include lines after {@code until})
     * @param target       the punished player
     * @param until        the punishment time: later lines are ignored
     * @param ipHidden     replacement for IP addresses (messages {@code evidence.ip-hidden})
     * @param blockedMark  appended to lines the chat filter blocked (messages {@code evidence.blocked})
     */
    static List<String> build(List<Line> all, UUID target, long until, PunishConfig.Evidence cfg, ZoneId zone,
                              String ipHidden, String blockedMark) {
        List<Line> mine = new ArrayList<>();
        List<Line> others = new ArrayList<>();
        for (Line l : all) {
            if (l.time() > until || l.message() == null) {
                continue;
            }
            if (target.equals(l.player())) {
                mine.add(l);
            } else if (!l.blocked()) {
                others.add(l);
            }
        }
        Comparator<Line> newestFirst = Comparator.comparingLong(Line::time).reversed();
        mine.sort(newestFirst);
        List<Line> chosen = new ArrayList<>(mine.subList(0, Math.min(cfg.chatLines(), mine.size())));
        // context: other players' lines up to the target's newest line (the conversation they answered)
        long contextUntil = chosen.isEmpty() ? until : chosen.getFirst().time();
        others.removeIf(l -> l.time() > contextUntil);
        others.sort(newestFirst);
        chosen.addAll(others.subList(0, Math.min(cfg.contextLines(), others.size())));
        chosen.sort(Comparator.comparingLong(Line::time));

        DateTimeFormatter time = TIME.withZone(zone);
        List<String> out = new ArrayList<>();
        for (Line l : chosen) {
            String text = "[" + time.format(Instant.ofEpochMilli(l.time())) + "] " + clean(l.name(), 32) + ": "
                    + redact(clean(l.message(), Integer.MAX_VALUE), ipHidden) + (l.blocked() ? " " + blockedMark : "");
            out.add(cut(text, cfg.maxLineLength()));
        }
        int total = out.stream().mapToInt(s -> s.length() + SEPARATOR.length()).sum();
        while (!out.isEmpty() && total > cfg.maxChars()) {
            total -= out.removeFirst().length() + SEPARATOR.length();
        }
        return List.copyOf(out);
    }

    /** One line: control characters (newlines included) become a space, then trimmed and cut. */
    static String clean(String raw, int max) {
        if (raw == null) {
            return "";
        }
        return cut(CONTROL.matcher(raw).replaceAll(" ").strip(), max);
    }

    /** Replaces every IPv4/IPv6 address (with or without port, also "1 . 2 . 3 . 4") by {@code replacement}. */
    static String redact(String text, String replacement) {
        String r = java.util.regex.Matcher.quoteReplacement(replacement);
        return IPV6.matcher(IPV4.matcher(text).replaceAll(r)).replaceAll(r);
    }

    static String cut(String s, int max) {
        if (s.length() <= max) {
            return s;
        }
        return max <= 3 ? s.substring(0, max) : s.substring(0, max - 3) + "...";
    }

    /** Stored form (one text column). */
    static String join(List<String> lines) {
        return String.join(SEPARATOR, lines);
    }

    static List<String> split(String stored) {
        if (stored == null || stored.isEmpty()) {
            return List.of();
        }
        return List.of(stored.split(SEPARATOR, -1));
    }
}
