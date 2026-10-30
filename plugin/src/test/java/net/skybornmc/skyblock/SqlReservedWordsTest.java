package net.skybornmc.skyblock;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests run migrations on H2, but the server runs MariaDB, where some words are reserved. A column named
 * {@code lines} passed every test and then failed on the VPS (P3, update 53). Column names in
 * {@code db/<module>/*.sql} must not be MariaDB reserved words (common ones listed here).
 */
class SqlReservedWordsTest {

    /** MariaDB reserved words that are tempting column names. */
    private static final Set<String> RESERVED = Set.of(
            "add", "all", "alter", "analyze", "and", "as", "asc", "before", "between", "both", "by", "call", "cascade",
            "case", "change", "check", "collate", "column", "condition", "constraint", "continue", "convert", "create",
            "cross", "current_date", "current_time", "current_user", "cursor", "database", "databases", "default",
            "delayed", "delete", "desc", "describe", "distinct", "div", "drop", "each", "else", "elseif", "exists",
            "exit", "explain", "false", "fetch", "for", "force", "foreign", "from", "fulltext", "grant", "group",
            "having", "if", "ignore", "in", "index", "inner", "insert", "interval", "into", "is", "join", "key", "keys",
            "kill", "leading", "leave", "left", "like", "limit", "lines", "load", "lock", "long", "loop", "match",
            "mod", "natural", "not", "null", "offset", "on", "option", "or", "order", "out", "outer", "over",
            "partition", "precision", "primary", "procedure", "purge", "range", "read", "references", "regexp",
            "release", "rename", "repeat", "replace", "require", "restrict", "return", "returning", "revoke", "right",
            "rlike", "rows", "schema", "select", "separator", "set", "show", "signal", "spatial", "sql", "ssl",
            "starting", "table", "terminated", "then", "to", "trailing", "trigger", "true", "undo", "union", "unique",
            "unlock", "unsigned", "update", "usage", "use", "using", "values", "when", "where", "while", "window",
            "with", "write", "xor", "zerofill");

    /** A column definition inside CREATE TABLE ({@code   name TYPE ...}) or {@code ADD COLUMN [IF NOT EXISTS] name}. */
    private static final Pattern COLUMN_DEF = Pattern.compile("^\\s+([A-Za-z_][A-Za-z0-9_]*)\\s+[A-Z]");
    private static final Pattern ADD_COLUMN = Pattern.compile("(?i)ADD\\s+COLUMN\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?([A-Za-z_][A-Za-z0-9_]*)");
    private static final Set<String> TABLE_WORDS = Set.of("primary", "unique", "key", "index", "constraint", "foreign", "check");

    @Test
    void columnNamesAreNotMariaDbReservedWords() throws IOException {
        Path root = Path.of("..").toAbsolutePath().normalize();
        List<String> problems = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root.resolve("modules"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".sql") && p.toString().contains("/db/")
                    && !p.toString().contains("/build/")).toList()) {
                List<String> lines = Files.readAllLines(file);
                boolean inCreate = false;
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i);
                    if (line.trim().startsWith("--")) {
                        continue;
                    }
                    if (line.toUpperCase(Locale.ROOT).contains("CREATE TABLE")) {
                        inCreate = true;
                    } else if (line.trim().startsWith(")")) {
                        inCreate = false;
                    } else if (inCreate) {
                        check(root, file, i, line, COLUMN_DEF.matcher(line), true, problems);
                    }
                    Matcher add = ADD_COLUMN.matcher(line);
                    while (add.find()) {
                        report(root, file, i, add.group(1), problems);
                    }
                }
            }
        }
        assertTrue(problems.isEmpty(), "Column names that are MariaDB reserved words (rename them):\n" + String.join("\n", problems));
    }

    private static void check(Path root, Path file, int i, String line, Matcher m, boolean skipTableWords, List<String> problems) {
        if (m.find()) {
            String name = m.group(1).toLowerCase(Locale.ROOT);
            if (skipTableWords && TABLE_WORDS.contains(name)) {
                return;
            }
            report(root, file, i, name, problems);
        }
    }

    private static void report(Path root, Path file, int i, String name, List<String> problems) {
        if (RESERVED.contains(name.toLowerCase(Locale.ROOT))) {
            problems.add(root.relativize(file) + ":" + (i + 1) + "  " + name);
        }
    }
}
