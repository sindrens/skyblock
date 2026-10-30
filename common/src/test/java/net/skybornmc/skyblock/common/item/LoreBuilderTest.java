package net.skybornmc.skyblock.common.item;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoreBuilderTest {

    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("lore.description", "D:{text}"),
            Map.entry("lore.stats", "STATS"),
            Map.entry("lore.stat", "S:{color}:{label}:{value}"),
            Map.entry("lore.enchants", "ENCHANTS"),
            Map.entry("lore.perk-slots", "PERKS {used}/{total}"),
            Map.entry("lore.enchant", "E:{name}"),
            Map.entry("lore.empty-slot", "EMPTY"),
            Map.entry("lore.action", "A:{input}:{text}"),
            Map.entry("lore.level", "LV {level} {bar} {percent}%"),
            Map.entry("lore.level-bar", "[{filled}|{empty}]"),
            Map.entry("lore.level-bar-filled", "#"),
            Map.entry("lore.level-bar-empty", "-"),
            Map.entry("lore.footer", "F:{rarity}:{rarity-name} {type-name}"),
            Map.entry("rarity.rare", "RARE"),
            Map.entry("type.tool", "TOOL"),
            Map.entry("type.crate-key", "CRATE KEY"),
            Map.entry("lore.name", "N:{rarity}:{name}"),
            Map.entry("lore.section", "H:{title}"),
            Map.entry("lore.section-row", "R:{label}={value}"),
            Map.entry("lore.section-line", "T:{text}"),
            Map.entry("lore.buy-price", "BUY {price}{unit}"),
            Map.entry("lore.sell-price", "SELL {price}{unit}"),
            Map.entry("lore.price-each", " each"),
            Map.entry("lore.afford", "AFFORD {amount}"),
            Map.entry("lore.progress", "P {current}/{max}"),
            Map.entry("lore.progress-bar", "[{filled}|{empty}] {percent}%"),
            Map.entry("lore.progress-bar-filled", "#"),
            Map.entry("lore.progress-bar-empty", "-"),
            Map.entry("lore.rewards", "REWARDS"),
            Map.entry("lore.reward", "W:{rarity}:{name}:x{amount}:{chance}%"),
            Map.entry("lore.quest-reward", "[+] {reward}"));

    private static final Function<String, String> L = key -> LABELS.getOrDefault(key, "?" + key);

    @Test
    void fullTemplateInOrderWithSingleBlankLines() {
        List<String> lore = new LoreBuilder(L)
                .subtitle("Tool")
                .description("Short text")
                .level(23, 0.62)
                .stat("pillar:mining", "Speed", "+12%")
                .enchant("Fortune III")
                .perkSlots(1, 3)
                .action("Right-click", "to open")
                .footer(Rarity.RARE, ItemType.TOOL)
                .build();
        assertEquals(List.of(
                "Tool", "",
                "D:Short text", "",
                "LV 23 [######|----] 62%", "",
                "STATS", "S:pillar:mining:Speed:+12%", "",
                "PERKS 1/3", "E:Fortune III", "EMPTY", "EMPTY", "",
                "A:RIGHT-CLICK:to open", "",
                "F:rare:RARE TOOL"), lore);
    }

    @Test
    void emptySectionsAreSkipped() {
        assertEquals(List.of("D:Hi", "", "F:rare:RARE TOOL"),
                new LoreBuilder(L).description("Hi").footer(Rarity.RARE, ItemType.TOOL).build());
    }

    @Test
    void menuItemsHaveNoFooter() {
        assertEquals(List.of("A:CLICK:to go"), new LoreBuilder(L).action("Click", "to go").footer(Rarity.RARE, ItemType.MENU).build());
    }

    @Test
    void dynamicValuesAreEscaped() {
        List<String> lore = new LoreBuilder(L).enchant("<red>evil").build();
        assertTrue(lore.get(1).contains("\\<red>"), lore.get(1));
        assertFalse(lore.get(1).replace("\\<red>", "").contains("<red>"), lore.get(1));
    }

    @Test
    void descriptionWrapsToTwoLines() {
        List<String> lines = LoreBuilder.wrap("one two three four five six seven eight nine ten eleven twelve thirteen", 20, 2);
        assertEquals(2, lines.size());
        assertTrue(lines.getLast().endsWith("..."));
        lines.forEach(l -> assertTrue(l.length() <= 20, l));
        assertEquals(List.of("short"), LoreBuilder.wrap("short", 32, 2));
    }

    /** STYLE2: every V2 block in the approved order, empty sections dropped, single blank lines. */
    @Test
    void v2SectionsInOrder() {
        LoreBuilder b = new LoreBuilder(L)
                .action("Right-click", "buy 64")
                .reward(Rarity.EPIC, "Inferno Key", 2, 12.5)
                .questReward("$5,000")
                .progress(23, 50)
                .buyPrice("$8", true)
                .sellPrice("$2", false)
                .afford("12")
                .description("Short text")
                .subtitle("Shop item")
                .footer(Rarity.RARE, ItemType.CRATE_KEY);
        b.section("Information").row("Tier", "3").text("plain");
        b.section("Empty"); // no rows: dropped
        assertEquals(List.of(
                "Shop item", "",
                "D:Short text", "",
                "BUY $8 each", "SELL $2", "AFFORD 12", "",
                "H:Information", "R:Tier=3", "T:plain", "",
                "P 23/50", "[####|------] 46%", "",
                "REWARDS", "W:epic:Inferno Key:x2:12.5%", "[+] $5,000", "",
                "A:RIGHT-CLICK:buy 64", "",
                "F:rare:RARE CRATE KEY"), b.build());
    }

    @Test
    void actionsAreAlwaysLastBeforeTheFooter() {
        List<String> lore = new LoreBuilder(L).action("Click", "to open").description("Text").footer(Rarity.RARE, ItemType.TOOL).build();
        assertEquals("A:CLICK:to open", lore.get(lore.size() - 3));
        assertEquals("", lore.get(lore.size() - 2));
    }

    @Test
    void onlyPricesStayWhenNothingElse() {
        assertEquals(List.of("SELL $1.5k each"), new LoreBuilder(L).sellPrice("$1.5k", true).build());
    }

    @Test
    void progressIsClampedAndFormatted() {
        assertEquals(List.of("P 1,200/1,000", "[##########|] 100%"), new LoreBuilder(L).progress(1200, 1000).build());
        assertEquals(List.of("P 0/0", "[|----------] 0%"), new LoreBuilder(L).progress(0, 0).build());
    }

    @Test
    void chanceKeepsAtMostTwoDecimals() {
        assertEquals("12.5", LoreBuilder.chance(12.5));
        assertEquals("0.05", LoreBuilder.chance(0.0499));
        assertEquals("50", LoreBuilder.chance(50.0));
        assertEquals("0", LoreBuilder.chance(Double.NaN));
    }

    @Test
    void rewardAndSectionValuesAreEscaped() {
        LoreBuilder b = new LoreBuilder(L).reward(Rarity.RARE, "<bold>x", 1, 1);
        b.section("S").row("<red>a", "<red>b");
        String all = String.join("\n", b.build());
        assertFalse(all.replace("\\<", "").contains("<red>"), all);
        assertFalse(all.replace("\\<", "").contains("<bold>"), all);
    }

    @Test
    void nameIsRarityAndTrustedName() {
        assertEquals("N:legendary:Phoenix Key", LoreBuilder.name(L, Rarity.LEGENDARY, "Phoenix Key"));
    }

    @Test
    void descriptionsWrapWithinTheLineWidth() {
        List<String> lore = new LoreBuilder(L).description("A long description that keeps going and going so it has to wrap over several lines here and then it still goes on past the limit").build();
        assertEquals(3, lore.size());
        lore.forEach(l -> assertTrue(l.length() - 2 <= LoreBuilder.DESCRIPTION_WIDTH, l));
        assertTrue(lore.getLast().endsWith("..."));
        LoreBuilder.wrap("a b c d e f g h i j k l m n o p q r s t u v w x y z", 5).forEach(l -> assertTrue(l.length() <= 5, l));
    }

    /** The real bundled formats: no italics, Bedrock-safe, every key the builder asks for exists. */
    @Test
    void bundledFormatsExist() throws Exception {
        var yaml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(java.nio.file.Path.of("../plugin/src/main/resources/messages/items.yml").toFile());
        for (String key : LABELS.keySet()) {
            if (key.startsWith("lore.")) {
                assertTrue(yaml.isString(key), "messages/items.yml misses " + key);
                assertFalse(yaml.getString(key).contains("<italic>") || yaml.getString(key).contains("<i>"), key);
            }
        }
        assertTrue(yaml.getString("lore.buy-price").contains("<c:buy>| Buy Price: "));
        assertTrue(yaml.getString("lore.sell-price").contains("<c:sell>| Sell Price: "));
        assertTrue(yaml.getString("lore.action").startsWith("<bold><c:action>» "));
    }

    @Test
    void itemIdValidation() {
        assertTrue(ItemDefinition.isValidId("tool_iron_pickaxe"));
        assertFalse(ItemDefinition.isValidId("Tool-Iron"));
        assertFalse(ItemDefinition.isValidId(""));
        assertFalse(ItemDefinition.isValidId(null));
    }

    @Test
    void duplicatesAreGroupedByUuid() {
        java.util.UUID a = java.util.UUID.randomUUID();
        java.util.UUID b = java.util.UUID.randomUUID();
        java.util.UUID p = java.util.UUID.randomUUID();
        var copies = List.of(
                new DupeDetector.Copy(a, "x", "s", "inv 1", p, null, -1),
                new DupeDetector.Copy(b, "x", "s", "inv 2", p, null, -1),
                new DupeDetector.Copy(a, "x", "s", "ender 0", p, null, -1));
        var dupes = DupeDetector.findDuplicates(copies);
        assertEquals(1, dupes.size());
        assertEquals(2, dupes.get(a).size());
    }
}
