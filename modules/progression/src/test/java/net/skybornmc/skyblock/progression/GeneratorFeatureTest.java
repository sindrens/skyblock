package net.skybornmc.skyblock.progression;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PHASE-7 M7.2: the Generator upgrade turns live only while custom generators are on. */
class GeneratorFeatureTest {

    @AfterEach
    void off() {
        GeneratorFeature.source(null);
        IslandFuelFeature.source(null);
    }

    @Test
    void generatorIsComingSoonUntilGeneratorsAreOn() {
        UpgradeCatalog.Def gen = new UpgradeCatalog.Def("generator", UpgradeTab.PRODUCTION, "cobblestone", 6, UpgradeEffect.GENERATOR, true,
                List.of(new UpgradeCatalog.Level(1, 7_140, 1, 0)));
        assertEquals(UpgradeRules.Status.COMING_SOON, UpgradeRules.status(gen, 0, true, 99, true, 1_000_000));
        IslandFuelFeature.source(() -> true);
        assertFalse(UpgradeEffect.GENERATOR.live(), "Island Fuel alone doesn't switch generators on");
        GeneratorFeature.source(() -> true);
        assertTrue(UpgradeEffect.GENERATOR.live());
        assertEquals(UpgradeRules.Status.READY, UpgradeRules.status(gen, 0, true, 99, true, 1_000_000));
    }
}
