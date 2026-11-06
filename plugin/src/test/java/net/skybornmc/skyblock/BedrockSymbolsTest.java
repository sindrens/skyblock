package net.skybornmc.skyblock;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FX-10 (6 Nov 2026): every quoted message text in the bundled message files only uses ASCII and the Bedrock-safe
 * symbols of {@code style.yml → bedrock-safe-symbols}. A symbol outside the list shows as a box on Bedrock (and makes a
 * live reload refuse the file).
 */
class BedrockSymbolsTest {

    private static final Set<Integer> SAFE = "✦✧◆●▸▶★✔✘⚔⛏❤⌛»«│·•".codePoints().boxed().collect(java.util.stream.Collectors.toSet());
    private static final Pattern QUOTED = Pattern.compile("\"([^\"]*)\"");

    @Test
    void messageFilesUseOnlyBedrockSafeSymbols() throws IOException {
        Path root = Path.of("..").toAbsolutePath().normalize();
        List<String> bad = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root, 8)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".yml"))
                    .filter(p -> p.toString().contains("src/main/resources/messages/"))
                    .filter(p -> !p.toString().contains("/build/")).toList()) {
                List<String> lines = Files.readAllLines(f);
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i);
                    int n = i + 1;
                    if (line.stripLeading().startsWith("#")) {
                        continue;
                    }
                    int comment = line.indexOf(" #");
                    Matcher m = QUOTED.matcher(comment >= 0 ? line.substring(0, comment) : line);
                    while (m.find()) {
                        String text = m.group(1);
                        text.codePoints().filter(c -> c > 0x7f && !SAFE.contains(c)).findFirst().ifPresent(c ->
                                bad.add(root.relativize(f) + ":" + n + " uses '" + Character.toString(c) + "'"));
                    }
                }
            }
        }
        assertTrue(bad.isEmpty(), "Symbols that are not Bedrock-safe:\n" + String.join("\n", bad));
    }
}
