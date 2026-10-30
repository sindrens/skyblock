package net.skybornmc.skyblock.economy.sell;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The bundled price table and the parser. */
class SellCatalogTest {

    static YamlConfiguration bundled() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        try (InputStream in = SellCatalogTest.class.getClassLoader().getResourceAsStream("sell.yml")) {
            yaml.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return yaml;
    }

    @Test
    void bundledTableLoadsWithoutProblems() throws Exception {
        List<String> problems = new ArrayList<>();
        SellCatalog catalog = SellCatalog.load(bundled(), id -> true, problems::add);
        assertTrue(problems.isEmpty(), () -> String.join("\n", problems));
        assertTrue(catalog.size() > 50);
        // ECONOMY_BALANCE §4.1 / §3.1 values.
        assertEquals(10, catalog.get("wheat").orElseThrow().base());
        assertEquals(10, catalog.get("coal").orElseThrow().base());
        assertEquals(870, catalog.get("pitcher_plant").orElseThrow().base());
        assertEquals(20, catalog.get("custom:depths_coal").orElseThrow().base());
        assertEquals(3829, catalog.get("custom:depths_voidstone").orElseThrow().base());
        assertEquals("depths-mine", catalog.get("custom:depths_coal").orElseThrow().category());
        assertFalse(catalog.get("emerald").orElseThrow().sellable());
        assertFalse(catalog.sellableKeys().contains("emerald"));
    }

    @Test
    void bundledTableSaysTheOwnerApprovesPrices() throws Exception {
        String text = Files.readString(Path.of(SellCatalogTest.class.getClassLoader().getResource("sell.yml").toURI()));
        assertTrue(text.contains("THE OWNER APPROVES THESE PRICES BEFORE LAUNCH"));
    }

    @Test
    void noCraftOrSmeltProfitsInTheDefaults() throws Exception {
        SellCatalog c = SellCatalog.load(bundled(), id -> true, p -> { });
        // Ingots never above raw ore; crafted goods and storage blocks are not listed.
        assertTrue(c.get("iron_ingot").orElseThrow().base() <= c.get("raw_iron").orElseThrow().base());
        for (String crafted : List.of("arrow", "sugar", "paper", "stick", "bone_meal", "coal_block", "iron_block", "hay_block",
                "gold_ingot", "copper_ingot", "white_wool", "bread", "melon")) {
            assertTrue(c.get(crafted).isEmpty(), crafted + " must not be sellable (crafted from cheaper sellable items)");
        }
    }

    @Test
    void parserReportsBrokenEntriesAndKeepsTheRest() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("categories.ores.coal", 10);
        yaml.set("categories.ores.diamond", Map.of("price", 61, "sellable", false));
        yaml.set("categories.ores.Bad-Id", 5);
        yaml.set("categories.ores.zero", 0);
        yaml.set("categories.ores.text", "lots");
        yaml.set("categories.ores.fake_item", 3);
        yaml.set("categories.crops.coal", 99);
        yaml.set("custom-items.depths.depths_coal", 20);
        List<String> problems = new ArrayList<>();
        SellCatalog c = SellCatalog.load(yaml, id -> !id.equals("fake_item"), problems::add);
        assertEquals(10, c.get("coal").orElseThrow().base());           // first one kept
        assertFalse(c.get("diamond").orElseThrow().sellable());
        assertEquals(20, c.get("custom:depths_coal").orElseThrow().base());
        assertTrue(c.get("zero").isEmpty());
        assertTrue(c.get("text").isEmpty());
        assertTrue(c.get("fake_item").isEmpty());
        assertEquals(5, problems.size(), () -> String.join("\n", problems)); // Bad-Id, zero, text, fake_item, duplicate coal
        assertEquals(List.of("ores", "depths"), c.categories());
    }

    @Test
    void missingFileOrSectionIsReported() {
        List<String> problems = new ArrayList<>();
        assertEquals(0, SellCatalog.load(null, id -> true, problems::add).size());
        assertEquals(0, SellCatalog.load(new YamlConfiguration(), id -> true, problems::add).size());
        assertEquals(2, problems.size());
    }

    @Test
    void newBundledEntriesAreAddedToAnOldFile() throws Exception {
        YamlConfiguration old = new YamlConfiguration();
        old.set("categories.ores.coal", 12);                 // owner changed a price
        old.set("categories.misc.wheat", 9);                 // owner moved wheat to another category
        assertTrue(SellFeature.addMissing(old, bundled()));
        assertEquals(12, old.getInt("categories.ores.coal"));
        assertFalse(old.contains("categories.crops.wheat"));
        assertTrue(old.contains("categories.crops.carrot"));
        assertTrue(old.contains("custom-items.depths-mine.depths_coal"));
        assertFalse(SellFeature.addMissing(old, bundled()));
    }
}
