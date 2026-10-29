package net.skybornmc.skyblock.moderation;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Line numbers of keys in a YAML file ({@code categories.chat.reasons.spam.ladder} → 42) so config errors can say
 * "punishments.yml line 42". Block style only (what our files use); list items get their index as a path segment
 * ({@code warn-rules.rules.0.action}). Unknown paths fall back to the nearest known parent.
 */
final class YamlLines {

    private static final Pattern KEY = Pattern.compile("^( *)(- +)?(\"[^\"]*\"|'[^']*'|[^\\s#'\"][^:#]*?)\\s*:(\\s|$)");
    private static final Pattern ITEM = Pattern.compile("^( *)- ");

    private final Map<String, Integer> lines = new HashMap<>();

    private YamlLines() {
    }

    static YamlLines index(String text) {
        YamlLines out = new YamlLines();
        record Frame(int indent, String path) {
        }
        Deque<Frame> stack = new ArrayDeque<>();
        Map<String, Integer> counters = new HashMap<>();
        String[] all = text.split("\\r?\\n", -1);
        for (int i = 0; i < all.length; i++) {
            String line = all[i];
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            Matcher item = ITEM.matcher(line);
            Matcher key = KEY.matcher(line);
            int indent = line.length() - line.stripLeading().length();
            if (item.find()) {
                // "- ..." : a list item of the nearest parent with a smaller indent.
                while (!stack.isEmpty() && stack.peek().indent() >= indent) {
                    stack.pop();
                }
                String parent = stack.isEmpty() ? "" : stack.peek().path();
                int index = counters.merge(parent, 1, Integer::sum) - 1;
                String itemPath = join(parent, String.valueOf(index));
                out.lines.putIfAbsent(itemPath, i + 1);
                stack.push(new Frame(indent, itemPath));
                if (key.find() && key.group(2) != null) {
                    int keyIndent = indent + key.group(2).length();
                    String path = join(itemPath, unquote(key.group(3)));
                    out.lines.putIfAbsent(path, i + 1);
                    stack.push(new Frame(keyIndent, path));
                }
                continue;
            }
            if (!key.find()) {
                continue;
            }
            while (!stack.isEmpty() && stack.peek().indent() >= indent) {
                stack.pop();
            }
            String parent = stack.isEmpty() ? "" : stack.peek().path();
            String path = join(parent, unquote(key.group(3)));
            out.lines.putIfAbsent(path, i + 1);
            stack.push(new Frame(indent, path));
        }
        return out;
    }

    private static String join(String parent, String key) {
        return parent.isEmpty() ? key : parent + "." + key;
    }

    private static String unquote(String key) {
        String k = key.strip();
        if (k.length() >= 2 && (k.startsWith("\"") && k.endsWith("\"") || k.startsWith("'") && k.endsWith("'"))) {
            return k.substring(1, k.length() - 1);
        }
        return k;
    }

    /** Line (1-based) of {@code path} or its nearest known parent; 0 if unknown. */
    int line(String path) {
        String p = path;
        while (p != null && !p.isEmpty()) {
            Integer line = lines.get(p);
            if (line != null) {
                return line;
            }
            int dot = p.lastIndexOf('.');
            p = dot < 0 ? null : p.substring(0, dot);
        }
        return 0;
    }
}
