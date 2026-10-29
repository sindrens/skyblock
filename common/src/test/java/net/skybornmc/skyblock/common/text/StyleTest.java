package net.skybornmc.skyblock.common.text;

import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StyleTest {

    private static final String YAML = """
            prefix: "<c:brand>P</c:brand> "
            colors:
              brand: "#C21F2B dark_red"
              ember: "#FF8A1F gold"
              broken: "red"
            gradients:
              phoenix: [brand, ember]
            symbols:
              money: "$"
            bedrock-safe-symbols: "✔ ✘"
            """;

    @Test
    void loadsColorsAndReportsBadOnes(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("style.yml");
        Files.writeString(file, YAML);
        Style style = new Style();
        List<String> problems = style.load(file.toFile());
        assertEquals(1, problems.size());
        assertTrue(problems.getFirst().contains("colors.broken"));
        assertEquals(NamedTextColor.DARK_RED, style.colors().get("c").get("brand").legacy());
        assertEquals("$", style.symbol("money"));
    }

    @Test
    void gradientsExpandForJavaAndBedrock(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("style.yml");
        Files.writeString(file, YAML);
        Style style = new Style();
        style.load(file.toFile());
        assertEquals("<gradient:#c21f2b:#ff8a1f>Hi</gradient>", style.preprocess("<grad:phoenix>Hi</grad>", false));
        assertEquals("<color:dark_red>Hi</color>", style.preprocess("<grad:phoenix>Hi</grad>", true));
    }

    @Test
    void findsUnsafeGlyphs(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("style.yml");
        Files.writeString(file, YAML);
        Style style = new Style();
        style.load(file.toFile());
        assertEquals(Set.of("⚠"), style.unsafeGlyphs("✔ ok ⚠ é"));
    }

    @Test
    void brokenFileKeepsPreviousStyle(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("style.yml");
        Files.writeString(file, YAML);
        Style style = new Style();
        style.load(file.toFile());
        Files.writeString(file, "colors: [unclosed");
        assertEquals(1, style.load(file.toFile()).size());
        assertEquals("$", style.symbol("money"));
    }
}
