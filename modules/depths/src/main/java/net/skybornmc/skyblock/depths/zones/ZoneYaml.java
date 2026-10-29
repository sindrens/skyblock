package net.skybornmc.skyblock.depths.zones;

import java.util.Locale;
import java.util.Map;

/** Writes a {@link ZoneSet} as {@code zones.yml} text (used to generate the shipped default file). */
public final class ZoneYaml {

    static final String HEADER = """
            # The Depths layout (M4.1): The Gate, the three wings and the world boss arena.
            # Generated from ZoneLayout (U-shaped wings inside the 2000 border); edit freely, the code never rewrites it.
            # Real builds only change coordinates here, never code. Coordinates are world coordinates (inclusive corners).
            #
            # Per zone:
            #   aspect: mine | forest | combat      tier: 1..   required-level: skill level to enter (Mining/Foraging/Combat)
            #   built: true once a builder's real build stands here -> /depths generate never touches it again
            #   region: the zone cuboid            spawn: "x, y, z, yaw, pitch" (arrival pad, /depths tp and /dungeon target)
            #   gate: tier gate opening (5x5)      exit: opening toward the next tier      causeway: walled path into the zone
            #   fields: ore/log cuboids registered with the per-player blocks engine as region <zone>_<field>
            #     resources: [{ block, depleted, respawn-seconds }]  (missing = depths.yml engine defaults)
            #   arena: combat fighting area        biome: set by the placeholder build      palette: placeholder blocks
            # Reload: /skyblock reload depths      Build placeholders: /depths generate [zone|all]
            """;

    private ZoneYaml() {
    }

    public static String write(ZoneSet set) {
        StringBuilder out = new StringBuilder(HEADER).append('\n');
        out.append("world: ").append(set.world()).append('\n');
        out.append("floor-y: ").append(set.floorY()).append("\n\n");
        Hub hub = set.hub();
        if (hub != null) {
            out.append("hub:\n");
            out.append("  name: ").append(quote(hub.name())).append('\n');
            out.append("  built: ").append(hub.built()).append('\n');
            box(out, "  ", "region", hub.region());
            out.append("  spawn: ").append(point(hub.spawn())).append('\n');
            out.append("  wing-gates:\n");
            for (Aspect a : Aspect.values()) {
                Box g = hub.wingGates().get(a);
                if (g != null) {
                    box(out, "    ", a.id(), g);
                }
            }
            palette(out, "  ", hub.palette());
            out.append('\n');
        }
        BossArena arena = set.arena();
        if (arena != null) {
            out.append("boss-arena:\n");
            out.append("  name: ").append(quote(arena.name())).append('\n');
            out.append("  built: ").append(arena.built()).append('\n');
            box(out, "  ", "region", arena.region());
            out.append("  spawn: ").append(point(arena.spawn())).append('\n');
            out.append("  portal-zone: ").append(arena.portalZone()).append('\n');
            box(out, "  ", "portal", arena.portal());
            box(out, "  ", "return-portal", arena.returnPortal());
            palette(out, "  ", arena.palette());
            out.append('\n');
        }
        out.append("zones:\n");
        Aspect last = null;
        for (Zone z : set.zones()) {
            if (z.aspect() != last) {
                out.append("  # ---------------------------------------------------------------- ")
                        .append(z.aspect().id()).append(" wing\n");
                last = z.aspect();
            }
            out.append("  ").append(z.id()).append(":\n");
            out.append("    aspect: ").append(z.aspect().id()).append('\n');
            out.append("    tier: ").append(z.tier()).append('\n');
            out.append("    name: ").append(quote(z.name())).append('\n');
            out.append("    resource-name: ").append(quote(z.resourceName())).append('\n');
            out.append("    required-level: ").append(z.requiredLevel()).append('\n');
            out.append("    built: ").append(z.built()).append('\n');
            box(out, "    ", "region", z.region());
            out.append("    spawn: ").append(point(z.spawn())).append('\n');
            box(out, "    ", "gate", z.gate());
            if (z.exit() != null) {
                box(out, "    ", "exit", z.exit());
            }
            if (z.causeway() != null) {
                box(out, "    ", "causeway", z.causeway());
            }
            if (!z.fields().isEmpty()) {
                out.append("    fields:\n");
                for (Field f : z.fields()) {
                    out.append("      ").append(f.id()).append(":\n");
                    out.append("        corner-1: ").append(quote(f.box().corner1())).append('\n');
                    out.append("        corner-2: ").append(quote(f.box().corner2())).append('\n');
                    out.append("        resources:\n");
                    for (Field.Resource r : f.resources()) {
                        out.append("          - { block: ").append(r.block());
                        if (r.depleted() != null) {
                            out.append(", depleted: ").append(quote(r.depleted()));
                        }
                        if (r.respawnSeconds() != null) {
                            out.append(", respawn-seconds: ").append(r.respawnSeconds());
                        }
                        out.append(" }\n");
                    }
                }
            }
            if (z.arena() != null) {
                box(out, "    ", "arena", z.arena());
            }
            if (z.biome() != null) {
                out.append("    biome: ").append(z.biome()).append('\n');
            }
            palette(out, "    ", z.palette());
        }
        return out.toString();
    }

    private static void box(StringBuilder out, String indent, String key, Box b) {
        out.append(indent).append(key).append(": { corner-1: ").append(quote(b.corner1()))
                .append(", corner-2: ").append(quote(b.corner2())).append(" }\n");
    }

    private static void palette(StringBuilder out, String indent, Map<String, String> palette) {
        out.append(indent).append("palette: {");
        boolean first = true;
        for (Map.Entry<String, String> e : ZoneParser.orderedPalette(palette).entrySet()) {
            out.append(first ? " " : ", ").append(e.getKey()).append(": ").append(quote(e.getValue()));
            first = false;
        }
        out.append(" }\n");
    }

    static String point(Point p) {
        return quote(num(p.x()) + ", " + num(p.y()) + ", " + num(p.z()) + ", " + num(p.yaw()) + ", " + num(p.pitch()));
    }

    private static String num(double v) {
        if (v == Math.rint(v)) {
            return Long.toString((long) v);
        }
        return String.format(Locale.ROOT, "%s", v);
    }

    private static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
