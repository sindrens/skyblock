package net.skybornmc.skyblock.logs;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.skybornmc.skyblock.common.util.Format;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Pure text helpers for log entries (no Bukkit), so they can be unit tested. */
final class LogText {

    /** Detail keys that hold IP addresses; hidden unless the viewer has {@code skyblock.staff.whois.ip}. */
    static final Set<String> IP_KEYS = Set.of("ip", "ips", "address", "addresses", "host", "ip_address");
    static final String HIDDEN = "(hidden)";
    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().serializeNulls().create();
    private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'", Locale.ROOT)
            .withZone(ZoneOffset.UTC);

    private LogText() {
    }

    /** {@code island_invite} → {@code island invite}. */
    static String humanize(String action) {
        return action == null ? "" : action.replace('_', ' ').trim();
    }

    /** "3m ago" style; "just now" under 5 s. */
    static String ago(long time, long now) {
        long ms = Math.max(0, now - time);
        if (ms < 5_000) {
            return "just now";
        }
        return Format.duration(Duration.ofMillis(ms)) + " ago";
    }

    static String utc(long time) {
        return UTC.format(Instant.ofEpochMilli(time));
    }

    /** A copy of {@code details} with IP-like values replaced by {@link #HIDDEN} (recursively), unless allowed. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> redact(Map<String, Object> details, boolean allowIp) {
        if (allowIp || details == null || details.isEmpty()) {
            return details == null ? Map.of() : details;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        details.forEach((key, value) -> {
            if (IP_KEYS.contains(key.toLowerCase(Locale.ROOT))) {
                out.put(key, HIDDEN);
            } else if (value instanceof Map<?, ?> nested) {
                out.put(key, redact((Map<String, Object>) nested, false));
            } else {
                out.put(key, value);
            }
        });
        return out;
    }

    /** Details as pretty-printed JSON lines (2-space indent), each line at most {@code width} chars (wrapped). */
    static List<String> prettyLines(Map<String, Object> details, int width) {
        List<String> out = new ArrayList<>();
        if (details == null || details.isEmpty()) {
            return out;
        }
        for (String line : PRETTY.toJson(details).split("\n")) {
            String rest = line;
            while (rest.length() > width) {
                out.add(rest.substring(0, width));
                rest = "    " + rest.substring(width);
            }
            out.add(rest);
        }
        return out;
    }

    /** {@code key: value} lines (top level only, nested maps flattened as JSON), for item lore. */
    static List<String> detailLines(Map<String, Object> details, int max, int width) {
        List<String> out = new ArrayList<>();
        if (details == null) {
            return out;
        }
        for (Map.Entry<String, Object> e : details.entrySet()) {
            if (out.size() >= max) {
                out.add("...+" + (details.size() - max) + " more");
                break;
            }
            String value = e.getValue() instanceof String s ? s : PRETTY.toJson(e.getValue()).replace("\n", "").replaceAll("\\s{2,}", " ");
            out.add(shorten(e.getKey() + ": " + value, width));
        }
        return out;
    }

    /** The main free-text detail of an entry (command line, chat message), or {@code null}. */
    static String mainText(Map<String, Object> details) {
        if (details == null) {
            return null;
        }
        for (String key : List.of("line", "message", "reason", "text")) {
            Object value = details.get(key);
            if (value instanceof String s && !s.isBlank()) {
                return s;
            }
        }
        return null;
    }

    static String shorten(String text, int max) {
        if (text == null || text.length() <= max) {
            return text;
        }
        return text.substring(0, Math.max(1, max - 3)) + "...";
    }

    /** One CSV field (RFC 4180): quoted when it contains a comma, quote or line break. */
    static String csv(Object value) {
        if (value == null) {
            return "";
        }
        String s = String.valueOf(value);
        if (s.indexOf(',') >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0) {
            return '"' + s.replace("\"", "\"\"") + '"';
        }
        return s;
    }

    /** Compact JSON (one line) for CSV. */
    static String json(Map<String, Object> details) {
        return details == null || details.isEmpty() ? "" : new Gson().toJson(details);
    }
}
