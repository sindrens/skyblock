package net.skybornmc.skyblock.common.text;

import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
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
    void oldTokensSymbolFallsBackToCrystals(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("style.yml");
        Files.writeString(file, """
                symbols:
                  crystals: "◆"
                """);
        Style style = new Style();
        style.load(file.toFile());
        assertEquals("◆", style.symbol("tokens"), "tokens were replaced by crystals");
        assertEquals("?", style.symbol("nothing"));
    }

    @Test
    void gradientsExpandForJavaAndBedrock(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("style.yml");
        Files.writeString(file, YAML);
        Style style = new Style();
        style.load(file.toFile());
        assertEquals("<gradient:#C21F2B:#FF8A1F>Hi</gradient>", style.preprocess("<grad:phoenix>Hi</grad>", false));
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

    /** STYLE2: the bundled and the tracked server style.yml are identical, load cleanly and carry the approved V2 palette. */
    @Test
    void bundledStyleIsTheV2Palette() throws Exception {
        Path bundled = Path.of("../plugin/src/main/resources/style.yml");
        Path server = Path.of("../server/plugins/PhoenixSkyblock/style.yml");
        assertEquals(Files.readString(bundled), Files.readString(server), "server/plugins/PhoenixSkyblock/style.yml must match the bundled copy");
        Style style = new Style();
        assertEquals(List.of(), style.load(bundled.toFile()));
        java.util.Map<String, String> expected = java.util.Map.ofEntries(
                java.util.Map.entry("c.brand", "#ff2d3f red"), java.util.Map.entry("c.frame", "#d1202f dark_red"),
                java.util.Map.entry("c.brand_bright", "#ff5a4d red"), java.util.Map.entry("c.ember", "#ff8c1a gold"),
                java.util.Map.entry("c.flame", "#ffd21a yellow"), java.util.Map.entry("c.text", "#e0e0e0 gray"),
                java.util.Map.entry("c.muted", "#a0a0a0 dark_gray"), java.util.Map.entry("c.locked", "#5e5e66 dark_gray"),
                java.util.Map.entry("c.value", "#ffffff white"), java.util.Map.entry("c.action", "#ffe135 yellow"),
                java.util.Map.entry("c.success", "#4dff6a green"), java.util.Map.entry("c.error", "#ff3b3b red"),
                java.util.Map.entry("c.warning", "#ffb020 gold"), java.util.Map.entry("c.info", "#3dc8ff aqua"),
                java.util.Map.entry("c.enchant", "#d17aff light_purple"), java.util.Map.entry("c.header", "#ff2d3f red"),
                java.util.Map.entry("c.bar", "#d1202f dark_red"), java.util.Map.entry("c.buy", "#4dff88 green"),
                java.util.Map.entry("c.sell", "#ff5a5a red"), java.util.Map.entry("c.top1", "#ffd21a yellow"),
                java.util.Map.entry("c.top2", "#e6e6e6 white"), java.util.Map.entry("c.top3", "#ff9a3d gold"),
                java.util.Map.entry("cur.money", "#4dff88 green"), java.util.Map.entry("cur.crystals", "#33e0ff aqua"),
                java.util.Map.entry("cur.gems", "#2effc0 dark_green"), java.util.Map.entry("cur.shards", "#ff6b8a red"),
                java.util.Map.entry("cur.credits", "#ff8c1a gold"),
                java.util.Map.entry("rarity.common", "#f2f2f2 white"), java.util.Map.entry("rarity.uncommon", "#4dff6a green"),
                java.util.Map.entry("rarity.rare", "#3da5ff blue"), java.util.Map.entry("rarity.epic", "#c05cff dark_purple"),
                java.util.Map.entry("rarity.legendary", "#ffb414 gold"), java.util.Map.entry("rarity.mythic", "#ff4fd8 light_purple"),
                java.util.Map.entry("rarity.special", "#ff2d3f red"),
                java.util.Map.entry("pillar.mining", "#d0d8e4 gray"), java.util.Map.entry("pillar.foraging", "#7cff4d green"),
                java.util.Map.entry("pillar.farming", "#ffe04d yellow"), java.util.Map.entry("pillar.combat", "#ff4d4d red"),
                java.util.Map.entry("pillar.conquest", "#ff9a3d gold"), java.util.Map.entry("pillar.fishing", "#3dd8ff aqua"));
        expected.forEach((key, value) -> {
            String[] parts = key.split("\\.");
            Style.Color c = style.colors().get(parts[0]).get(parts[1]);
            assertTrue(c != null, key + " missing");
            assertEquals(value, (c.hex().asHexString() + " " + c.legacy()).toLowerCase(java.util.Locale.ROOT), key);
        });
        assertEquals("<gradient:#ff2d3f:#ff6a1a:#ffc21a>p</gradient>", style.preprocess("<grad:phoenix>P</grad>", false).toLowerCase(java.util.Locale.ROOT));
        assertEquals("<color:red>P</color>", style.preprocess("<grad:phoenix>P</grad>", true));
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

    @Test
    void fullResolverIsCachedUntilReload(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("style.yml");
        Files.writeString(file, YAML);
        Style style = new Style();
        style.load(file.toFile());
        net.kyori.adventure.text.minimessage.MiniMessage mm = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage();
        var java = style.fullResolver(false, mm);
        assertSame(java, style.fullResolver(false, mm), "same resolver until the style changes");
        assertNotSame(java, style.fullResolver(true, mm), "Bedrock has its own resolver");
        var plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();
        assertEquals("P hi", plain.serialize(mm.deserialize("<prefix>hi", java)));
        Files.writeString(file, YAML.replace("prefix: \"<c:brand>P</c:brand> \"", "prefix: \"Q \""));
        style.load(file.toFile());
        var reloaded = style.fullResolver(false, mm);
        assertNotSame(java, reloaded, "a reload drops the cache");
        assertEquals("Q hi", plain.serialize(mm.deserialize("<prefix>hi", reloaded)));
    }
}
