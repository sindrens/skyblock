package net.skybornmc.skyblock.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * One parsed scoreboard layout (pure, no Bukkit): its lines with optional conditions.
 *
 * <p>A line written as {@code "if:<placeholder>|<text>"} is only shown while that placeholder has a value (not empty,
 * not "-"), e.g. {@code "if:island_next_note| <c:muted>Next</c:muted>  <ph:island_next_note>"}. Several placeholders
 * separated by commas ({@code "if:island_level,island_next_note|"}) show the line while <b>any</b> of them has a value
 * (a section header over conditional lines, owner answer 275). Hidden lines take no
 * space, so the board never shows a label without a value. Parsed once per message text and cached by the service,
 * so a refresh does not split strings again.</p>
 *
 * <p>Board rules (G7-R1-01, {@link #visible(Predicate, Rules)}): every line has a {@link Kind} worked out once at parse
 * time. A section is a {@link Kind#HEADER} line ({@code <bold><c:header>...}) followed by {@link Kind#ROW} lines
 * ({@code <c:bar>...}); rows past {@link Rules#sectionRows} are dropped (first in layout order win), a header with no
 * row under it is dropped, and the {@code <soft-blank>} line only shows while the board is short.</p>
 */
record LayoutLines(String source, List<Line> lines, boolean usesTimer) {

    static final String IF = "if:";
    /** A blank line that only shows while the board is short ({@link Rules#softBlankMaxLines}); never parsed. */
    static final String SOFT_BLANK = "<soft-blank>";
    static final String HEADER_START = "<bold><c:header>";
    static final String ROW_START = "<c:bar>";
    /** Look v2 (owner 7 Nov 2026): a header in its block colour, {@code <bold><c:farm><sc>The Farm</sc>...}. */
    static final java.util.regex.Pattern HEADER_V2 = java.util.regex.Pattern.compile("<bold><c:[a-z_]+>");
    /** Look v2: a row starting with the block colour's bar, {@code <c:farm>│</c:farm> ...}. */
    static final java.util.regex.Pattern ROW_V2 = java.util.regex.Pattern.compile("<c:[a-z_]+>[|│]");
    /** The built-in condition key that is true while the player has a running timer ({@code BoardTimers}). */
    static final String TIMER_KEY = "board_timer";

    /** What a line is, for the board rules. */
    enum Kind { BLANK, SOFT_BLANK, HEADER, ROW, OTHER, GAP }

    /** One line: {@code condition} is a placeholder key (or keys joined by commas) or null (always shown); {@code text} is MiniMessage. */
    record Line(String condition, String text, Kind kind) {

        Line(String condition, String text) {
            this(condition, text, kindOf(text));
        }

        boolean blank() {
            return kind == Kind.BLANK || kind == Kind.SOFT_BLANK || kind == Kind.GAP;
        }
    }

    /**
     * Board limits: {@code maxLines} in total (title not counted), {@code sectionRows} rows per header, and the soft
     * blank line shows only while the other visible lines are at most {@code softBlankMaxLines}.
     */
    record Rules(int maxLines, int sectionRows, int softBlankMaxLines, boolean gaps) {

        Rules(int maxLines, int sectionRows, int softBlankMaxLines) {
            this(maxLines, sectionRows, softBlankMaxLines, false);
        }
    }

    /** An automatic blank line between sections (owner 3 Nov 2026 "more space"); the first lines to go on a full board. */
    static final Line GAP = new Line(null, "", Kind.GAP);

    LayoutLines {
        lines = List.copyOf(lines);
    }

    LayoutLines(String source, List<Line> lines) {
        this(source, lines, lines.stream().anyMatch(l -> l.text().contains("<timer>") || l.text().contains(TIMER_KEY)
                || (l.condition() != null && l.condition().contains(TIMER_KEY))));
    }

    static Kind kindOf(String text) {
        if (text.isEmpty()) {
            return Kind.BLANK;
        }
        if (text.equals(SOFT_BLANK)) {
            return Kind.SOFT_BLANK;
        }
        String t = text.stripLeading();
        if (t.startsWith(HEADER_START) || HEADER_V2.matcher(t).lookingAt()) {
            return Kind.HEADER;
        }
        if (t.startsWith(ROW_START) || ROW_V2.matcher(t).lookingAt()) {
            return Kind.ROW;
        }
        return Kind.OTHER;
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
            if (!key.isEmpty() && key.matches("[a-z0-9_]+(,[a-z0-9_]+)*")) {
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
     * lines (hidden lines don't count). No section rules (kept for custom callers and the old tests).
     */
    List<Line> visible(Predicate<String> shown, int max) {
        List<Line> out = new ArrayList<>(Math.min(lines.size(), Math.max(0, max)));
        for (Line line : lines) {
            if (out.size() >= max) {
                break;
            }
            if (line.condition() == null || line.kind() == Kind.SOFT_BLANK || anyShown(line.condition(), shown)) {
                out.add(line);
            }
        }
        return out;
    }

    /** The lines to show under the board rules (G7-R1-01); see the class comment. */
    List<Line> visible(Predicate<String> shown, Rules rules) {
        // 1 + 2: conditions, then the row cap per section (a non-row line ends the section).
        List<Line> kept = new ArrayList<>(lines.size());
        int rows = 0;
        boolean inSection = false;
        for (Line line : lines) {
            if (line.kind() != Kind.SOFT_BLANK && line.condition() != null && !anyShown(line.condition(), shown)) {
                continue;
            }
            switch (line.kind()) {
                case HEADER -> {
                    inSection = true;
                    rows = 0;
                }
                case ROW -> {
                    if (inSection && ++rows > rules.sectionRows()) {
                        continue;
                    }
                }
                default -> inSection = false;
            }
            kept.add(line);
        }
        // 3: a header without a row right under it goes; 4: count the lines that are not soft blanks.
        List<Line> out = new ArrayList<>(kept.size());
        int others = 0;
        for (int i = 0; i < kept.size(); i++) {
            Line line = kept.get(i);
            if (line.kind() == Kind.HEADER && (i + 1 >= kept.size() || kept.get(i + 1).kind() != Kind.ROW)) {
                continue;
            }
            if (line.kind() != Kind.SOFT_BLANK) {
                others++;
            }
            out.add(line);
        }
        if (others > rules.softBlankMaxLines()) {
            out.removeIf(l -> l.kind() == Kind.SOFT_BLANK);
        }
        int max = Math.max(0, rules.maxLines());
        // 4b: gaps before every section but the first and before the footer; dropped bottom-up while the board is full.
        if (rules.gaps()) {
            out = withGaps(out);
            for (int i = out.size() - 1; i >= 0 && out.size() > max; i--) {
                if (out.get(i).kind() == Kind.GAP) {
                    out.remove(i);
                }
            }
        }
        // 5: the total cap.
        return out.size() > max ? new ArrayList<>(out.subList(0, max)) : out;
    }

    /** Pure: a gap before each header after the first section, and before a closing non-row line (the footer). */
    static List<Line> withGaps(List<Line> lines) {
        List<Line> out = new ArrayList<>(lines.size() + 4);
        boolean seenSection = false;
        for (int i = 0; i < lines.size(); i++) {
            Line line = lines.get(i);
            boolean last = i == lines.size() - 1;
            boolean previousBlank = !out.isEmpty() && out.getLast().blank();
            if (line.kind() == Kind.HEADER) {
                if (seenSection && !previousBlank) {
                    out.add(GAP);
                }
                seenSection = true;
            } else if (last && line.kind() == Kind.OTHER && seenSection && !previousBlank) {
                out.add(GAP);
            }
            out.add(line);
        }
        return out;
    }

    /** True if any key of a condition ({@code a} or {@code a,b,...}) has a value. */
    static boolean anyShown(String condition, Predicate<String> shown) {
        for (String key : condition.split(",")) {
            if (shown.test(key)) {
                return true;
            }
        }
        return false;
    }

    /** Lines that are always visible plus conditional ones: the most a layout can show. */
    int maxSize() {
        return lines.size();
    }
}
