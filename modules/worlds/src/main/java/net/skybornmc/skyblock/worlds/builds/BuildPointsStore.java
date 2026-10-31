package net.skybornmc.skyblock.worlds.builds;

import net.skybornmc.skyblock.common.builds.BuildBox;
import net.skybornmc.skyblock.common.builds.BuildPoint;
import net.skybornmc.skyblock.common.builds.BuildPoints;
import net.skybornmc.skyblock.common.builds.BuildRegion;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * {@code build-points.yml}: the spots and regions of every applied build (NPC pads, holograms, leaderboards, crates,
 * particles, tutorial nodes, viewpoints, portals, regions...), written by {@code /skyblock builds apply} and read by
 * other modules through {@link BuildPoints}. Applying a build replaces only that build's entries. Snapshots are
 * immutable, so readers on any thread see a consistent state.
 */
public final class BuildPointsStore implements BuildPoints {

    /** One applied build. */
    public record Applied(String build, String world, String appliedAt, String appliedBy, List<BuildPoint> points,
                          List<BuildRegion> regions) {

        public Applied {
            points = List.copyOf(points);
            regions = List.copyOf(regions);
        }
    }

    static final String HEADER = """
            Spots and regions of the applied builds (written by /skyblock builds apply; applying a build again replaces
            its entries). Other modules read this file through the BuildPoints service: NPC pads, holograms,
            leaderboards, crates, particles, tutorial, viewpoints, treasure spots, portals, regions. Coordinates are world
            coordinates as the builder wrote them. Edit the build's .yml in <server>/builds and apply again instead.""";

    private volatile Map<String, Applied> builds = Map.of();
    /** PERF11: goes up on every change of {@link #builds}, so readers can cache lookups ({@link BuildPoints#revision}). */
    private final java.util.concurrent.atomic.AtomicLong revision = new java.util.concurrent.atomic.AtomicLong();

    /** Replaces the snapshot; the new revision is published after it, so a reader that sees it also sees the snapshot. */
    private void set(Map<String, Applied> next) {
        builds = next;
        revision.incrementAndGet();
    }

    @Override
    public long revision() {
        return revision.get();
    }

    public Map<String, Applied> all() {
        return builds;
    }

    /** Adds or replaces one build. */
    public synchronized void put(Applied applied) {
        Map<String, Applied> copy = new TreeMap<>(builds);
        copy.put(applied.build(), applied);
        set(Collections.unmodifiableMap(copy));
    }

    public static Applied fromMeta(BuildMeta meta, String appliedAt, String appliedBy) {
        return new Applied(meta.id(), meta.world() == null ? "" : meta.world(), appliedAt, appliedBy, meta.points(), meta.regions());
    }

    // ------------------------------------------------------------------ BuildPoints

    @Override
    public List<BuildPoint> points(String category) {
        List<BuildPoint> out = new ArrayList<>();
        for (Applied a : builds.values()) {
            for (BuildPoint p : a.points()) {
                if (p.category().equals(category)) {
                    out.add(p);
                }
            }
        }
        return out;
    }

    @Override
    public List<BuildPoint> points(String build, String category) {
        Applied a = builds.get(build);
        return a == null ? List.of() : a.points().stream().filter(p -> p.category().equals(category)).toList();
    }

    @Override
    public Optional<BuildPoint> point(String category, String id) {
        return points(category).stream().filter(p -> p.id().equals(id)).findFirst();
    }

    @Override
    public List<BuildRegion> regions(String category) {
        List<BuildRegion> out = new ArrayList<>();
        for (Applied a : builds.values()) {
            for (BuildRegion r : a.regions()) {
                if (r.category().equals(category)) {
                    out.add(r);
                }
            }
        }
        return out;
    }

    @Override
    public Optional<BuildRegion> region(String category, String id) {
        return regions(category).stream().filter(r -> r.id().equals(id)).findFirst();
    }

    @Override
    public Set<String> categories() {
        Set<String> out = new LinkedHashSet<>();
        builds.values().forEach(a -> a.points().forEach(p -> out.add(p.category())));
        return out;
    }

    @Override
    public Set<String> builds() {
        return builds.keySet();
    }

    // ------------------------------------------------------------------ file

    public void load(File file, Consumer<String> problem) {
        if (!file.isFile()) {
            set(Map.of());
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (IOException | InvalidConfigurationException e) {
            problem.accept(file.getName() + ": not valid YAML, no build points loaded. "
                    + String.valueOf(e.getMessage()).lines().findFirst().orElse(""));
            set(Map.of());
            return;
        }
        set(Collections.unmodifiableMap(read(yaml, problem)));
    }

    public void save(File file) throws IOException {
        YamlConfiguration yaml = write(builds);
        file.getParentFile().mkdirs();
        File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
        yaml.save(tmp);
        java.nio.file.Files.move(tmp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE);
    }

    static YamlConfiguration write(Map<String, Applied> builds) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().setHeader(List.of(HEADER.split("\n")));
        for (Applied a : builds.values()) {
            String base = "builds." + a.build();
            yaml.set(base + ".world", a.world());
            yaml.set(base + ".applied-at", a.appliedAt());
            yaml.set(base + ".applied-by", a.appliedBy());
            Map<String, List<Map<String, Object>>> points = new LinkedHashMap<>();
            for (BuildPoint p : a.points()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", p.id());
                m.put("x", p.x());
                m.put("y", p.y());
                m.put("z", p.z());
                m.put("yaw", (double) p.yaw());
                m.put("pitch", (double) p.pitch());
                if (!p.extra().isEmpty()) {
                    m.put("extra", new LinkedHashMap<>(p.extra()));
                }
                points.computeIfAbsent(p.category(), k -> new ArrayList<>()).add(m);
            }
            points.forEach((category, list) -> yaml.set(base + ".points." + category, list));
            Map<String, List<Map<String, Object>>> regions = new LinkedHashMap<>();
            for (BuildRegion r : a.regions()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", r.id());
                m.put("kind", r.kind());
                BuildBox b = r.box();
                m.put("from", b.minX() + ", " + b.minY() + ", " + b.minZ());
                m.put("to", b.maxX() + ", " + b.maxY() + ", " + b.maxZ());
                if (!r.extra().isEmpty()) {
                    m.put("extra", new LinkedHashMap<>(r.extra()));
                }
                regions.computeIfAbsent(r.category(), k -> new ArrayList<>()).add(m);
            }
            regions.forEach((category, list) -> yaml.set(base + ".regions." + category, list));
        }
        return yaml;
    }

    static Map<String, Applied> read(ConfigurationSection yaml, Consumer<String> problem) {
        Map<String, Applied> out = new TreeMap<>();
        ConfigurationSection root = yaml.getConfigurationSection("builds");
        if (root == null) {
            return out;
        }
        for (String build : root.getKeys(false)) {
            ConfigurationSection b = root.getConfigurationSection(build);
            if (b == null) {
                continue;
            }
            String world = b.getString("world", "");
            List<BuildPoint> points = new ArrayList<>();
            ConfigurationSection ps = b.getConfigurationSection("points");
            if (ps != null) {
                for (String category : ps.getKeys(false)) {
                    for (Map<?, ?> m : ps.getMapList(category)) {
                        Map<String, Object> e = BuildMetaParser.map(m);
                        BuildMeta.Spot s = BuildMetaParser.spot(e);
                        String id = BuildMetaParser.text(e.get("id"));
                        if (s == null || id == null) {
                            problem.accept("build-points.yml → " + build + "." + category + ": broken entry skipped");
                            continue;
                        }
                        points.add(new BuildPoint(build, category, id, world, s.x(), s.y(), s.z(), s.yaw(), s.pitch(), strings(e.get("extra"))));
                    }
                }
            }
            List<BuildRegion> regions = new ArrayList<>();
            ConfigurationSection rs = b.getConfigurationSection("regions");
            if (rs != null) {
                for (String category : rs.getKeys(false)) {
                    for (Map<?, ?> m : rs.getMapList(category)) {
                        Map<String, Object> e = BuildMetaParser.map(m);
                        String id = BuildMetaParser.text(e.get("id"));
                        int[] from = triple(e.get("from"));
                        int[] to = triple(e.get("to"));
                        if (id == null || from == null || to == null) {
                            problem.accept("build-points.yml → " + build + "." + category + ": broken region skipped");
                            continue;
                        }
                        regions.add(new BuildRegion(build, category, id, BuildMetaParser.text(e.get("kind")), world,
                                new BuildBox(from[0], from[1], from[2], to[0], to[1], to[2]), strings(e.get("extra"))));
                    }
                }
            }
            out.put(build, new Applied(build, world, b.getString("applied-at", ""), b.getString("applied-by", ""), points, regions));
        }
        return out;
    }

    private static Map<String, String> strings(Object raw) {
        Map<String, String> out = new LinkedHashMap<>();
        BuildMetaParser.map(raw).forEach((k, v) -> out.put(k, String.valueOf(v)));
        return out;
    }

    /** "x, y, z" → ints, or {@code null}. */
    static int[] triple(Object raw) {
        if (raw == null) {
            return null;
        }
        String[] parts = String.valueOf(raw).split(",");
        if (parts.length != 3) {
            return null;
        }
        try {
            return new int[]{Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()), Integer.parseInt(parts[2].trim())};
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
