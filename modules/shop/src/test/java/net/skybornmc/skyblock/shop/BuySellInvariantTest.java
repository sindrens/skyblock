package net.skybornmc.skyblock.shop;

import net.skybornmc.skyblock.economy.EconomyConfig;
import net.skybornmc.skyblock.economy.sell.DynamicSettings;
import net.skybornmc.skyblock.economy.sell.SaleCalculator;
import net.skybornmc.skyblock.economy.sell.SellCatalog;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The endless-money guard: nothing in shop.yml can be bought for less than it (or what it turns back into) can ever sell
 * for with /sell (sell.yml base x ceiling x Hot Item x sell cap, from the real economy defaults).
 */
class BuySellInvariantTest {

    /**
     * Shop items that turn back into /sell items (crafting back, breaking without silk touch, fortune maximum). If one of
     * these is in shop.yml, it must declare at least this in "contains" so the live check also catches price edits.
     */
    static final Map<String, Map<String, Integer>> KNOWN_YIELDS = Map.ofEntries(
            Map.entry("stone", Map.of("cobblestone", 1)),
            Map.entry("glowstone", Map.of("glowstone_dust", 4)),
            Map.entry("sea_lantern", Map.of("prismarine_crystals", 5)),
            Map.entry("melon", Map.of("melon_slice", 9)),
            Map.entry("hay_block", Map.of("wheat", 9)),
            Map.entry("slime_block", Map.of("slime_ball", 9)),
            Map.entry("cobweb", Map.of("string", 1)),
            Map.entry("redstone_block", Map.of("redstone", 9)),
            Map.entry("coal_block", Map.of("coal", 9)),
            Map.entry("iron_block", Map.of("iron_ingot", 9)),
            Map.entry("lapis_block", Map.of("lapis_lazuli", 9)),
            Map.entry("diamond_block", Map.of("diamond", 9)),
            Map.entry("emerald_block", Map.of("emerald", 9)),
            Map.entry("raw_iron_block", Map.of("raw_iron", 9)),
            Map.entry("raw_copper_block", Map.of("raw_copper", 9)),
            Map.entry("raw_gold_block", Map.of("raw_gold", 9)),
            Map.entry("coal_ore", Map.of("coal", 1)),
            Map.entry("redstone_ore", Map.of("redstone", 5)),
            Map.entry("lapis_ore", Map.of("lapis_lazuli", 9)),
            Map.entry("diamond_ore", Map.of("diamond", 4)),
            Map.entry("bone_block", Map.of()),
            // Seeds that grow 1:1 into an expensive sellable plant (no seed back): treated like the plant itself.
            Map.entry("torchflower_seeds", Map.of("torchflower", 1)),
            Map.entry("pitcher_pod", Map.of("pitcher_plant", 1)),
            Map.entry("nether_wart_block", Map.of()));

    static SellCatalog sellCatalog() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        try (InputStream in = BuySellInvariantTest.class.getClassLoader().getResourceAsStream("sell.yml")) {
            yaml.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return SellCatalog.load(yaml, id -> true, p -> { });
    }

    /** The most one item of a key can sell for with the economy's DEFAULT settings. */
    static Function<String, OptionalDouble> maxSell(SellCatalog sell, EconomyConfig eco) {
        DynamicSettings s = eco.dynamicSettings();
        return key -> sell.get(key).filter(SellCatalog.Entry::sellable)
                .map(e -> OptionalDouble.of(SaleCalculator.maxUnit(e.base(), s, eco.sellMultiplierCapPercent)))
                .orElse(OptionalDouble.empty());
    }

    @Test
    void nothingCanBeBoughtForLessThanItSells() throws Exception {
        SellCatalog sell = sellCatalog();
        assertTrue(sell.size() > 50, "sell.yml was found");
        Function<String, OptionalDouble> max = maxSell(sell, new EconomyConfig());
        assertEquals(7.5, max.apply("coal").getAsDouble() / 10, 1e-9, "defaults: x1.5 ceiling, x1.25 hot, x4 cap");
        List<String> loops = new ArrayList<>();
        for (ShopCatalog.Item item : ShopCatalogTest.catalog().items()) {
            Map<String, Integer> parts = new HashMap<>(item.contains());
            KNOWN_YIELDS.getOrDefault(item.id(), Map.of()).forEach((k, v) -> parts.merge(k, v, Math::max));
            double value = ShopMath.maxSellValue(item.id(), parts, max);
            if (item.price() <= value) {
                loops.add(item.id() + " costs " + item.price() + " but sells for up to " + value);
            }
        }
        assertTrue(loops.isEmpty(), () -> "Infinite money loops in shop.yml:\n" + String.join("\n", loops));
    }

    @Test
    void knownConversionsAreDeclaredSoTheLiveCheckSeesThem() throws Exception {
        List<String> missing = new ArrayList<>();
        for (ShopCatalog.Item item : ShopCatalogTest.catalog().items()) {
            KNOWN_YIELDS.getOrDefault(item.id(), Map.of()).forEach((k, v) -> {
                if (item.contains().getOrDefault(k, 0) < v) {
                    missing.add(item.id() + " needs contains: { " + k + ": " + v + " }");
                }
            });
        }
        assertTrue(missing.isEmpty(), () -> String.join("\n", missing));
    }

    @Test
    void theCheckCatchesALoop() throws Exception {
        Function<String, OptionalDouble> max = maxSell(sellCatalog(), new EconomyConfig());
        assertTrue(ShopMath.undercutsSell("carrot", 100, Map.of(), max), "carrot sells for up to 105");
        assertTrue(ShopMath.undercutsSell("hay_block", 600, Map.of("wheat", 9), max), "9 wheat sell for up to 675");
    }
}
