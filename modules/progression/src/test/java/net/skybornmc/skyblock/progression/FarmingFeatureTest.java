package net.skybornmc.skyblock.progression;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PHASE-7 M7.3 + owner 4 Nov 2026: Crop Growth and Harvest Yield are always live (the island tools read them). */
class FarmingFeatureTest {

    @AfterEach
    void off() {
        FarmingFeature.source(null);
        GeneratorFeature.source(null);
        IslandFuelFeature.source(null);
    }

    /** Owner 4 Nov 2026: the island tools read Crop Growth and Harvest Yield, so they are buyable whether farming is on or not. */
    @Test
    void cropUpgradesAreLiveForTheIslandTools() {
        UpgradeCatalog.Def growth = new UpgradeCatalog.Def("crop_growth", UpgradeTab.PRODUCTION, "wheat", 4, UpgradeEffect.CROP_GROWTH, true,
                List.of(new UpgradeCatalog.Level(1, 4_220, 12, 0)));
        UpgradeCatalog.Def yield = new UpgradeCatalog.Def("harvest_yield", UpgradeTab.PRODUCTION, "hay_block", 5, UpgradeEffect.HARVEST_YIELD, true,
                List.of(new UpgradeCatalog.Level(1, 5_490, 12, 0)));
        assertTrue(UpgradeEffect.CROP_GROWTH.live());
        assertTrue(UpgradeEffect.HARVEST_YIELD.live());
        assertTrue(UpgradeEffect.MINE_FORTUNE.live());
        assertFalse(UpgradeEffect.MOB_LOOT.live(), "Mob Loot waits for M7.7");
        assertEquals(UpgradeRules.Status.READY, UpgradeRules.status(growth, 0, true, 99, true, 1_000_000));
        assertEquals(UpgradeRules.Status.READY, UpgradeRules.status(yield, 0, true, 99, true, 1_000_000));
        FarmingFeature.source(() -> true);
        assertTrue(UpgradeEffect.HARVEST_YIELD.live());
    }
}
