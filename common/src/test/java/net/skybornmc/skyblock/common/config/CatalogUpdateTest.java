package net.skybornmc.skyblock.common.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Owner 7 Nov 2026: a changed bundled price reaches a live catalog once, an owner price stays. */
class CatalogUpdateTest {

    static final Map<Integer, Map<String, CatalogUpdate.Change>> STEPS = Map.of(3, Map.of(
            "crops.wheat", CatalogUpdate.Change.of(1200, 700),
            "crops.carrot", CatalogUpdate.Change.of(1500, 900),
            "rows.seeds", CatalogUpdate.Change.of(List.of("a", "b"), List.of("b", "a")),
            "crops.gone", CatalogUpdate.Change.of(1, 2)));

    @Test
    void untouchedValuesChangeOnceOwnerValuesStay() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("crops.wheat", 1200);
        y.set("crops.carrot", 2000); // the owner's price
        y.set("rows.seeds", List.of("a", "b"));
        List<String> log = CatalogUpdate.apply(y, STEPS);
        assertEquals(700, y.getInt("crops.wheat"));
        assertEquals(2000, y.getInt("crops.carrot"));
        assertEquals(List.of("b", "a"), y.getStringList("rows.seeds"));
        assertTrue(!y.contains("crops.gone"), "a missing path is not created");
        assertEquals(2, log.size());
        assertEquals(3, CatalogUpdate.version(y));
        y.set("crops.wheat", 1200);
        assertTrue(CatalogUpdate.apply(y, STEPS).isEmpty(), "once");
        assertEquals(1200, y.getInt("crops.wheat"));
    }

    @Test
    void aNullTargetRemovesAnUntouchedEntry() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("spawners.hoglin", 7_500_000);
        y.set("spawners.husk", 9_000_000); // the owner's price: stays
        CatalogUpdate.apply(y, Map.of(9, Map.of("spawners.hoglin", CatalogUpdate.Change.of(7_500_000L, null),
                "spawners.husk", CatalogUpdate.Change.of(12_500_000L, null))));
        assertTrue(!y.contains("spawners.hoglin"));
        assertEquals(9_000_000, y.getInt("spawners.husk"));
    }

    @Test
    void numbersCompareByValue() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("crops.wheat", 1200L);
        CatalogUpdate.apply(y, STEPS);
        assertEquals(700, y.getInt("crops.wheat"));
    }
}
