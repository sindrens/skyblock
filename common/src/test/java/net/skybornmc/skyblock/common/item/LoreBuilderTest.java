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
            Map.entry("type.tool", "TOOL"));

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
                "A:Right-click:to open", "",
                "F:rare:RARE TOOL"), lore);
    }

    @Test
    void emptySectionsAreSkipped() {
        assertEquals(List.of("D:Hi", "", "F:rare:RARE TOOL"),
                new LoreBuilder(L).description("Hi").footer(Rarity.RARE, ItemType.TOOL).build());
    }

    @Test
    void menuItemsHaveNoFooter() {
        assertEquals(List.of("A:Click:to go"), new LoreBuilder(L).action("Click", "to go").footer(Rarity.RARE, ItemType.MENU).build());
    }

    @Test
    void dynamicValuesAreEscaped() {
        List<String> lore = new LoreBuilder(L).enchant("<red>evil").build();
        assertFalse(lore.get(1).contains("<red>"));
        assertTrue(lore.get(1).contains("\\<red>"));
    }

    @Test
    void descriptionWrapsToTwoLines() {
        List<String> lines = LoreBuilder.wrap("one two three four five six seven eight nine ten eleven twelve thirteen", 20, 2);
        assertEquals(2, lines.size());
        assertTrue(lines.getLast().endsWith("..."));
        lines.forEach(l -> assertTrue(l.length() <= 20, l));
        assertEquals(List.of("short"), LoreBuilder.wrap("short", 32, 2));
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
