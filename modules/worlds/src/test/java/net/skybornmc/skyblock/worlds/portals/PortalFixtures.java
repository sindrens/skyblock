package net.skybornmc.skyblock.worlds.portals;

import net.skybornmc.skyblock.worlds.builds.BuildMetaParser;
import net.skybornmc.skyblock.worlds.builds.BuildPointsStore;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** The real gatehouse and Depths gate builds (server/builds) applied into a store, plus a fake world view. */
final class PortalFixtures {

    static final File FOLDER = new File("../../server/builds");

    private PortalFixtures() {
    }

    static BuildPointsStore store(String... ids) {
        BuildPointsStore store = new BuildPointsStore();
        for (String id : ids) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new File(FOLDER, id + ".yml"));
            store.put(BuildPointsStore.fromMeta(BuildMetaParser.parse(id, yaml, p -> {
                throw new AssertionError(id + ": " + p);
            }), "t", "test"));
        }
        return store;
    }

    static List<Portal> realPortals() {
        return Portals.from(store("spawn_gatehouse_v1", "depths_gate_v1", "spawn_base_v1"));
    }

    static Portal portal(List<Portal> all, String id) {
        return all.stream().filter(p -> p.id().equals(id)).findFirst().orElseThrow(() -> new AssertionError("no portal " + id));
    }

    /** Worlds loaded, their spawns, which have builds and the build points. */
    record FakeWorld(Set<String> loaded, Map<String, Portal.Pos> spawns, Set<String> built,
                     Map<String, PortalResolver.Located> points) implements PortalResolver.World {

        static FakeWorld standard() {
            return new FakeWorld(Set.of("spawn", "islands", "depths", "warzone"),
                    Map.of("spawn", new Portal.Pos(0.5, 100, 0.5, 180, 0), "depths", new Portal.Pos(7.5, 100, 14.5, 165, 0),
                            "warzone", new Portal.Pos(0.5, 100, 0.5, 0, 0)),
                    Set.of("spawn", "depths"), Map.of());
        }

        @Override
        public boolean loaded(String world) {
            return loaded.contains(world);
        }

        @Override
        public Optional<Portal.Pos> spawn(String world) {
            return loaded.contains(world) ? Optional.ofNullable(spawns.get(world)) : Optional.empty();
        }

        @Override
        public boolean hasBuild(String world) {
            return built.contains(world);
        }

        @Override
        public Optional<PortalResolver.Located> point(String category, String id) {
            return Optional.ofNullable(points.get(category + ":" + id));
        }
    }
}
