package net.skybornmc.skyblock.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * One parsed scoreboard layout (pure, no Bukkit): its lines with optional conditions.
 *
 * <p>A line written as {@code "if:<placeholder>|<text>"} is only shown while that placeholder has a value (not empty,
 * not "-"), e.g. {@code "if:island_next_note| <c:muted>Next</c:muted>  <ph:island_next_note>"}. Hidden lines take no
 * space, so the board never shows a label without a value. Parsed once per message text and cached by the service,
 * so a refresh does not split strings again.</p>
 */
record LayoutLines(String source, List<Line> lines) {

    static final String IF = "if:";

    /** One line: {@code condition} is a placeholder key or null (always shown); {@code text} is MiniMessage. */
    record Line(String condition, String text) {

        boolean blank() {
            return text.isEmpty();
        }
    }

    LayoutLines {
        lines = List.copyOf(lines);
    }

    /** Parses the raw message (list items joined with {@code <newline>} by the messages loader); null = no lines. */
    static LayoutLines parse(String raw) {
        List<Line> out = new ArrayList<>();
        if (raw != null && !raw.isEmpty()) {
            for (String line : raw.split("<newline>", -1)) {
                out.add(line(line));
            }
        }
        return new LayoutLines(raw, out);
    }

    static Line line(String line) {
        if (line.startsWith(IF)) {
            int bar = line.indexOf('|');
            String key = bar < 0 ? "" : line.substring(IF.length(), bar).trim();
            if (!key.isEmpty() && key.matches("[a-z0-9_]+")) {
                return new Line(key, line.substring(bar + 1));
            }
        }
        return new Line(null, line);
    }

    /** True if a placeholder value counts as "has a value" for {@code if:} lines. */
    static boolean present(String value) {
        if (value == null) {
            return false;
        }
        String v = value.strip();
        return !v.isEmpty() && !v.equals("-") && !v.equals("—");
    }

    /**
     * The lines to show: conditions checked with {@code shown} (placeholder key → has a value), at most {@code max}
     * lines (hidden lines don't count).
     */
    List<Line> visible(Predicate<String> shown, int max) {
        List<Line> out = new ArrayList<>(Math.min(lines.size(), Math.max(0, max)));
        for (Line line : lines) {
            if (out.size() >= max) {
                break;
            }
            if (line.condition() == null || shown.test(line.condition())) {
                out.add(line);
            }
        }
        return out;
    }

    /** Lines that are always visible plus conditional ones: the most a layout can show. */
    int maxSize() {
        return lines.size();
    }
}
