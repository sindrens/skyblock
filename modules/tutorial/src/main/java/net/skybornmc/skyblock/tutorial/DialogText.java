package net.skybornmc.skyblock.tutorial;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The text side of talking NPCs (pure, unit-tested): commands in a dialog line, word wrapping for the text above the NPC and
 * how much of a line the typewriter shows. A line is plain text; {@code /word} and {@code {/two words}} are commands that
 * become clickable in chat.
 */
final class DialogText {

    /** A piece of a line: plain text, or a command ({@code command} without the slash, else null). */
    record Segment(String text, String command) {

        boolean isCommand() {
            return command != null;
        }
    }

    private static final Pattern COMMAND = Pattern.compile("\\{/([a-z0-9_][a-z0-9_ -]{0,40})}|(?<![\\w/])/([a-z][a-z0-9_-]{0,24})");

    private DialogText() {
    }

    /** The line split into text and command pieces, in order. */
    static List<Segment> segments(String line) {
        List<Segment> out = new ArrayList<>();
        if (line == null || line.isEmpty()) {
            return out;
        }
        Matcher m = COMMAND.matcher(line);
        int at = 0;
        while (m.find()) {
            if (m.start() > at) {
                out.add(new Segment(line.substring(at, m.start()), null));
            }
            String cmd = (m.group(1) != null ? m.group(1) : m.group(2)).strip();
            out.add(new Segment("/" + cmd, cmd));
            at = m.end();
        }
        if (at < line.length()) {
            out.add(new Segment(line.substring(at), null));
        }
        return out;
    }

    /** The line as it reads (braces of multi-word commands removed). */
    static String plain(String line) {
        StringBuilder sb = new StringBuilder();
        for (Segment s : segments(line)) {
            sb.append(s.text());
        }
        return sb.toString();
    }

    /** Word wrap at {@code width} characters; a word longer than the width is cut. */
    static List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        int w = Math.max(4, width);
        StringBuilder line = new StringBuilder();
        for (String word : text.strip().split("\\s+")) {
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

    /**
     * The pieces the text above the NPC shows one after another: the wrapped line in groups of {@code maxLines} lines,
     * each group joined with a newline. A short line is one piece.
     */
    static List<String> pieces(String plain, int width, int maxLines) {
        List<String> wrapped = wrap(plain, width);
        List<String> out = new ArrayList<>();
        int per = Math.max(1, maxLines);
        for (int i = 0; i < wrapped.size(); i += per) {
            out.add(String.join("\n", wrapped.subList(i, Math.min(wrapped.size(), i + per))));
        }
        if (out.isEmpty()) {
            out.add("");
        }
        return out;
    }

    /** Characters typed after {@code ticks} at {@code charsPerSecond} (at least 1 per tick once started). */
    static int typed(long ticks, double charsPerSecond, int length) {
        if (ticks <= 0) {
            return 0;
        }
        long n = (long) Math.floor(ticks * Math.max(1, charsPerSecond) / 20.0);
        return (int) Math.min(length, Math.max(1, n));
    }

    /** Ticks until the whole piece is typed. */
    static long typingTicks(int length, double charsPerSecond) {
        return (long) Math.ceil(length * 20.0 / Math.max(1, charsPerSecond));
    }
}
