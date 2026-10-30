package net.skybornmc.skyblock.worlds.builds;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Map;
import java.util.Set;

/** The tracked builds in server/builds, used as test fixtures (they are what the server pastes). */
final class RealBuilds {

    static final File FOLDER = new File("../../server/builds");

    /** The worlds of worlds.yml with their border radius (0 = none). */
    static final Map<String, Double> BORDERS = Map.of("spawn", 500.0, "islands", 0.0, "outposts", 0.0, "depths", 1000.0, "warzone", 500.0);

    static final BuildValidator.Context CONTEXT = new BuildValidator.Context(BORDERS::containsKey, 5023,
            w -> BORDERS.getOrDefault(w, 0.0) > 0, w -> BORDERS.getOrDefault(w, 0.0));

    static final Set<String> IDS = Set.of("depths_combat_t01_graveyard_v1", "depths_forest_t01_oak_v1", "depths_gate_v1",
            "depths_mine_t01_coal_v1", "depths_mine_t02_copper_v1", "islands_starter_meadow_v1", "spawn_base_v1",
            "spawn_forge_quarter_v1", "spawn_market_square_v1");

    private RealBuilds() {
    }

    static BuildMeta meta(String id) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new File(FOLDER, id + ".yml"));
        return BuildMetaParser.parse(id, yaml, p -> {
            throw new AssertionError(id + ": " + p);
        });
    }

    static LayoutFile layout() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new File(FOLDER, LayoutFile.FILE));
        return LayoutFile.parse(yaml, p -> {
            throw new AssertionError(p);
        });
    }
}
