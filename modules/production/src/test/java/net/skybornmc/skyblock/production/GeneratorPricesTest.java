package net.skybornmc.skyblock.production;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PHASE-7 M7.2: every generator drop has the plan's island price in the economy's sell.yml (ECONOMY_BALANCE §4.1), and the
 * two custom ores are priced as custom items ({@code custom:<id>}), so /sell knows them.
 */
class GeneratorPricesTest {

    private static YamlConfiguration sell() {
        File file = new File("../economy/src/main/resources/sell.yml");
        assertTrue(file.isFile(), "economy sell.yml next to this module");
        return YamlConfiguration.loadConfiguration(file);
    }

    private static long price(YamlConfiguration yml, String path) {
        Object v = yml.get(path);
        if (v instanceof Number n) {
            return n.longValue();
        }
        assertTrue(yml.isConfigurationSection(path), path + " is priced");
        return yml.getLong(path + ".price");
    }

    @Test
    void vanillaOreDropsSellAtThePlanPrices() {
        YamlConfiguration yml = sell();
        Map<String, Long> plan = new LinkedHashMap<>();
        plan.put("cobblestone", 1L);
        plan.put("coal", 10L);
        plan.put("raw_copper", 14L);
        plan.put("raw_iron", 18L);
        plan.put("raw_gold", 25L);
        plan.put("redstone", 33L);
        plan.put("lapis_lazuli", 45L);
        plan.put("diamond", 61L);
        plan.put("emerald", 82L);
        plan.forEach((item, p) -> assertEquals(p, price(yml, "categories.ores." + item), item));
    }

    @Test
    void customOresArePricedAsCustomItems() {
        YamlConfiguration yml = sell();
        GeneratorSettings s = new GeneratorSettings();
        GeneratorTable t = GeneratorTable.parse(s.ores, s.levels, p -> { });
        assertEquals(180, price(yml, "custom-items.island-ores.ashstone"));
        assertEquals(396, price(yml, "custom-items.island-ores.emberite"));
        for (GeneratorTable.Ore ore : t.ores().values()) {
            if (ore.custom()) {
                assertTrue(yml.contains("custom-items.island-ores." + ore.id()), ore.id() + " has a sell price");
            }
        }
    }
}
