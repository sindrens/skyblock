package net.skybornmc.skyblock.worlds.builds;

import net.skybornmc.skyblock.common.builds.BuildBox;
import net.skybornmc.skyblock.common.builds.DepthsLayout;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * {@code builds/depths_layout.yml}: The Gate and every planned tier zone with the schematic name each build will have
 * ({@code depths_mine_t01_coal_vN.schem}). Builds are matched to zones by that name (any version).
 *
 * @param gateSchematic name pattern of The Gate's schematic, or {@code null}
 * @param schematics    zone id → schematic name pattern
 */
public record LayoutFile(int floorY, BuildBox gate, String gateSchematic, List<DepthsLayout.LayoutZone> zones,
                         Map<String, String> schematics) {

    public static final String FILE = "depths_layout.yml";

    public LayoutFile {
        zones = List.copyOf(zones);
        schematics = Map.copyOf(schematics);
    }

    public static LayoutFile parse(ConfigurationSection yaml, Consumer<String> problem) {
        int floorY = yaml.getInt("floor_y", 100);
        Map<String, Object> gate = BuildMetaParser.map(yaml.get("gate"));
        BuildBox gateBox = gate.isEmpty() ? null : BuildMetaParser.box(gate);
        if (!gate.isEmpty() && gateBox == null) {
            problem.accept(FILE + " → gate: needs from/to");
        }
        String gateSchematic = BuildMetaParser.text(gate.get("schematic"));
        List<DepthsLayout.LayoutZone> zones = new ArrayList<>();
        Map<String, String> schematics = new java.util.LinkedHashMap<>();
        Object raw = yaml.get("zones");
        if (raw instanceof List<?> list) {
            int index = 0;
            for (Object o : list) {
                index++;
                Map<String, Object> z = BuildMetaParser.map(o);
                String id = BuildMetaParser.text(z.get("id"));
                String wing = BuildMetaParser.text(z.get("wing"));
                Double tier = BuildMetaParser.num(z.get("tier"));
                BuildBox box = BuildMetaParser.box(z);
                Map<String, Object> center = BuildMetaParser.map(z.get("center"));
                Double cx = BuildMetaParser.num(center.get("x"));
                Double cz = BuildMetaParser.num(center.get("z"));
                String entrance = side(z.get("entrance"));
                if (id == null || wing == null || tier == null || box == null || entrance == null) {
                    problem.accept(FILE + " → zones #" + index + ": needs id, wing, tier, from/to and entrance, skipped");
                    continue;
                }
                int centerX = cx == null ? Math.floorDiv(box.minX() + box.maxX(), 2) : cx.intValue();
                int centerZ = cz == null ? Math.floorDiv(box.minZ() + box.maxZ(), 2) : cz.intValue();
                zones.add(new DepthsLayout.LayoutZone(id, wing.toLowerCase(Locale.ROOT), tier.intValue(), box, centerX, centerZ,
                        entrance, side(z.get("exit"))));
                String schem = BuildMetaParser.text(z.get("schematic"));
                if (schem != null) {
                    schematics.put(id, schem);
                }
            }
        } else {
            problem.accept(FILE + " → zones: missing or not a list");
        }
        return new LayoutFile(floorY, gateBox, gateSchematic, zones, schematics);
    }

    private static String side(Object raw) {
        String s = BuildMetaParser.text(raw);
        if (s == null) {
            return null;
        }
        s = s.toLowerCase(Locale.ROOT);
        return switch (s) {
            case "north", "south", "east", "west" -> s;
            default -> null;
        };
    }

    /** True if {@code buildId} is a version of the pattern ({@code depths_gate_vN.schem} matches {@code depths_gate_v1}). */
    static boolean matches(String pattern, String buildId) {
        if (pattern == null) {
            return false;
        }
        String base = pattern.endsWith(".schem") ? pattern.substring(0, pattern.length() - ".schem".length()) : pattern;
        String regex = Pattern.quote(base).replace("_vN", "\\E_v\\d+\\Q");
        return Pattern.compile(regex.replace("\\Q\\E", "")).matcher(buildId).matches();
    }

    /** The zone a build belongs to, or {@code null}. */
    public DepthsLayout.LayoutZone zoneOf(String buildId) {
        for (DepthsLayout.LayoutZone z : zones) {
            if (matches(schematics.get(z.id()), buildId)) {
                return z;
            }
        }
        return null;
    }

    /** True if the build is The Gate. */
    public boolean isGate(String buildId) {
        return matches(gateSchematic, buildId);
    }
}
