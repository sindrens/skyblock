package net.skybornmc.skyblock.progression;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PHASE-7 M7.3: Crop Growth and Harvest Yield turn live only while farming is on (like the Generator and Fuel Tank). */
class FarmingFeatureTest {

    @AfterEach
    void off() {
        FarmingFeature.source(null);
        GeneratorFeature.source(null);
        IslandFuelFeature.source(null);
    }

    @Test
    void cropUpgradesAreComingSoonUntilFarmingIsOn() {
        UpgradeCatalog.Def growth = new UpgradeCatalog.Def("crop_growth", UpgradeTab.PRODUCTION, "wheat", 4, UpgradeEffect.CROP_GROWTH, true,
                List.of(new UpgradeCatalog.Level(1, 4_220, 12, 0)));
        UpgradeCatalog.Def yield = new UpgradeCatalog.Def("harvest_yield", UpgradeTab.PRODUCTION, "hay_block", 5, UpgradeEffect.HARVEST_YIELD, true,
                List.of(new UpgradeCatalog.Level(1, 5_490, 12, 0)));
        assertEquals(UpgradeRules.Status.COMING_SOON, UpgradeRules.status(growth, 0, true, 99, true, 1_000_000));
        assertEquals(UpgradeRules.Status.COMING_SOON, UpgradeRules.status(yield, 0, true, 99, true, 1_000_000));
        IslandFuelFeature.source(() -> true);
        GeneratorFeature.source(() -> true);
        assertFalse(UpgradeEffect.CROP_GROWTH.live(), "fuel and generators alone don't switch farming on");
        assertFalse(UpgradeEffect.HARVEST_YIELD.live());
        FarmingFeature.source(() -> true);
        assertTrue(UpgradeEffect.CROP_GROWTH.live());
        assertTrue(UpgradeEffect.HARVEST_YIELD.live());
        assertFalse(UpgradeEffect.MOB_LOOT.live(), "Mob Loot waits for M7.7");
        assertEquals(UpgradeRules.Status.READY, UpgradeRules.status(growth, 0, true, 99, true, 1_000_000));
        assertEquals(UpgradeRules.Status.READY, UpgradeRules.status(yield, 0, true, 99, true, 1_000_000));
    }
}
