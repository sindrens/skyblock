package net.skybornmc.skyblock;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 7 Nov 2026 (update167 incident: /store was registered by social and by the economy's credit shop, so the economy
 * module failed to enable on the test server): every top-level command name and alias is used once across all modules.
 * Reads the sources: {@code CommandSpec.command("x")} with the {@code .aliases(...)} of the same statement.
 */
class CommandNamesUniqueTest {

    private static final Pattern COMMAND = Pattern.compile("(?:CommandSpec\\.command|\\blink)\\(\"([a-z0-9_-]+)\"");
    private static final Pattern ALIASES = Pattern.compile("\\.aliases\\(([^)]*)\\)");
    private static final Pattern WORD = Pattern.compile("\"([a-z0-9_-]+)\"");

    @Test
    void commandNamesAndAliasesAreUnique() throws IOException {
        Path root = Path.of("..").toAbsolutePath().normalize();
        Map<String, String> seen = new HashMap<>();
        List<String> twice = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root.resolve("modules"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java") && p.toString().contains("/src/main/")).toList()) {
                String src = Files.readString(file);
                Matcher m = COMMAND.matcher(src);
                while (m.find()) {
                    int end = src.indexOf(';', m.end());
                    String statement = src.substring(m.start(), end < 0 ? src.length() : end);
                    // only this command's own aliases (stop at the first .sub( of the statement)
                    int sub = statement.indexOf(".sub(");
                    String head = sub < 0 ? statement : statement.substring(0, sub);
                    List<String> names = new ArrayList<>(List.of(m.group(1)));
                    Matcher a = ALIASES.matcher(head);
                    if (a.find()) {
                        Matcher w = WORD.matcher(a.group(1));
                        while (w.find()) {
                            names.add(w.group(1));
                        }
                    }
                    String where = root.relativize(file).toString();
                    for (String n : names) {
                        String before = seen.putIfAbsent(n, where);
                        if (before != null && !before.equals(where)) {
                            twice.add("/" + n + ": " + before + " and " + where);
                        }
                    }
                }
            }
        }
        assertEquals(List.of(), twice, "Command names or aliases used by two modules");
    }
}
