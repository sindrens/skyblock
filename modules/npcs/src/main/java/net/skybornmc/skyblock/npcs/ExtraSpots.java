package net.skybornmc.skyblock.npcs;

import net.skybornmc.skyblock.common.builds.BuildPoint;
import net.skybornmc.skyblock.common.builds.BuildPoints;
import net.skybornmc.skyblock.common.builds.BuildRegion;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * NPCs from {@code npcs.extra-spots} (7 Nov 2026, FX-21): places no build covers, such as the code-built grind areas.
 * Each line is {@code id: role world x y z yaw}; the points join the build points as build {@value #BUILD}, so they are
 * placed, named and clicked exactly like build NPCs. Pure and unit-tested.
 */
final class ExtraSpots {

    static final String BUILD = "npcs.extra-spots";

    private ExtraSpots() {
    }

    /** The parsed points; bad lines are skipped with a problem line. */
    static List<BuildPoint> parse(List<String> lines, List<String> problems) {
        List<BuildPoint> out = new ArrayList<>();
        Set<String> seen = new java.util.HashSet<>();
        for (String raw : lines == null ? List.<String>of() : lines) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            int colon = raw.indexOf(':');
            String id = colon < 0 ? "" : raw.substring(0, colon).strip().toLowerCase(Locale.ROOT);
            String[] parts = colon < 0 ? new String[0] : raw.substring(colon + 1).strip().split("\\s+");
            if (id.isEmpty() || parts.length != 6) {
                problems.add(BUILD + ": '" + raw.strip() + "' is not 'id: role world x y z yaw' (skipped)");
                continue;
            }
            double x;
            double y;
            double z;
            float yaw;
            try {
                x = Double.parseDouble(parts[2]);
                y = Double.parseDouble(parts[3]);
                z = Double.parseDouble(parts[4]);
                yaw = Float.parseFloat(parts[5]);
            } catch (NumberFormatException e) {
                problems.add(BUILD + ": '" + raw.strip() + "' has a position that is not a number (skipped)");
                continue;
            }
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || !Float.isFinite(yaw)) {
                problems.add(BUILD + ": '" + raw.strip() + "' has a position that is not a number (skipped)");
                continue;
            }
            if (!seen.add(id)) {
                problems.add(BUILD + ": id '" + id + "' is used twice; the second one is skipped");
                continue;
            }
            out.add(new BuildPoint(BUILD, Spots.NPCS, id, parts[1].equals("-") ? "" : parts[1], x, y, z, yaw, 0f,
                    Map.of("role", parts[0].toLowerCase(Locale.ROOT))));
        }
        return out;
    }

    /** {@code base} with the extra NPC points added ({@code base} null = only the extras). */
    static BuildPoints over(BuildPoints base, List<BuildPoint> extra) {
        if (extra.isEmpty()) {
            return base;
        }
        return new BuildPoints() {
            @Override
            public List<BuildPoint> points(String category) {
                List<BuildPoint> own = base == null ? List.of() : base.points(category);
                if (!Spots.NPCS.equals(category)) {
                    return own;
                }
                List<BuildPoint> all = new ArrayList<>(own);
                all.addAll(extra);
                return all;
            }

            @Override
            public List<BuildPoint> points(String build, String category) {
                if (BUILD.equals(build)) {
                    return Spots.NPCS.equals(category) ? extra : List.of();
                }
                return base == null ? List.of() : base.points(build, category);
            }

            @Override
            public Optional<BuildPoint> point(String category, String id) {
                Optional<BuildPoint> own = base == null ? Optional.empty() : base.point(category, id);
                if (own.isPresent() || !Spots.NPCS.equals(category)) {
                    return own;
                }
                return extra.stream().filter(p -> p.id().equalsIgnoreCase(id)).findFirst();
            }

            @Override
            public List<BuildRegion> regions(String category) {
                return base == null ? List.of() : base.regions(category);
            }

            @Override
            public Optional<BuildRegion> region(String category, String id) {
                return base == null ? Optional.empty() : base.region(category, id);
            }

            @Override
            public Set<String> categories() {
                Set<String> all = new LinkedHashSet<>(base == null ? Set.of() : base.categories());
                all.add(Spots.NPCS);
                return all;
            }

            @Override
            public Set<String> builds() {
                Set<String> all = new LinkedHashSet<>(base == null ? Set.of() : base.builds());
                all.add(BUILD);
                return all;
            }

            @Override
            public long revision() {
                return base == null ? -1 : base.revision();
            }
        };
    }
}
