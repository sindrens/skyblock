package net.skybornmc.skyblock.economy.sell;

import net.skybornmc.skyblock.economy.EconomyConfig;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ECON-SELL-SHOP-AH A1/A7: the price knob scales listed, custom and root prices; decimals; new keys and defaults. */
class SellKnobTest {

    @Test
    void theMultiplierScalesEveryListedPrice() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("categories.ores.coal", 10);
        yaml.set("categories.ores.redstone", 2.5);
        yaml.set("custom-items.drops.mobdrop_pig", 5);
        yaml.set("roots.dirt", 0.5);
        yaml.set("roots.coal", 99);
        List<String> problems = new ArrayList<>();
        SellCatalog c = SellCatalog.load(yaml, id -> true, 1.5, problems::add);
        assertEquals(List.of(), problems);
        assertEquals(15.0, c.get("coal").orElseThrow().base(), 1e-9, "a listed item keeps its listed price, not the root one");
        assertEquals(3.75, c.get("redstone").orElseThrow().base(), 1e-9, "decimals are allowed");
        assertEquals(7.5, c.get("custom:mobdrop_pig").orElseThrow().base(), 1e-9);
        assertEquals(0.75, c.get("dirt").orElseThrow().base(), 1e-9);
        assertEquals(SellCatalog.ROOTS_CATEGORY, c.get("dirt").orElseThrow().category());
    }

    @Test
    void derivedPricesJoinTheCatalogOnlyWhenLower() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("categories.ores.stone", 5);
        SellCatalog c = SellCatalog.load(yaml, id -> true, p -> { }).withDerived(Map.of("stone", 1.0, "oak_planks", 0.22, "x", 9.0));
        assertEquals(1.0, c.get("stone").orElseThrow().base(), 1e-9);
        assertEquals("ores", c.get("stone").orElseThrow().category());
        assertEquals(0.22, c.get("oak_planks").orElseThrow().base(), 1e-9);
        assertTrue(c.get("oak_planks").orElseThrow().sellable());
    }

    @Test
    void newKeysHaveTheSpecDefaults() {
        EconomyConfig e = new EconomyConfig();
        assertEquals(1.0, e.sellPriceMultiplier, 1e-9);
        assertTrue(e.sellDeriveEnabled);
        assertEquals(6, e.sellDeriveMaxDepth);
        assertEquals(500.0, e.hotMinBase, 1e-9);
        assertTrue(e.hotAnnounce && e.hotJoinLine && e.marketTipsButton);
        assertEquals(500.0, e.marketTipsMinBase, 1e-9);
        assertEquals(5.0, e.marketTipsMinPercent, 1e-9);
        assertEquals(5, e.marketTipsSize);
        assertEquals(60, e.marketTipsRefreshSeconds);
    }
}
