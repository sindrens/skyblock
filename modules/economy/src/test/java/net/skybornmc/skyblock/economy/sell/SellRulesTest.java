package net.skybornmc.skyblock.economy.sell;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Unsellable filtering: unique items never sell, custom items only by their own entry, blocked ids/data, types. */
class SellRulesTest {

    private static final List<String> BLOCKED = List.of("money_note", "starter_*", "tool_*");
    private static final List<String> TYPES = List.of("material");

    private static SellCatalog catalog() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("categories.ores.coal", 10);
        yaml.set("categories.ores.diamond", java.util.Map.of("price", 61, "sellable", false));
        yaml.set("categories.ores.paper", 1);
        yaml.set("custom-items.depths.depths_coal", 20);
        yaml.set("custom-items.depths.enchant_book", 500);  // someone listed a unique item by mistake
        yaml.set("custom-items.depths.money_note", 1);
        yaml.set("custom-items.depths.shiny_pick", 100);
        return SellCatalog.load(yaml, id -> true, p -> { });
    }

    private static Optional<SellRules.Reason> check(SellRules.ItemFacts facts) {
        return SellRules.check(facts, catalog(), BLOCKED, TYPES);
    }

    @Test
    void vanillaItemsInTheTableSell() {
        assertTrue(check(SellRules.ItemFacts.vanilla("coal")).isEmpty());
        assertEquals(Optional.of(SellRules.Reason.NOT_IN_TABLE), check(SellRules.ItemFacts.vanilla("dirt")));
        assertEquals(Optional.of(SellRules.Reason.DISABLED), check(SellRules.ItemFacts.vanilla("diamond")));
        assertEquals(Optional.of(SellRules.Reason.EMPTY), check(SellRules.ItemFacts.vanilla(null)));
        assertEquals(Optional.of(SellRules.Reason.EMPTY), check(null));
    }

    @Test
    void customItemsArePricedOnlyByTheirOwnEntry() {
        assertTrue(check(SellRules.ItemFacts.custom("coal", "depths_coal", "material", false)).isEmpty());
        assertEquals("custom:depths_coal", SellRules.ItemFacts.custom("coal", "depths_coal", "material", false).key());
        // A custom item made of coal is NOT sold as vanilla coal.
        assertEquals(Optional.of(SellRules.Reason.NOT_IN_TABLE), check(SellRules.ItemFacts.custom("coal", "event_coal", "material", false)));
    }

    @Test
    void uniqueItemsNeverSellEvenWhenListed() {
        assertEquals(Optional.of(SellRules.Reason.UNIQUE), check(SellRules.ItemFacts.custom("book", "enchant_book", "material", true)));
        assertEquals(Optional.of(SellRules.Reason.UNIQUE), check(SellRules.ItemFacts.custom("coal", "depths_coal", "material", true)));
        // Money notes: unique, and blocked by id even without the unique id.
        assertEquals(Optional.of(SellRules.Reason.UNIQUE), check(SellRules.ItemFacts.custom("paper", "money_note", "note", true)));
        assertEquals(Optional.of(SellRules.Reason.BLOCKED), check(SellRules.ItemFacts.custom("paper", "money_note", "material", false)));
    }

    @Test
    void toolsSetPiecesAndUnknownTypesNeverSell() {
        assertEquals(Optional.of(SellRules.Reason.CUSTOM_TYPE), check(SellRules.ItemFacts.custom("iron_pickaxe", "shiny_pick", "tool", false)));
        assertEquals(Optional.of(SellRules.Reason.CUSTOM_TYPE), check(SellRules.ItemFacts.custom("iron_pickaxe", "shiny_pick", "set-piece", false)));
        assertEquals(Optional.of(SellRules.Reason.CUSTOM_TYPE), check(SellRules.ItemFacts.custom("iron_pickaxe", "shiny_pick", null, false)));
        assertEquals(Optional.of(SellRules.Reason.BLOCKED), check(SellRules.ItemFacts.custom("wooden_pickaxe", "starter_pickaxe", "material", false)));
        assertEquals(Optional.of(SellRules.Reason.BLOCKED), check(SellRules.ItemFacts.custom("iron_pickaxe", "tool_pickaxe", "material", false)));
    }

    @Test
    void dupesAndBlockedDataNeverSell() {
        assertEquals(Optional.of(SellRules.Reason.DUPE), check(new SellRules.ItemFacts("coal", null, null, false, true, false)));
        assertEquals(Optional.of(SellRules.Reason.BLOCKED), check(new SellRules.ItemFacts("coal", null, null, false, false, true)));
    }

    @Test
    void blockedIdPatterns() {
        assertTrue(SellRules.idBlocked("STARTER_axe", BLOCKED));
        assertTrue(SellRules.idBlocked("money_note", BLOCKED));
        assertFalse(SellRules.idBlocked("money_notes", BLOCKED));
        assertFalse(SellRules.idBlocked("depths_coal", BLOCKED));
        assertFalse(SellRules.idBlocked(null, BLOCKED));
        assertTrue(SellRules.typeAllowed("Material", TYPES));
        assertFalse(SellRules.typeAllowed("consumable", TYPES));
    }

    @Test
    void reasonKeysMatchTheMessages() {
        assertEquals("not-in-table", SellRules.Reason.NOT_IN_TABLE.key());
        assertEquals("custom-type", SellRules.Reason.CUSTOM_TYPE.key());
    }
}
