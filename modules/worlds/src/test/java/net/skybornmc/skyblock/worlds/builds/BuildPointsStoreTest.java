package net.skybornmc.skyblock.worlds.builds;

import net.skybornmc.skyblock.common.builds.BuildPoint;
import net.skybornmc.skyblock.common.builds.BuildRegion;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildPointsStoreTest {

    @Test
    void roundTripsEveryBuild() throws InvalidConfigurationException {
        BuildPointsStore store = new BuildPointsStore();
        for (String id : RealBuilds.IDS) {
            store.put(BuildPointsStore.fromMeta(RealBuilds.meta(id), "2026-10-30 12:00", "Console"));
        }
        String text = BuildPointsStore.write(store.all()).saveToString();
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(text);
        Map<String, BuildPointsStore.Applied> back = BuildPointsStore.read(yaml, p -> {
            throw new AssertionError(p);
        });
        assertEquals(store.all(), back);
    }

    @Test
    void answersQueriesAcrossBuilds() {
        BuildPointsStore store = new BuildPointsStore();
        store.put(BuildPointsStore.fromMeta(RealBuilds.meta("spawn_market_square_v1"), "t", "me"));
        store.put(BuildPointsStore.fromMeta(RealBuilds.meta("spawn_forge_quarter_v1"), "t", "me"));
        assertEquals(8, store.points("npcs").size());
        BuildPoint bank = store.point("npcs", "bank").orElseThrow();
        assertEquals(81, bank.blockZ());
        assertEquals(180f, bank.yaw());
        assertEquals(3, store.points("spawn_forge_quarter_v1", "npcs").size());
        assertTrue(store.categories().containsAll(Set.of("npcs", "holograms", "particles", "treasure_spots", "secrets")));
        BuildRegion plot = store.region("regions", "market_square_plot").orElseThrow();
        assertEquals("plot", plot.kind());
        // applying again replaces only that build
        store.put(BuildPointsStore.fromMeta(RealBuilds.meta("spawn_forge_quarter_v1"), "t2", "me"));
        assertEquals(8, store.points("npcs").size());
        assertEquals(Set.of("spawn_forge_quarter_v1", "spawn_market_square_v1"), store.builds());
    }

    /** PERF11: every change moves the revision, so readers (the visibility zones) can cache region lookups. */
    @Test
    void revisionMovesOnEveryChange(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) {
        BuildPointsStore store = new BuildPointsStore();
        long start = store.revision();
        assertTrue(start >= 0, "tracked");
        store.put(BuildPointsStore.fromMeta(RealBuilds.meta("spawn_market_square_v1"), "t", "me"));
        long afterPut = store.revision();
        assertTrue(afterPut > start);
        assertEquals(afterPut, store.revision(), "reads do not move it");
        store.region("regions", "market_square_plot");
        assertEquals(afterPut, store.revision());
        store.put(BuildPointsStore.fromMeta(RealBuilds.meta("spawn_market_square_v1"), "t2", "me"));
        assertTrue(store.revision() > afterPut, "applying the same build again counts as a change");
        long beforeLoad = store.revision();
        store.load(dir.resolve("missing.yml").toFile(), p -> { });
        assertTrue(store.revision() > beforeLoad, "a (re)load counts as a change");
    }
}
