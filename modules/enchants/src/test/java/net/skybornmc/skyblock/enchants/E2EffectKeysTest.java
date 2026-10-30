package net.skybornmc.skyblock.enchants;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Every enchants.yml number the E2 effects read exists for every level of that enchant (a typo would silently mean 0). */
class E2EffectKeysTest {

    private static final String[][] KEYS = {
            {"gilded_touch", "chance", "multiplier"}, {"geode_sense", "chance", "worth"}, {"amber_sap", "chance", "worth"},
            {"vein_rend", "chance", "count", "radius", "yield"}, {"dragonfire_blast", "chance", "depth", "size", "yield"},
            {"worldbreaker", "every", "size", "depth", "yield"}, {"treefeller", "count", "cooldown", "yield"},
            {"wildfire", "chance", "trees", "radius", "yield"}, {"ashen_grove", "every", "radius", "yield"},
            {"ember_momentum", "percent", "actions", "idle-seconds", "max-stacks"}, {"phoenix_haste", "chance", "seconds", "percent", "cooldown"},
            {"phoenix_edge", "percent"}, {"giant_slayer", "percent"}, {"bloodthirst", "percent"}, {"ember_strike", "chance", "seconds"},
            {"cleave", "percent", "radius"}, {"executioner", "health"}, {"phoenix_wrath", "every", "damage", "radius"},
            {"plunder", "chance"}};

    @Test
    void effectNumbersExist() {
        EnchantRegistry registry = Fixtures.registry();
        List<String> missing = new ArrayList<>();
        for (String[] row : KEYS) {
            EnchantDef def = registry.get(row[0]).orElse(null);
            if (def == null) {
                missing.add(row[0]);
                continue;
            }
            for (int i = 1; i < row.length; i++) {
                for (int level = 1; level <= def.maxLevel(); level++) {
                    if (!Double.isFinite(def.value(row[i], level))) {
                        missing.add(row[0] + "." + row[i] + " @" + level);
                    }
                }
            }
        }
        assertEquals(List.of(), missing);
    }

    @Test
    void pvpValuesFollowThePlan() {
        EnchantRegistry registry = Fixtures.registry();
        assertEquals(40, registry.get("phoenix_edge").orElseThrow().pvpPercent(), 1e-9);
        assertEquals(50, registry.get("bloodthirst").orElseThrow().pvpPercent(), 1e-9);
        assertEquals(0, registry.get("cleave").orElseThrow().pvpPercent(), 1e-9);
        assertEquals(0, registry.get("executioner").orElseThrow().pvpPercent(), 1e-9);
    }
}
