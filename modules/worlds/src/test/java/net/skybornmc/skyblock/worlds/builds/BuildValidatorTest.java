package net.skybornmc.skyblock.worlds.builds;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildValidatorTest {

    @Test
    void everyTrackedBuildIsValid() {
        BuildCatalog catalog = BuildCatalog.scan(RealBuilds.FOLDER, RealBuilds.CONTEXT);
        assertEquals(List.of(), catalog.problems());
        assertEquals(RealBuilds.IDS, catalog.builds().keySet());
        for (BuildEntry e : catalog.builds().values()) {
            assertTrue(e.ok(), e.id() + ": " + e.problems());
            assertTrue(e.info() != null && e.info().nonAir() > 0, e.id());
            assertTrue(e.pasteBox().contains(e.meta().bounds()), e.id());
        }
        assertEquals(33, catalog.layout().zones().size()); // mine 12 + forest 11 + combat 10
    }

    @Test
    void blocksWithAirWriteTheWholeArea() {
        BuildCatalog catalog = BuildCatalog.scan(RealBuilds.FOLDER, RealBuilds.CONTEXT);
        BuildEntry base = catalog.builds().get("spawn_base_v1");
        assertEquals(base.info().volume(), base.blocksToPaste());
        BuildEntry market = catalog.builds().get("spawn_market_square_v1");
        assertEquals(market.info().nonAir(), market.blocksToPaste());
    }

    private static BuildMeta meta(String yamlText) throws InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(yamlText);
        return BuildMetaParser.parse("spawn_test_v1", yaml, p -> {
        });
    }

    private static final SchematicInfo INFO = new SchematicInfo(2, 5023, 10, 10, 10, -5, 95, -5, 0, 100, 0, 500, 0, 0);

    @Test
    void missingSchematicAndUnknownWorldAreErrors() throws InvalidConfigurationException {
        BuildMeta m = meta("schematic: spawn_test_v1.schem\nworld: moon\norigin: { x: 0, y: 100, z: 0 }\n");
        List<BuildProblem> problems = BuildValidator.validate(m, false, null, null, RealBuilds.CONTEXT);
        assertTrue(BuildValidator.hasErrors(problems));
        assertEquals(2, problems.stream().filter(BuildProblem::error).count(), problems.toString());
    }

    @Test
    void boundsOutsideThePastedAreaAreAnError() throws InvalidConfigurationException {
        BuildMeta ok = meta("schematic: a.schem\nworld: spawn\norigin: { x: 0, y: 100, z: 0 }\n"
                + "bounds: { from: { x: -5, y: 95, z: -5 }, to: { x: 4, y: 104, z: 4 } }\n");
        assertFalse(BuildValidator.hasErrors(BuildValidator.validate(ok, true, INFO, null, RealBuilds.CONTEXT)));
        BuildMeta shifted = meta("schematic: a.schem\nworld: spawn\norigin: { x: 0, y: 100, z: 0 }\n"
                + "bounds: { from: { x: -5, y: 95, z: -5 }, to: { x: 9, y: 104, z: 4 } }\n");
        assertTrue(BuildValidator.hasErrors(BuildValidator.validate(shifted, true, INFO, null, RealBuilds.CONTEXT)));
    }

    @Test
    void otherOriginIsAWarningAndNewerDataIsAnError() throws InvalidConfigurationException {
        BuildMeta m = meta("schematic: a.schem\nworld: spawn\norigin: { x: 3, y: 100, z: 0 }\n");
        List<BuildProblem> warn = BuildValidator.validate(m, true, INFO, null, RealBuilds.CONTEXT);
        assertFalse(BuildValidator.hasErrors(warn));
        assertEquals(1, warn.size(), warn.toString());
        SchematicInfo newer = new SchematicInfo(2, 9999, 10, 10, 10, -5, 95, -5, 0, 100, 0, 500, 0, 0);
        assertTrue(BuildValidator.hasErrors(BuildValidator.validate(m, true, newer, null, RealBuilds.CONTEXT)));
    }

    @Test
    void outsideTheWorldBorderIsAnError() throws InvalidConfigurationException {
        BuildMeta m = meta("schematic: a.schem\nworld: spawn\norigin: { x: 498, y: 100, z: 0 }\n");
        assertTrue(BuildValidator.hasErrors(BuildValidator.validate(m, true, INFO, null, RealBuilds.CONTEXT)));
    }
}
