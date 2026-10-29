package net.skybornmc.skyblock;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bukkit's YAML (YAML 1.1) reads unquoted keys like {@code yes}, {@code no}, {@code on}, {@code off} as booleans,
 * so {@code confirm.yes.name} silently becomes {@code confirm.true.name} and shows as "[missing: …]" in game.
 * Every such key must be quoted ({@code "yes":}).
 */
class YamlKeysTest {

    private static final Pattern BOOLEAN_KEY = Pattern.compile("^\\s*(?i:yes|no|on|off|y|n|true|false):(\\s|$)");

    @Test
    void noUnquotedBooleanLikeKeys() throws IOException {
        Path root = Path.of("..").toAbsolutePath().normalize();
        List<String> problems = new ArrayList<>();
        for (String dir : List.of("modules", "plugin/src/main/resources", "server/plugins/PhoenixSkyblock")) {
            Path base = root.resolve(dir);
            if (!Files.isDirectory(base)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(base)) {
                for (Path file : files.filter(p -> p.toString().endsWith(".yml") && !p.toString().contains("/build/")).toList()) {
                    List<String> lines = Files.readAllLines(file);
                    for (int i = 0; i < lines.size(); i++) {
                        if (BOOLEAN_KEY.matcher(lines.get(i)).find()) {
                            problems.add(root.relativize(file) + ":" + (i + 1) + "  " + lines.get(i).trim());
                        }
                    }
                }
            }
        }
        assertTrue(problems.isEmpty(), "Quote these YAML keys (Bukkit reads them as booleans):\n" + String.join("\n", problems));
    }
}
