package net.skybornmc.skyblock.progression;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PHASE-7 M7.1: the Fuel Tank upgrade and the Fuel Efficiency mastery (MA8) turn live only while Island Fuel is on. */
class IslandFuelFeatureTest {

    @AfterEach
    void off() {
        IslandFuelFeature.source(null);
    }

    @Test
    void fuelTankIsComingSoonUntilIslandFuelIsOn() {
        UpgradeCatalog.Def tank = new UpgradeCatalog.Def("fuel_tank", UpgradeTab.PRODUCTION, "blast_furnace", 7, UpgradeEffect.FUEL_TANK, true,
                List.of(new UpgradeCatalog.Level(1, 2_680, 1, 0)));
        assertEquals(UpgradeRules.Status.COMING_SOON, UpgradeRules.status(tank, 0, true, 99, true, 1_000_000));
        IslandFuelFeature.source(() -> true);
        assertEquals(UpgradeRules.Status.READY, UpgradeRules.status(tank, 0, true, 99, true, 1_000_000));
        assertFalse(UpgradeEffect.GENERATOR.live(), "other Phase 7 upgrades stay coming soon");
    }

    @Test
    void fuelEfficiencyMasteryShowsOnlyWhileIslandFuelIsOn() throws Exception {
        MasteryCatalog catalog = MasteryCatalogTest.bundled();
        assertFalse(catalog.shown().stream().anyMatch(d -> d.id().equals("fuel_efficiency")));
        IslandFuelFeature.source(() -> true);
        assertTrue(catalog.shown().stream().anyMatch(d -> d.id().equals("fuel_efficiency")), "unhidden without reloading the catalog");
        assertEquals(List.of(10.0, 20.0, 30.0), catalog.get("fuel_efficiency").orElseThrow().ranks().stream()
                .map(MasteryCatalog.Rank::value).toList(), "fuel lasts +10 / +20 / +30%");
    }
}
