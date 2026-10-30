package net.skybornmc.skyblock.worlds.builds;

import net.skybornmc.skyblock.common.builds.BuildBox;
import net.skybornmc.skyblock.common.builds.BuildPoint;
import net.skybornmc.skyblock.common.builds.BuildRegion;
import net.skybornmc.skyblock.common.builds.DepthsLayout;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turns the layout file plus one Depths build's metadata into the {@link DepthsLayout} handed to the depths module:
 * the arrival pad, tier gate, exit, combat arena and ore/log fields of a tier zone, or the spawn and causeway starts of
 * The Gate. Pure.
 */
public final class DepthsWiring {

    private DepthsWiring() {
    }

    /** The layout with {@code build} wired in (or only the layout when {@code build} is {@code null}). */
    public static DepthsLayout layout(LayoutFile file, BuildMeta build) {
        List<DepthsLayout.BuiltZone> built = new ArrayList<>();
        DepthsLayout.BuiltHub hub = null;
        if (build != null) {
            if (file.isGate(build.id())) {
                hub = hub(build);
            } else {
                DepthsLayout.LayoutZone zone = file.zoneOf(build.id());
                if (zone != null) {
                    built.add(zone(build, zone.wing(), zone.tier()));
                }
            }
        }
        return new DepthsLayout(file.floorY(), file.gate(), file.zones(), built, hub);
    }

    /** True if the build is The Gate or one of the layout's zones. */
    public static boolean isDepthsBuild(LayoutFile file, String buildId) {
        return file != null && (file.isGate(buildId) || file.zoneOf(buildId) != null);
    }

    static DepthsLayout.BuiltHub hub(BuildMeta build) {
        DepthsLayout.Pos spawn = null;
        BuildMeta.Spot s = build.playerSpawn();
        if (s != null) {
            spawn = new DepthsLayout.Pos(s.feetX(), s.y(), s.feetZ(), s.yaw(), s.pitch());
        }
        Map<String, BuildBox> gates = new LinkedHashMap<>();
        for (BuildRegion r : build.regions("regions")) {
            String id = r.id().toLowerCase(Locale.ROOT);
            if (id.startsWith("causeway_stub_")) {
                gates.put(id.substring("causeway_stub_".length()), r.box());
            }
        }
        return new DepthsLayout.BuiltHub(build.id(), spawn, gates);
    }

    static DepthsLayout.BuiltZone zone(BuildMeta build, String wing, int tier) {
        DepthsLayout.Pos arrival = null;
        List<BuildPoint> pads = new ArrayList<>(build.points("arrival"));
        pads.addAll(build.points("arrival_pad"));
        if (!pads.isEmpty()) {
            BuildPoint p = pads.getFirst();
            boolean integer = p.x() == Math.rint(p.x()) && p.z() == Math.rint(p.z());
            arrival = new DepthsLayout.Pos(integer ? p.x() + 0.5 : p.x(), p.y(), integer ? p.z() + 0.5 : p.z(), p.yaw(), p.pitch());
        }
        BuildBox gate = null;
        BuildBox exit = null;
        BuildBox arena = null;
        List<DepthsLayout.BuiltField> fields = new ArrayList<>();
        for (BuildRegion r : build.regions("regions")) {
            String id = r.id().toLowerCase(Locale.ROOT);
            String kind = r.kind().toLowerCase(Locale.ROOT);
            if (gate == null && (id.equals("tier_gate") || kind.equals("tier_gate"))) {
                gate = r.box();
            } else if (exit == null && (kind.equals("exit") || r.extra().containsKey("leads_to") || r.extra().containsKey("to_zone"))) {
                exit = r.box();
            } else if (arena == null && (id.equals("arena") || kind.equals("mob_area"))) {
                arena = r.box();
            }
            String block = r.extra().containsKey("ore") ? r.extra().get("ore") : r.extra().get("log");
            if (block != null) {
                String respawn = r.extra().get("respawn_seconds");
                Integer seconds = null;
                if (respawn != null) {
                    try {
                        seconds = (int) Double.parseDouble(respawn);
                    } catch (NumberFormatException ignored) {
                        // keep the engine default
                    }
                }
                fields.add(new DepthsLayout.BuiltField(r.id(), r.box(), block.toLowerCase(Locale.ROOT), r.extra().get("depleted"), seconds));
            }
        }
        return new DepthsLayout.BuiltZone(build.id(), wing, tier, arrival, gate, exit, arena, fields);
    }
}
