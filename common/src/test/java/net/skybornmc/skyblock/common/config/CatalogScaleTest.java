package net.skybornmc.skyblock.common.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogScaleTest {

    private static final List<CatalogScale.Path> PATHS = List.of(
            CatalogScale.Path.of("categories.*.*", MoneyScale.number()),
            CatalogScale.Path.of("upgrades.*.levels", CatalogScale.mapList("cost", MoneyScale.number())),
            CatalogScale.Path.when("tasks.*.amount", "type", Set.of("earn", "deposit"), MoneyScale.number()),
            CatalogScale.Path.of("quests", CatalogScale.mapList("rewards", CatalogScale.moneyLines())));

    @Test
    void everyKindOfEntryIsScaledOnce() throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString("""
                categories:
                  ores:
                    coal: 10
                    emerald: {price: 82, sellable: false}
                    leaves: auto
                upgrades:
                  size:
                    levels:
                    - {tier: 1, cost: 3350, value: 5}
                tasks:
                  a: {type: earn, amount: 25000}
                  b: {type: sell, amount: 500}
                quests:
                - {id: x, rewards: ["money 750", "crystals 10"]}
                """);
        List<String> log = CatalogScale.apply(y, PATHS);
        assertEquals(1000, y.getInt("categories.ores.coal"));
        assertEquals(8200, y.getInt("categories.ores.emerald.price"));
        assertEquals("auto", y.getString("categories.ores.leaves"));
        assertEquals(335000, ((Map<?, ?>) y.getMapList("upgrades.size.levels").getFirst()).get("cost"));
        assertEquals(2_500_000, y.getInt("tasks.a.amount"));
        assertEquals(500, y.getInt("tasks.b.amount"), "sell tasks count items");
        assertEquals(List.of("money 75000", "crystals 10"), y.getMapList("quests").getFirst().get("rewards"));
        assertEquals(5, log.size(), log.toString());
        assertTrue(CatalogScale.apply(y, PATHS).isEmpty(), "once");
        assertEquals(1000, y.getInt("categories.ores.coal"));
    }
}
